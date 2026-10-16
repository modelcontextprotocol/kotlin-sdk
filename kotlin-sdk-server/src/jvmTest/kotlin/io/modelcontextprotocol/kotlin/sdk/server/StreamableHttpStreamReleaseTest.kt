package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.sse
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Verifies that a standalone GET handler returns once the transport stops using its stream, and that a closed
 * transport refuses requests instead of leaving them waiting. An engine may never cancel a handler that is only
 * waiting, even after its client went away, so a handler that waited for cancellation stayed alive for good.
 */
class StreamableHttpStreamReleaseTest {

    private val transport = StreamableHttpServerTransport(
        StreamableHttpServerTransport.Configuration(enableJsonResponse = true),
    ).apply {
        onMessage { message ->
            if (message is JSONRPCRequest) send(JSONRPCResponse(message.id, EmptyResult()))
        }
    }

    /** Receives the [STREAM_NAME_HEADER] of each GET whose handler finishes, by returning or by throwing. */
    private val finishedStreams = Channel<String>(Channel.UNLIMITED)

    private fun ApplicationTestBuilder.serveTransport(wrap: (ServerSSESession) -> ServerSSESession = { it }) {
        application {
            installMcpContentNegotiation()
            install(SSE)
            routing {
                post("/mcp") { transport.handleRequest(null, call) }
                sse("/mcp") {
                    try {
                        transport.handleRequest(wrap(this), call)
                    } finally {
                        finishedStreams.trySend(call.request.headers[STREAM_NAME_HEADER].orEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun `closing the transport ends an open GET stream`() = testApplication {
        serveTransport()
        val sessionId = client.initialize()

        client.openStream(sessionId).execute { stream ->
            stream.bodyAsChannel().readLine()

            transport.close()

            withTimeout(5.seconds) { finishedStreams.receive() }
        }
    }

    @Test
    fun `a newer GET stream ends the one it replaces`() = testApplication {
        serveTransport()
        val sessionId = client.initialize()

        client.openStream(sessionId, name = "first").execute { first ->
            awaitRoutedTo(first.bodyAsChannel())

            client.openStream(sessionId, name = "second").execute { second ->
                second.bodyAsChannel().readLine()

                withTimeout(5.seconds) { finishedStreams.receive() } shouldBe "first"
                finishedStreams.tryReceive().isSuccess shouldBe false
            }
        }
    }

    @Test
    fun `closing does not wait on a stream whose client stopped reading`() = testApplication {
        val stalledSessions = Channel<StalledSseSession>(Channel.UNLIMITED)
        serveTransport { session -> StalledSseSession(session).also { stalledSessions.trySend(it) } }
        val sessionId = client.initialize()

        client.openStream(sessionId).execute { stream ->
            stream.bodyAsChannel().readLine()
            val session = stalledSessions.receive()
            session.stall()

            coroutineScope {
                // Server-initiated messages until one gets stuck on the stream, holding the lock that closing
                // the SSE session takes, as a send to a client that stopped reading does.
                val sender = launch {
                    while (isActive) {
                        transport.send(JSONRPCNotification(method = "notifications/message"))
                        delay(10.milliseconds)
                    }
                }
                try {
                    withTimeout(5.seconds) { session.sendStuck.await() }

                    val closing = launch { transport.close() }
                    // A close that waited for the stuck send would never finish, so join with a deadline.
                    withTimeoutOrNull(5.seconds) { closing.join() }.shouldNotBeNull()
                    withTimeout(5.seconds) { finishedStreams.receive() }
                } finally {
                    sender.cancel()
                }
            }
        }
    }

    @Test
    fun `POST on a closed transport is rejected instead of left waiting`() = testApplication {
        serveTransport()
        val sessionId = client.initialize()
        transport.close()

        val response = withTimeout(5.seconds) {
            client.post("/mcp") {
                streamableHeaders()
                header(MCP_SESSION_ID_HEADER, sessionId)
                setBody("""{"jsonrpc":"2.0","id":2,"method":"ping"}""")
            }
        }
        response.status shouldBe HttpStatusCode.NotFound
        response.bodyAsText() shouldContain "Session not found"
    }

    private suspend fun HttpClient.initialize(): String {
        val response = post("/mcp") {
            streamableHeaders()
            setBody(McpJson.encodeToString(JSONRPCMessage.serializer(), initializeRequest()))
        }
        response.status shouldBe HttpStatusCode.OK
        return response.headers[MCP_SESSION_ID_HEADER].shouldNotBeNull()
    }

    /** Returns once the transport routes server-initiated messages to the stream read through [channel]. */
    private suspend fun awaitRoutedTo(channel: ByteReadChannel) = coroutineScope {
        // Messages sent before the stream registers are dropped, so keep probing until one arrives.
        val prober = launch {
            while (isActive) {
                transport.send(JSONRPCNotification(method = "notifications/probe"))
                delay(10.milliseconds)
            }
        }
        try {
            withTimeout(5.seconds) {
                do {
                    val line = channel.readLine()
                } while (line?.contains("notifications/probe") != true)
            }
        } finally {
            prober.cancel()
        }
    }

    private suspend fun HttpClient.openStream(sessionId: String, name: String = ""): HttpStatement =
        prepareGet("/mcp") {
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            header(MCP_SESSION_ID_HEADER, sessionId)
            header(STREAM_NAME_HEADER, name)
        }

    /**
     * An SSE session whose client stops reading once [stall] is called: a send then never completes and keeps holding
     * the lock that [close] also takes, like Ktor's own session does while its buffer is full.
     */
    private class StalledSseSession(private val delegate: ServerSSESession) : ServerSSESession by delegate {
        private val lock = Mutex()

        @Volatile
        private var stalled = false

        val sendStuck = CompletableDeferred<Unit>()

        fun stall() {
            stalled = true
        }

        override suspend fun send(event: ServerSentEvent) {
            lock.withLock {
                if (stalled) {
                    sendStuck.complete(Unit)
                    awaitCancellation()
                }
                delegate.send(event)
            }
        }

        // Delegation would route this default method to the delegate, bypassing the stall.
        override suspend fun send(
            data: String?,
            event: String?,
            id: String?,
            retry: Long?,
            comments: String?,
        ) {
            send(ServerSentEvent(data, event, id, retry, comments))
        }

        override suspend fun close() {
            lock.withLock { delegate.close() }
        }
    }

    private companion object {
        const val STREAM_NAME_HEADER = "X-Test-Stream"
    }
}
