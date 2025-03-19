package integration

import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpSse
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

class SseIntegrationTest {
    @Test
    fun `client should be able to connect to sse server`() { // runTest will cause network timeout issues
        runBlocking {
            val serverEngine = initServer()
            try {
                assertDoesNotThrow { initClient() }
            } finally {
                // Make sure to stop the server
                serverEngine.stop(1000, 2000)
            }
        }
    }

    private suspend fun initClient(): Client {
        return HttpClient(ClientCIO) { install(SSE) }.mcpSse("http://$URL:$PORT")
    }

    private fun initServer(): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        val server = Server(
            Implementation(name = "sse-e2e-test", version = "1.0.0"),
            ServerOptions(capabilities = ServerCapabilities()),
        )

        return embeddedServer(ServerCIO, host = URL, port = PORT) { mcp { server } }.start(wait = false)
    }

    companion object {
        private const val PORT = 3001
        private const val URL = "localhost"
    }
}