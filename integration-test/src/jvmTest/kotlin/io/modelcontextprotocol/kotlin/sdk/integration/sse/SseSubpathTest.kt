package io.modelcontextprotocol.kotlin.sdk.integration.sse

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.test.utils.actualPort
import io.modelcontextprotocol.kotlin.test.utils.runIntegrationTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

private const val HOST = "127.0.0.1"
private const val SSE_PATH = "/api/mcp/sse"

class SseSubpathTest {

    @Test
    fun `client connects to a server mounted on a subpath`() = runIntegrationTest(timeout = 20.seconds) {
        val server = embeddedServer(ServerCIO, host = HOST, port = 0) {
            install(ServerSSE)
            routing {
                mcp(SSE_PATH) {
                    Server(
                        serverInfo = Implementation("test-server", "1.0.0"),
                        options = ServerOptions(ServerCapabilities()),
                    )
                }
            }
        }.startSuspend(wait = false)
        val httpClient = HttpClient(ClientCIO) { install(SSE) }
        val client = Client(Implementation("test-client", "1.0.0"))
        try {
            // No requestBuilder: the client must follow the query-only endpoint the server announces.
            client.connect(SseClientTransport(httpClient, "http://$HOST:${server.actualPort()}$SSE_PATH"))

            client.serverVersion?.name shouldBe "test-server"
            client.ping()
        } finally {
            client.close()
            httpClient.close()
            server.stopSuspend(gracePeriodMillis = 0, timeoutMillis = 1_000)
        }
    }
}
