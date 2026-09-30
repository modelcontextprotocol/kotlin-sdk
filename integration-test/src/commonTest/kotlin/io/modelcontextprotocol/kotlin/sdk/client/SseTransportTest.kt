package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import io.ktor.client.plugins.sse.SSE as ClientSSE
import io.ktor.server.sse.SSE as ServerSSE

class SseTransportTest {

    @Test
    fun `should connect ping and close over SSE`() = runTest {
        val mcpServer = Server(
            serverInfo = Implementation(name = "test-server", version = "1.0"),
            options = ServerOptions(ServerCapabilities()),
        )
        val server = embeddedServer(CIO, port = 0) {
            install(ServerSSE)
            routing { mcp { mcpServer } }
        }.startSuspend(wait = false)
        val serverPort = server.engine.resolvedConnectors().first().port
        val httpClient = HttpClient { install(ClientSSE) }
        val transport = httpClient.mcpSseTransport {
            url {
                host = "localhost"
                port = serverPort
            }
        }
        var closed = false
        transport.onClose { closed = true }
        val client = Client(clientInfo = Implementation(name = "test-client", version = "1.0"))

        try {
            withContext(Dispatchers.Default) {
                client.connect(transport)
                client.ping()
                client.close()
            }
            closed shouldBe true
        } finally {
            httpClient.close()
            server.stopSuspend(gracePeriodMillis = 0, timeoutMillis = 500)
        }
    }
}
