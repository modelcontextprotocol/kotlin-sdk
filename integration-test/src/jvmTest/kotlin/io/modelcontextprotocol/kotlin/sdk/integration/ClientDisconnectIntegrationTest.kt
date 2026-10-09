package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

/**
 * Runs against a real CIO server, because what matters here is engine behavior: CIO never cancels a handler that is
 * only waiting after its client disconnects, so a client that goes away without a word must not leave its session
 * behind. The clients are raw sockets, so they can disconnect without saying anything.
 */
class ClientDisconnectIntegrationTest {

    private fun testServer() = Server(Implementation("test-server", "1.0"), ServerOptions(ServerCapabilities()))

    @Test
    fun `an SSE session ends when its client disconnects`(): Unit = runBlocking {
        val server = testServer()
        withCioServer({
            install(ServerSSE)
            routing { mcp { server } }
        }) { port ->
            val sessionId = connect(port, LEGACY_SSE_REQUEST).use { socket ->
                socket.readLineMatching { "sessionId=" in it }.substringAfter("sessionId=")
            }

            eventually(10.seconds) { server.sessions.shouldBeEmpty() }
            HttpClient(ClientCIO).use { client ->
                client.post("http://$HOST:$port/?sessionId=$sessionId") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"jsonrpc":"2.0","id":1,"method":"ping"}""")
                }.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `an SSE handler ends when the server closes its session`(): Unit = runBlocking {
        val server = testServer()
        val finishedGets = AtomicInteger()
        withCioServer({
            countFinishedGets(finishedGets)
            install(ServerSSE)
            routing { mcp { server } }
        }) { port ->
            connect(port, LEGACY_SSE_REQUEST).use { socket ->
                socket.readLineMatching { "sessionId=" in it }
                eventually(10.seconds) { server.sessions shouldHaveSize 1 }

                server.sessions.values.single().close()

                // The client stays connected, so only the handler returning can finish the call.
                eventually(10.seconds) { finishedGets.get() shouldBe 1 }
            }
        }
    }

    @Test
    fun `a GET stream ends as soon as its client disconnects`(): Unit = runBlocking {
        val server = testServer()
        val finishedGets = AtomicInteger()
        withCioServer({
            countFinishedGets(finishedGets)
            mcpStreamableHttp { server }
        }) { port ->
            val sessionId = initializeSession(port)
            val stream = openStream(port, sessionId)
            withContext(Dispatchers.IO) { stream.close() }

            // Long before the session could expire.
            eventually(10.seconds) { finishedGets.get() shouldBe 1 }
            server.sessions shouldHaveSize 1
        }
    }

    @Test
    fun `a GET stream ends when its session expires even while its client stays connected`(): Unit = runBlocking {
        val server = testServer()
        val finishedGets = AtomicInteger()
        withCioServer({
            countFinishedGets(finishedGets)
            mcpStreamableHttp(sessionIdleTimeout = 1.seconds) { server }
        }) { port ->
            val sessionId = initializeSession(port)
            openStream(port, sessionId).use {
                eventually(10.seconds) {
                    server.sessions.shouldBeEmpty()
                    finishedGets.get() shouldBe 1
                }
            }
        }
    }

    @Test
    fun `a POST whose client disconnects does not keep its session from expiring`(): Unit = runBlocking {
        val toolStarted = CompletableDeferred<Unit>()
        val server = Server(
            Implementation("test-server", "1.0"),
            ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools())),
        ) {
            addTool(name = "wait", description = "Never answers") {
                toolStarted.complete(Unit)
                awaitCancellation()
            }
        }
        withCioServer({ mcpStreamableHttp(sessionIdleTimeout = 1.seconds) { server } }) { port ->
            val sessionId = initializeSession(port)
            val body = """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"wait","arguments":{}}}"""
            val request = "POST /mcp HTTP/1.1\r\nHost: localhost\r\nAccept: application/json, text/event-stream\r\n" +
                "Content-Type: application/json\r\nMcp-Session-Id: $sessionId\r\n" +
                "Content-Length: ${body.toByteArray().size}\r\n\r\n$body"
            connect(port, request).use {
                withTimeout(10.seconds) { toolStarted.await() }
            }

            // The request is still unanswered, but with its client gone it no longer holds the session.
            eventually(10.seconds) { server.sessions.shouldBeEmpty() }
        }
    }

    private suspend fun initializeSession(port: Int): String = HttpClient(ClientCIO).use { client ->
        val sessionId = client.post("http://$HOST:$port/mcp") {
            header(HttpHeaders.Accept, "${ContentType.Application.Json}, ${ContentType.Text.EventStream}")
            contentType(ContentType.Application.Json)
            setBody(INITIALIZE_REQUEST)
        }.headers["mcp-session-id"].shouldNotBeNull()
        client.post("http://$HOST:$port/mcp") {
            header(HttpHeaders.Accept, "${ContentType.Application.Json}, ${ContentType.Text.EventStream}")
            header("mcp-session-id", sessionId)
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        }.status shouldBe HttpStatusCode.Accepted
        sessionId
    }

    /** Opens the session's GET stream on a raw socket, which the caller can drop without a word. */
    private suspend fun openStream(port: Int, sessionId: String): Socket {
        val socket = connect(
            port,
            "GET /mcp HTTP/1.1\r\nHost: localhost\r\nAccept: text/event-stream\r\nMcp-Session-Id: $sessionId\r\n\r\n",
        )
        socket.readLineMatching { it.startsWith("HTTP/") } shouldStartWith "HTTP/1.1 200"
        return socket
    }

    /** Opens a raw connection and sends [request] on it, so that the test can later drop it without a word. */
    private suspend fun connect(port: Int, request: String): Socket = withContext(Dispatchers.IO) {
        Socket(HOST, port).apply { getOutputStream().write(request.toByteArray()) }
    }

    /** Reads this connection until a line matches [predicate], and returns that line. */
    private suspend fun Socket.readLineMatching(predicate: (String) -> Boolean): String = withContext(Dispatchers.IO) {
        val lines = getInputStream().bufferedReader()
        generateSequence { lines.readLine() }.first(predicate)
    }

    /**
     * Counts the GET calls that have ended, whether their handler returned or they were cancelled. A cancelled call
     * never finishes sending its response, so its end shows only as the completion of its job.
     */
    private fun Application.countFinishedGets(counter: AtomicInteger) {
        install(
            createApplicationPlugin("FinishedGets") {
                onCall { call ->
                    if (call.request.httpMethod == HttpMethod.Get) {
                        call.coroutineContext.job.invokeOnCompletion { counter.incrementAndGet() }
                    }
                }
            },
        )
    }

    private suspend fun withCioServer(module: Application.() -> Unit, block: suspend (port: Int) -> Unit) {
        val server = embeddedServer(ServerCIO, host = HOST, port = 0, module = module).start(wait = false)
        try {
            block(server.engine.resolvedConnectors().first().port)
        } finally {
            server.stop(0, 0)
        }
    }

    private companion object {
        const val HOST = "127.0.0.1"
        const val LEGACY_SSE_REQUEST = "GET / HTTP/1.1\r\nHost: localhost\r\nAccept: text/event-stream\r\n\r\n"
        const val INITIALIZE_REQUEST =
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",""" +
                """"capabilities":{},"clientInfo":{"name":"test-client","version":"1.0"}}}"""
    }
}
