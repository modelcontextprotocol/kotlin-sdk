package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.sse.ServerSentEvent
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * The standalone GET stream keeps its handler suspended on `awaitCancellation()` for the
 * lifetime of the stream. Closing the [ServerSSESession] only closes the response body, so
 * tearing the session down has to cancel that call as well — otherwise the coroutine and the
 * connection behind it are never released
 * (https://github.com/modelcontextprotocol/kotlin-sdk/issues/922).
 */
class StatefulStreamableHttpGetStreamLifecycleTest {

    @Test
    fun `closing the transport releases the standalone GET stream handler`() = testApplication {
        val transport =
            StreamableHttpServerTransport(StreamableHttpServerTransport.Configuration(enableJsonResponse = true))
        val handlerReleased = CompletableDeferred<Unit>()
        val streams = CoroutineScope(Dispatchers.Default)

        try {
            startSessionWithGetStream(transport, handlerReleased, streams)

            transport.close()

            awaitRelease(handlerReleased) shouldBe true
        } finally {
            streams.cancel()
        }
    }

    @Test
    fun `a replacement GET stream releases the previous stream handler`() = testApplication {
        val transport =
            StreamableHttpServerTransport(StreamableHttpServerTransport.Configuration(enableJsonResponse = true))
        val firstReleased = CompletableDeferred<Unit>()
        val streams = CoroutineScope(Dispatchers.Default)

        try {
            val sessionId = startSessionWithGetStream(transport, firstReleased, streams)

            // A client reconnecting its GET stream takes over the standalone slot; the handler
            // serving the previous stream must be released rather than left suspended.
            streams.launch { openGetStream(sessionId) }

            awaitRelease(firstReleased) shouldBe true
        } finally {
            streams.cancel()
        }
    }

    /**
     * Initializes a session against [transport] and opens the standalone GET stream, completing
     * [handlerReleased] when the GET handler returns. Fails if the handler returns before the
     * stream is torn down, which would make the assertions vacuous.
     */
    private suspend fun ApplicationTestBuilder.startSessionWithGetStream(
        transport: StreamableHttpServerTransport,
        handlerReleased: CompletableDeferred<Unit>,
        streams: CoroutineScope,
    ): String {
        val streamOpened = CompletableDeferred<Unit>()
        application {
            install(ServerContentNegotiation) { json(McpJson) }
            routing {
                post(PATH) { transport.handlePostRequest(null, call) }
                get(PATH) {
                    try {
                        transport.handleGetRequest(RecordingSseSession(call, streamOpened), call)
                    } finally {
                        handlerReleased.complete(Unit)
                    }
                }
            }
        }
        Server(
            Implementation("test-server", "1.0.0"),
            ServerOptions(capabilities = ServerCapabilities()),
        ).createSession(transport)

        val initResponse = client.post(PATH) {
            header(HttpHeaders.Host, "localhost")
            header(
                HttpHeaders.Accept,
                listOf(ContentType.Application.Json, ContentType.Text.EventStream).joinToString(", "),
            )
            contentType(ContentType.Application.Json)
            setBody(McpJson.encodeToString(JSONRPCMessage.serializer(), initializePayload()))
        }
        initResponse.status shouldBe HttpStatusCode.OK
        val sessionId = assertNotNull(initResponse.headers[MCP_SESSION_ID_HEADER])

        streams.launch { openGetStream(sessionId) }

        withTimeoutOrNull(5.seconds) { streamOpened.await() }
        streamOpened.isCompleted shouldBe true
        // The handler must still be suspended at this point, otherwise the test proves nothing.
        handlerReleased.isCompleted shouldBe false
        return sessionId
    }

    private suspend fun ApplicationTestBuilder.openGetStream(sessionId: String) {
        client.get(PATH) {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            header(MCP_SESSION_ID_HEADER, sessionId)
            header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
        }
    }

    private suspend fun awaitRelease(released: CompletableDeferred<Unit>): Boolean = withTimeoutOrNull(5.seconds) {
        released.await()
        true
    } ?: false

    private fun initializePayload() = InitializeRequest(
        InitializeRequestParams(
            protocolVersion = LATEST_PROTOCOL_VERSION,
            capabilities = ClientCapabilities(),
            clientInfo = Implementation(name = "test-client", version = "1.0.0"),
        ),
    ).toJSON()

    private companion object {
        const val PATH = "/mcp"
    }
}

/** Signals [opened] once the transport writes to the stream, i.e. the handler is about to suspend. */
private class RecordingSseSession(override val call: ApplicationCall, private val opened: CompletableDeferred<Unit>) :
    ServerSSESession {
    override val coroutineContext: CoroutineContext = call.coroutineContext

    override suspend fun send(event: ServerSentEvent) {
        opened.complete(Unit)
    }

    override suspend fun close() {}
}
