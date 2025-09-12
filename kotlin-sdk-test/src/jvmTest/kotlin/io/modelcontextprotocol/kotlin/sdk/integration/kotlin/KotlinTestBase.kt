package io.modelcontextprotocol.kotlin.sdk.integration.kotlin

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.integration.utils.Retry
import io.modelcontextprotocol.kotlin.sdk.integration.utils.port
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.time.Duration.Companion.seconds
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

@Retry(times = 3)
abstract class KotlinTestBase {

    protected val host = "localhost"
    protected var port: Int = 0

    protected lateinit var server: Server
    protected lateinit var client: Client
    protected lateinit var serverEngine: EmbeddedServer<*, *>

    protected abstract fun configureServerCapabilities(): ServerCapabilities
    protected abstract fun configureServer()

    @BeforeEach
    fun setUp() {
        setupServer()
        port = serverEngine.port()
        runBlocking {
            setupClient()
        }
    }

    protected suspend fun setupClient() {
        val transport = SseClientTransport(
            HttpClient(CIO) {
                install(SSE)
            },
            "http://$host:$port",
        )
        client = Client(
            Implementation("test", "1.0"),
        )
        client.connect(transport)
    }

    protected fun setupServer() {
        val capabilities = configureServerCapabilities()

        server = Server(
            Implementation(name = "test-server", version = "1.0"),
            ServerOptions(capabilities = capabilities),
        )

        configureServer()

        serverEngine = embeddedServer(ServerCIO, host = host, port = 0) {
            install(ServerSSE)
            routing {
                mcp { server }
            }
        }.start(wait = false)
    }

    @AfterEach
    fun tearDown() {
        // close client
        if (::client.isInitialized) {
            try {
                runBlocking {
                    withTimeout(3.seconds) {
                        client.close()
                    }
                }
            } catch (e: Exception) {
                println("Warning: Error during client close: ${e.message}")
            }
        }

        // stop server
        if (::serverEngine.isInitialized) {
            try {
                serverEngine.stop(500, 1000)
            } catch (e: Exception) {
                println("Warning: Error during server stop: ${e.message}")
            }
        }
    }
}
