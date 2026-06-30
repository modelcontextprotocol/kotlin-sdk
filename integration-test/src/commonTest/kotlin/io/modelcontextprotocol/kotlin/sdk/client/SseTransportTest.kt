package io.modelcontextprotocol.kotlin.sdk.client

import io.ktor.client.HttpClient
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.shared.BaseTransportTest
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import io.ktor.client.plugins.sse.SSE as ClientSSE
import io.ktor.server.sse.SSE as ServerSSE

class SseTransportTest : BaseTransportTest() {

    private suspend fun EmbeddedServer<*, *>.actualPort() = engine.resolvedConnectors().first().port

    private lateinit var mcpServer: Server

    @BeforeTest
    fun setUp() {
        mcpServer = Server(
            serverInfo = Implementation(
                name = "test-server",
                version = "1.0",
            ),
            options = ServerOptions(ServerCapabilities()),
        )
    }

    @Test
    fun `should start then close cleanly`() = runTest {
        val server = embeddedServer(CIO, port = 0) {
            install(ServerSSE)
            routing {
                mcp { mcpServer }
            }
        }.startSuspend(wait = false)

        val actualPort = server.actualPort()

        val transport = HttpClient {
            install(ClientSSE)
        }.mcpSseTransport {
            url {
                host = "localhost"
                this.port = actualPort
            }
        }

        try {
            testTransportOpenClose(transport)
        } finally {
            server.stopSuspend()
        }
    }

    @Test
    fun `should read messages`() = runTest {
        val server = embeddedServer(CIO, port = 0) {
            install(ServerSSE)
            routing {
                mcp { mcpServer }
            }
        }.startSuspend(wait = false)

        val actualPort = server.actualPort()

        val transport = HttpClient {
            install(ClientSSE)
        }.mcpSseTransport {
            url {
                host = "localhost"
                this.port = actualPort
            }
        }

        val client = Client(
            clientInfo = Implementation(name = "test-client", version = "1.0"),
            options = ClientOptions(),
        )

        try {
            withContext(Dispatchers.Default) {
                client.connect(transport)
                client.ping()
            }
        } finally {
            client.close()
            server.stopSuspend()
        }
    }

    @Test
    fun `test sse path not root path`() = runTest {
        val server = embeddedServer(CIO, port = 0) {
            install(ServerSSE)
            routing {
                mcp("/sse") { mcpServer }
            }
        }.startSuspend(wait = false)

        val actualPort = server.actualPort()

        val transport = HttpClient {
            install(ClientSSE)
        }.mcpSseTransport {
            url {
                host = "localhost"
                this.port = actualPort
                pathSegments = listOf("sse")
            }
        }

        val client = Client(
            clientInfo = Implementation(name = "test-client", version = "1.0"),
            options = ClientOptions(),
        )

        try {
            withContext(Dispatchers.Default) {
                client.connect(transport)
                client.ping()
            }
        } finally {
            client.close()
            server.stopSuspend()
        }
    }
}
