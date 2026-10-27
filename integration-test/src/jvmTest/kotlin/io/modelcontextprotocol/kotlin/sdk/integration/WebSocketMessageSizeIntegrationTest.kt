package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.engine.embeddedServer
import io.ktor.websocket.Frame
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpWebSocketTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpWebSocket
import io.modelcontextprotocol.kotlin.sdk.shared.MCP_SUBPROTOCOL
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.test.utils.actualPort
import io.modelcontextprotocol.kotlin.test.utils.runIntegrationTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

class WebSocketMessageSizeIntegrationTest {

    @Test
    fun `server closes a connection that sends a message over 16 MiB and keeps serving others`() =
        runIntegrationTest(timeout = 30.seconds) {
            val server = Server(Implementation("test-server", "1.0.0"), ServerOptions(ServerCapabilities()))
            val engine = embeddedServer(ServerCIO, host = HOST, port = 0) { mcpWebSocket { server } }
                .startSuspend(wait = false)
            val httpClient = HttpClient(ClientCIO) { install(WebSockets) }
            val url = "ws://$HOST:${engine.actualPort()}"
            try {
                val peer = httpClient.webSocketSession(url) {
                    header(HttpHeaders.SecWebSocketProtocol, MCP_SUBPROTOCOL)
                }

                // A valid request: without the limit the server would answer it instead of closing.
                peer.send(Frame.Text(ping(padding = "a".repeat(16 * 1024 * 1024))))

                peer.incoming.receiveCatching().getOrNull() shouldBe null
                val client = Client(Implementation("test-client", "1.0.0"))
                client.connect(httpClient.mcpWebSocketTransport(url))
                client.ping()
                client.close()
            } finally {
                httpClient.close()
                engine.stopSuspend(gracePeriodMillis = 0, timeoutMillis = 1_000)
                server.close()
            }
        }

    private fun ping(padding: String): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", 1)
        put("method", "ping")
        putJsonObject("params") { put("padding", padding) }
    }.toString()

    private companion object {
        const val HOST = "127.0.0.1"
    }
}
