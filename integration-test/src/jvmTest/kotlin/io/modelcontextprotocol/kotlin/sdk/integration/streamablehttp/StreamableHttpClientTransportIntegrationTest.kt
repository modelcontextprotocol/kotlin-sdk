package io.modelcontextprotocol.kotlin.sdk.integration.streamablehttp

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.test.utils.actualPort
import io.modelcontextprotocol.kotlin.test.utils.runIntegrationTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

private const val SESSION_ID_HEADER = "mcp-session-id"
private const val SESSION_ID = "test-session"
private const val HOST = "127.0.0.1"
private const val MCP_PATH = "/mcp"

private const val LAST_EVENT_ID_HEADER = "Last-Event-ID"

private const val INITIALIZE_RESULT =
    """{"capabilities":{"tools":{}},"protocolVersion":"2025-03-26",""" +
        """"serverInfo":{"name":"test-server","version":"1.0.0"}}"""

/** Long enough for a resumption the client should not start, immediate or after a short `retry`, to arrive. */
private val RESUMPTION_WINDOW = 500.milliseconds

/**
 * Drives [StreamableHttpClientTransport]'s GET SSE handling against a scripted server on a real socket;
 * the MockEngine-based unit tests can't serve SSE.
 */
class StreamableHttpClientTransportIntegrationTest {

    @Test
    fun `client receives server notifications via GET SSE`() {
        val received = Channel<String>(Channel.UNLIMITED)
        withConnectedClient(
            getRoute = {
                get(MCP_PATH) {
                    call.response.header(SESSION_ID_HEADER, SESSION_ID)
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        write(sseEvent(id = "1", data = logNotification("first")))
                        write(sseEvent(id = "2", data = logNotification("second")))
                        flush()
                        awaitCancellation()
                    }
                }
            },
            configure = {
                setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) {
                    received.trySend(it.params.data.jsonPrimitive.content)
                    CompletableDeferred(Unit)
                }
            },
        ) {
            listOf(received.receive(), received.receive()) shouldBe listOf("first", "second")
        }
    }

    @Test
    fun `client survives 405 on GET SSE`() = withConnectedClient(
        getRoute = {
            get(MCP_PATH) {
                call.respondText("", ContentType.Text.EventStream, HttpStatusCode.MethodNotAllowed)
            }
        },
    ) { client ->
        client.ping()
    }

    @Test
    fun `client survives non streaming JSON response on GET SSE`() = withConnectedClient(
        getRoute = {
            get(MCP_PATH) {
                call.response.header(SESSION_ID_HEADER, SESSION_ID)
                call.respondText("", ContentType.Application.Json, HttpStatusCode.OK)
            }
        },
    ) { client ->
        client.ping()
    }

    @Test
    fun `client does not resume the stream after an inline error response`() {
        val resumptions = AtomicInteger()
        withConnectedClient(getRoute = { resumptionRoute(resumptions) }) { client ->
            val exception = shouldThrow<McpException> { client.callTool("missing", emptyMap()) }

            exception.code shouldBe RPCError.ErrorCode.INVALID_PARAMS
            delay(RESUMPTION_WINDOW)
            resumptions.get() shouldBe 0
        }
    }

    @Test
    fun `client re-addresses a replayed error response and stops resuming`() {
        val resumptions = AtomicInteger()
        withConnectedClient(getRoute = { resumptionRoute(resumptions) }) { client ->
            val exception = shouldThrow<McpException> {
                client.callTool(
                    CallToolRequest(CallToolRequestParams("missing")),
                    RequestOptions(resumptionToken = "1", timeout = 5.seconds),
                )
            }

            exception.code shouldBe RPCError.ErrorCode.INVALID_PARAMS
            delay(RESUMPTION_WINDOW)
            resumptions.get() shouldBe 1
        }
    }

    @Test
    fun `client keeps the GET stream open while a request stream resumes`() {
        val sendNotification = CompletableDeferred<Unit>()
        val received = Channel<String>(Channel.UNLIMITED)
        withConnectedClient(
            postRoute = { mcpPostRoute { primingOnlyStream(it) } },
            getRoute = {
                get(MCP_PATH) {
                    val lastEventId = call.request.headers[LAST_EVENT_ID_HEADER]
                    call.response.header(SESSION_ID_HEADER, SESSION_ID)
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        if (lastEventId == null) {
                            write(": open\n\n")
                            flush()
                            sendNotification.await()
                            write(sseEvent(id = "1", data = logNotification("after resumption")))
                            flush()
                            awaitCancellation()
                        } else {
                            write(sseEvent(id = "$lastEventId-1", data = toolResult(id = lastEventId)))
                            flush()
                        }
                    }
                }
            },
            configure = {
                setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) {
                    received.trySend(it.params.data.jsonPrimitive.content)
                    CompletableDeferred(Unit)
                }
            },
        ) { client ->
            client.callTool("poll", emptyMap()).text() shouldBe "polled"

            sendNotification.complete(Unit)
            withTimeout(5.seconds) { received.receive() } shouldBe "after resumption"
        }
    }

    @Test
    fun `client resumes concurrent request streams independently`() {
        val resumptions = AtomicInteger()
        val bothResumed = CompletableDeferred<Unit>()
        withConnectedClient(
            postRoute = { mcpPostRoute { primingOnlyStream(it) } },
            getRoute = {
                get(MCP_PATH) {
                    val lastEventId = call.request.headers[LAST_EVENT_ID_HEADER]
                    if (lastEventId == null) {
                        call.respondText("", ContentType.Text.EventStream, HttpStatusCode.MethodNotAllowed)
                        return@get
                    }
                    call.response.header(SESSION_ID_HEADER, SESSION_ID)
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        write(": resumed\n\n")
                        flush()
                        // Answers only once both streams are resumed, so both must still be open.
                        if (resumptions.incrementAndGet() == 2) bothResumed.complete(Unit)
                        bothResumed.await()
                        write(sseEvent(id = "$lastEventId-1", data = toolResult(id = lastEventId)))
                        flush()
                    }
                }
            },
        ) { client ->
            val results = coroutineScope {
                List(2) {
                    async { client.callTool("poll", emptyMap(), options = RequestOptions(timeout = 5.seconds)) }
                }.awaitAll()
            }

            results.map { it.text() } shouldBe listOf("polled", "polled")
        }
    }

    @Test
    fun `client stops resuming the stream of a request it cancelled`() {
        val resumptions = AtomicInteger()
        withConnectedClient(
            postRoute = { mcpPostRoute { primingOnlyStream(it) } },
            getRoute = { pollingRoute(resumptions) },
        ) { client ->
            val exception = shouldThrow<McpException> {
                client.callTool("poll", emptyMap(), options = RequestOptions(timeout = 1.seconds))
            }

            exception.code shouldBe RPCError.ErrorCode.REQUEST_TIMEOUT
            delay(RESUMPTION_WINDOW)
            val afterCancel = resumptions.get()
            afterCancel shouldBeGreaterThan 0
            delay(RESUMPTION_WINDOW)
            resumptions.get() shouldBe afterCancel
        }
    }

    @Test
    fun `client stops resuming the stream of a cancelled request sent with a resumption token`() {
        val resumptions = AtomicInteger()
        withConnectedClient(getRoute = { pollingRoute(resumptions) }) { client ->
            val exception = shouldThrow<McpException> {
                client.callTool(
                    "poll",
                    emptyMap(),
                    options = RequestOptions(resumptionToken = "1", timeout = 1.seconds),
                )
            }

            exception.code shouldBe RPCError.ErrorCode.REQUEST_TIMEOUT
            delay(RESUMPTION_WINDOW)
            val afterCancel = resumptions.get()
            afterCancel shouldBeGreaterThan 0
            delay(RESUMPTION_WINDOW)
            resumptions.get() shouldBe afterCancel
        }
    }

    @Test
    fun `client reports an expired session through onError`() = runIntegrationTest(timeout = 20.seconds) {
        val server = Server(Implementation("test-server", "1.0.0"), ServerOptions(ServerCapabilities()))
        val ktorServer = embeddedServer(ServerCIO, host = HOST, port = 0) {
            mcpStreamableHttp(sessionIdleTimeout = 1.seconds) { server }
        }.startSuspend(wait = false)
        val httpClient = HttpClient(ClientCIO) { install(SSE) }
        val client = Client(Implementation("test-client", "1.0.0"))
        try {
            val url = "http://$HOST:${ktorServer.actualPort()}$MCP_PATH"
            val transport = StreamableHttpClientTransport(httpClient, url)
            val sessionNotFound = CompletableDeferred<StreamableHttpError>()
            transport.onError { if (it is StreamableHttpError && it.code == 404) sessionNotFound.complete(it) }
            client.connect(transport)

            // The open GET stream does not keep the session alive, so its reconnect after expiry gets 404.
            withTimeout(10.seconds) { sessionNotFound.await() }
        } finally {
            client.close()
            httpClient.close()
            ktorServer.stopSuspend(1000, 2000)
        }
    }

    private fun withConnectedClient(
        getRoute: Route.() -> Unit,
        postRoute: Route.() -> Unit = { mcpPostRoute() },
        configure: Client.() -> Unit = {},
        block: suspend (Client) -> Unit,
    ) = runIntegrationTest(timeout = 20.seconds) {
        val server = embeddedServer(ServerCIO, host = HOST, port = 0) {
            routing {
                postRoute()
                getRoute()
            }
        }.startSuspend(wait = false)
        val httpClient = HttpClient(ClientCIO) { install(SSE) }
        val client = Client(Implementation("test-client", "1.0.0")).apply(configure)
        try {
            client.connect(StreamableHttpClientTransport(httpClient, "http://$HOST:${server.actualPort()}$MCP_PATH"))
            block(client)
        } finally {
            client.close()
            httpClient.close()
            server.stopSuspend(gracePeriodMillis = 0, timeoutMillis = 1_000)
        }
    }

    /**
     * Answers `initialize` and `ping` with JSON and accepts notifications.
     * `tools/call` gets [toolCallStream] of its request id as an SSE stream; by default an error with event ids,
     * the way a server with an event store answers.
     */
    private fun Route.mcpPostRoute(toolCallStream: (requestId: String) -> String = { errorStream(it) }) {
        post(MCP_PATH) {
            val message = Json.parseToJsonElement(call.receiveText()).jsonObject
            call.response.header(SESSION_ID_HEADER, SESSION_ID)
            val method = message["method"]?.jsonPrimitive?.content
            if (method == "tools/call") {
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    write(toolCallStream("${message["id"]}"))
                    flush()
                }
                return@post
            }
            val result = when (method) {
                "initialize" -> INITIALIZE_RESULT
                "ping" -> "{}"
                else -> null
            }
            if (result == null) {
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respondText(
                    """{"jsonrpc":"2.0","id":${message["id"]},"result":$result}""",
                    ContentType.Application.Json,
                )
            }
        }
    }

    /**
     * Rejects the standalone GET stream and answers each resumption with an error carrying a stale request id,
     * as a replay of a request sent before a reconnect does.
     */
    private fun Route.resumptionRoute(resumptions: AtomicInteger) {
        get(MCP_PATH) {
            if (call.request.headers[LAST_EVENT_ID_HEADER] == null) {
                call.respondText("", ContentType.Text.EventStream, HttpStatusCode.MethodNotAllowed)
            } else {
                resumptions.incrementAndGet()
                call.response.header(SESSION_ID_HEADER, SESSION_ID)
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    write("retry: 100\n" + sseEvent(id = "3", data = errorResponse(id = "\"stale\"")))
                    flush()
                }
            }
        }
    }

    /** Rejects the standalone GET stream and ends each resumption without the response, so the client keeps polling. */
    private fun Route.pollingRoute(resumptions: AtomicInteger) {
        get(MCP_PATH) {
            if (call.request.headers[LAST_EVENT_ID_HEADER] == null) {
                call.respondText("", ContentType.Text.EventStream, HttpStatusCode.MethodNotAllowed)
                return@get
            }
            resumptions.incrementAndGet()
            call.response.header(SESSION_ID_HEADER, SESSION_ID)
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                write("retry: 50\n\n")
                flush()
            }
        }
    }

    private fun errorStream(requestId: String): String =
        "id: 1\ndata:\n\n" + sseEvent(id = "2", data = errorResponse(id = requestId))

    /** Only a priming event whose id is the request id: the server closed the stream before answering. */
    private fun primingOnlyStream(requestId: String): String = "id: $requestId\nretry: 50\ndata:\n\n"

    private fun errorResponse(id: String): String =
        """{"jsonrpc":"2.0","id":$id,"error":{"code":-32602,"message":"Tool not found: missing"}}"""

    private fun toolResult(id: String): String =
        """{"jsonrpc":"2.0","id":$id,"result":{"content":[{"type":"text","text":"polled"}]}}"""

    private fun CallToolResult.text(): String = (content.single() as TextContent).text

    private fun logNotification(data: String): String =
        """{"jsonrpc":"2.0","method":"notifications/message","params":{"level":"info","data":"$data"}}"""

    private fun sseEvent(id: String, data: String): String = "event: message\nid: $id\ndata: $data\n\n"
}
