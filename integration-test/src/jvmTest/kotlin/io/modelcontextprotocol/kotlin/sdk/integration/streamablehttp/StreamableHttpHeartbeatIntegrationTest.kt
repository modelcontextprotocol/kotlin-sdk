package io.modelcontextprotocol.kotlin.sdk.integration.streamablehttp

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.engine.embeddedServer
import io.ktor.server.sse.Heartbeat
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import io.modelcontextprotocol.kotlin.test.utils.actualPort
import io.modelcontextprotocol.kotlin.test.utils.runIntegrationTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

private const val HOST = "127.0.0.1"
private const val SESSION_ID_HEADER = "mcp-session-id"
private const val HEARTBEAT_LINE = ": mcp-heartbeat"

class StreamableHttpHeartbeatIntegrationTest {

    @Test
    fun `GET SSE stream emits configured heartbeat`() = withGetStream(
        heartbeat = {
            period = 50.milliseconds
            event = ServerSentEvent(comments = "mcp-heartbeat")
        },
    ) { stream ->
        stream.readLineMatching(2.seconds) { it == HEARTBEAT_LINE }.shouldNotBeNull()
    }

    @Test
    fun `GET SSE stream does not emit heartbeat by default`() = withGetStream(heartbeat = null) { stream ->
        // Ktor emits the first heartbeat immediately, so an enabled default would show up within the window.
        stream.readLineMatching(300.milliseconds) { it.startsWith(":") && "heartbeat" in it } shouldBe null
    }

    private fun withGetStream(heartbeat: (Heartbeat.() -> Unit)?, block: suspend (ByteReadChannel) -> Unit) =
        runIntegrationTest(timeout = 20.seconds) {
            val server = embeddedServer(ServerCIO, host = HOST, port = 0) {
                mcpStreamableHttp(sseHeartbeatConfig = heartbeat) {
                    Server(Implementation("heartbeat-server", "1.0.0"), ServerOptions(ServerCapabilities()))
                }
            }.startSuspend(wait = false)
            val httpClient = HttpClient(ClientCIO)
            try {
                val url = "http://$HOST:${server.actualPort()}/mcp"
                val sessionId = httpClient.initializeSession(url)
                httpClient.prepareGet(url) {
                    header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
                    header(SESSION_ID_HEADER, sessionId)
                    header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
                }.execute { response ->
                    response.status shouldBe HttpStatusCode.OK
                    block(response.bodyAsChannel())
                }
            } finally {
                httpClient.close()
                server.stopSuspend(gracePeriodMillis = 0, timeoutMillis = 1_000)
            }
        }

    private suspend fun HttpClient.initializeSession(url: String): String {
        val initialize = InitializeRequest(
            InitializeRequestParams(
                protocolVersion = LATEST_PROTOCOL_VERSION,
                capabilities = ClientCapabilities(),
                clientInfo = Implementation("heartbeat-client", "1.0.0"),
            ),
        ).toJSON()
        val response = post(url) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "${ContentType.Application.Json}, ${ContentType.Text.EventStream}")
            setBody(McpJson.encodeToString(initialize))
        }
        response.status shouldBe HttpStatusCode.OK
        return response.headers[SESSION_ID_HEADER].shouldNotBeNull()
    }

    private suspend fun ByteReadChannel.readLineMatching(timeout: Duration, matches: (String) -> Boolean): String? =
        withTimeoutOrNull(timeout) {
            var line = readLine()
            while (line != null && !matches(line)) line = readLine()
            line
        }
}
