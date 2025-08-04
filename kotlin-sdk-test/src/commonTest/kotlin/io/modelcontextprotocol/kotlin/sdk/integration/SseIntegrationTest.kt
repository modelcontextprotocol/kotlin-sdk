package io.modelcontextprotocol.kotlin.sdk.integration

import io.ktor.client.HttpClient
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpSse
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.fail
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

private const val URL = "127.0.0.1"

class SseIntegrationTest {
    @Test
    fun `client should be able to connect to sse server`() = runTest {
        val serverEngine = initServer()
        try {
            withContext(Dispatchers.Default) {
                val port = serverEngine.engine.resolvedConnectors().first().port
                val client = initClient(port)
                client.close()
            }
        } catch (e: Exception) {
            fail("Failed to connect client: $e")
        } finally {
            // Make sure to stop the server
            serverEngine.stopSuspend(1000, 2000)
        }
    }

    private suspend fun initClient(port: Int): Client {
        return HttpClient(ClientCIO) { install(ClientSSE) }.mcpSse("http://$URL:$port")
    }

    private suspend fun initServer(): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        val server = Server(
            Implementation(name = "sse-e2e-test", version = "1.0.0"),
            ServerOptions(capabilities = ServerCapabilities()),
        )

        return embeddedServer(ServerCIO, host = URL, port = 0) {
            install(ServerSSE)
            routing {
                mcp { server }
            }
        }.startSuspend(wait = false)
    }
}