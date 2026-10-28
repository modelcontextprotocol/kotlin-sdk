package io.modelcontextprotocol.kotlin.sdk.integration.streamablehttp

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
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.test.utils.actualPort
import io.modelcontextprotocol.kotlin.test.utils.runIntegrationTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

private const val SESSION_ID_HEADER = "mcp-session-id"
private const val SESSION_ID = "test-session"
private const val HOST = "127.0.0.1"
private const val MCP_PATH = "/mcp"

private const val INITIALIZE_RESULT =
    """{"capabilities":{},"protocolVersion":"2025-03-26","serverInfo":{"name":"test-server","version":"1.0.0"}}"""

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
        configure: Client.() -> Unit = {},
        block: suspend (Client) -> Unit,
    ) = runIntegrationTest(timeout = 20.seconds) {
        val server = embeddedServer(ServerCIO, host = HOST, port = 0) {
            routing {
                mcpPostRoute()
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

    /** Answers `initialize` and `ping` with JSON and accepts notifications. */
    private fun Route.mcpPostRoute() {
        post(MCP_PATH) {
            val message = Json.parseToJsonElement(call.receiveText()).jsonObject
            call.response.header(SESSION_ID_HEADER, SESSION_ID)
            val result = when (message["method"]?.jsonPrimitive?.content) {
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

    private fun logNotification(data: String): String =
        """{"jsonrpc":"2.0","method":"notifications/message","params":{"level":"info","data":"$data"}}"""

    private fun sseEvent(id: String, data: String): String = "event: message\nid: $id\ndata: $data\n\n"
}
