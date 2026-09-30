package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.mcpWebSocketTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.server.mcpWebSocket
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.test.utils.actualPort
import io.modelcontextprotocol.kotlin.test.utils.runIntegrationTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.channels.Channels
import java.nio.channels.Pipe
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Checks what differs between transports: message framing, large payloads,
 * response correlation on a shared connection, and session routing.
 * Feature semantics are covered by the in-memory server tests.
 */
class TransportIntegrationTest {

    enum class TransportKind { STDIO, SSE, STREAMABLE_HTTP, WEBSOCKET }

    @ParameterizedTest
    @EnumSource
    fun `tool call round trip preserves special characters`(kind: TransportKind) = runTransportTest(kind) {
        val result = connect().callTool("echo", mapOf("text" to SPECIAL_TEXT))

        (result.content.single() as TextContent).text shouldBe SPECIAL_TEXT
        result.structuredContent shouldBe buildJsonObject { put("text", SPECIAL_TEXT) }
    }

    @ParameterizedTest
    @EnumSource
    fun `large resource arrives intact`(kind: TransportKind) = runTransportTest(kind) {
        val result = connect().readResource(ReadResourceRequest(ReadResourceRequestParams(LARGE_RESOURCE_URI)))

        (result.contents.single() as TextResourceContents).text shouldBe LARGE_TEXT
    }

    @ParameterizedTest
    @EnumSource
    fun `concurrent tool calls each receive their own response`(kind: TransportKind) = runTransportTest(kind) {
        val client = connect()

        // Earlier calls wait longer, so responses complete in reverse order and must be matched by request id.
        val texts = coroutineScope {
            (0 until CONCURRENT_CALLS).map { i ->
                async {
                    val result = client.callTool(
                        "echo",
                        mapOf(
                            "text" to "call-$i",
                            "delayMs" to (CONCURRENT_CALLS - i) * 10,
                        ),
                    )
                    (result.content.single() as TextContent).text
                }
            }.awaitAll()
        }

        texts shouldBe (0 until CONCURRENT_CALLS).map { "call-$it" }
    }

    @ParameterizedTest
    @EnumSource(names = ["SSE", "STREAMABLE_HTTP", "WEBSOCKET"])
    fun `each client connection is served by its own session`(kind: TransportKind) = runTransportTest(kind) {
        val first = connect()
        val second = connect()

        val firstSession = first.servingSessionId()
        val secondSession = second.servingSessionId()

        firstSession shouldNotBe secondSession
        first.servingSessionId() shouldBe firstSession
        second.servingSessionId() shouldBe secondSession
    }

    private fun runTransportTest(kind: TransportKind, block: suspend TransportFixture.() -> Unit) =
        runIntegrationTest(timeout = 30.seconds) {
            val fixture = TransportFixture(kind)
            try {
                fixture.start()
                fixture.block()
            } finally {
                fixture.close()
            }
        }

    private suspend fun Client.servingSessionId(): String =
        (callTool("session-id", emptyMap()).content.single() as TextContent).text

    private class TransportFixture(private val kind: TransportKind) {
        private val server = createServer()
        private var engine: EmbeddedServer<*, *>? = null
        private val clients = mutableListOf<Client>()
        private val httpClients = mutableListOf<HttpClient>()
        private val pipes = mutableListOf<Pipe>()

        suspend fun start() {
            engine = when (kind) {
                TransportKind.STDIO -> null

                TransportKind.SSE -> embeddedServer(ServerCIO, host = HOST, port = 0) { mcp { server } }

                TransportKind.STREAMABLE_HTTP -> embeddedServer(ServerCIO, host = HOST, port = 0) {
                    mcpStreamableHttp { server }
                }

                TransportKind.WEBSOCKET -> embeddedServer(ServerCIO, host = HOST, port = 0) { mcpWebSocket { server } }
            }?.startSuspend(wait = false)
        }

        suspend fun connect(): Client {
            val client = Client(Implementation("test-client", "1.0.0"))
            clients += client
            client.connect(clientTransport())
            return client
        }

        suspend fun close() {
            clients.forEach { it.close() }
            httpClients.forEach { it.close() }
            engine?.stopSuspend(gracePeriodMillis = 0, timeoutMillis = 1_000)
            server.close()
            pipes.forEach {
                it.sink().close()
                it.source().close()
            }
        }

        private suspend fun clientTransport(): Transport = when (kind) {
            TransportKind.STDIO -> stdioClientTransport()

            TransportKind.SSE -> SseClientTransport(httpClient { install(SSE) }, "http://$HOST:${port()}")

            TransportKind.STREAMABLE_HTTP ->
                StreamableHttpClientTransport(httpClient { install(SSE) }, "http://$HOST:${port()}/mcp")

            TransportKind.WEBSOCKET -> httpClient { install(WebSockets) }.mcpWebSocketTransport("ws://$HOST:${port()}")
        }

        private suspend fun stdioClientTransport(): StdioClientTransport {
            val clientToServer = Pipe.open().also { pipes += it }
            val serverToClient = Pipe.open().also { pipes += it }
            server.createSession(
                StdioServerTransport(
                    input = Channels.newInputStream(clientToServer.source()).asSource().buffered(),
                    output = Channels.newOutputStream(serverToClient.sink()).asSink().buffered(),
                ),
            )
            return StdioClientTransport(
                input = Channels.newInputStream(serverToClient.source()).asSource().buffered(),
                output = Channels.newOutputStream(clientToServer.sink()).asSink().buffered(),
            )
        }

        private fun httpClient(config: HttpClientConfig<CIOEngineConfig>.() -> Unit): HttpClient =
            HttpClient(ClientCIO, config).also { httpClients += it }

        private suspend fun port(): Int = checkNotNull(engine).actualPort()
    }

    private companion object {
        const val HOST = "127.0.0.1"
        const val LARGE_RESOURCE_URI = "test://large"
        const val CONCURRENT_CALLS = 10
        const val SPECIAL_TEXT =
            "quotes \" ' backslash \\ newline \n crlf \r\n tab \t unicode ü € 🚀 control \u0001 json {\"a\":[1]} </script>"

        // 100 000 distinct characters: truncation or reordered chunks change the text.
        val LARGE_TEXT = buildString { repeat(10_000) { append(it.toString().padStart(10, '0')) } }

        fun createServer(): Server = Server(
            serverInfo = Implementation("test-server", "1.0.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = null),
                    resources = ServerCapabilities.Resources(subscribe = null, listChanged = null),
                ),
            ),
        ) {
            addTool(name = "echo", description = "Echoes the text back after an optional delay") { request ->
                val arguments = request.params.arguments
                arguments?.get("delayMs")?.jsonPrimitive?.long?.let { delay(it) }
                val text = arguments?.get("text")?.jsonPrimitive?.content.orEmpty()
                CallToolResult(
                    content = listOf(TextContent(text)),
                    structuredContent = buildJsonObject {
                        put("text", text)
                    },
                )
            }
            addTool(name = "session-id", description = "Returns the id of the session serving the call") {
                CallToolResult(content = listOf(TextContent(sessionId)))
            }
            addResource(
                uri = LARGE_RESOURCE_URI,
                name = "large",
                description = "Large text resource",
                mimeType = "text/plain",
            ) { request ->
                ReadResourceResult(listOf(TextResourceContents(LARGE_TEXT, request.params.uri, "text/plain")))
            }
        }
    }
}
