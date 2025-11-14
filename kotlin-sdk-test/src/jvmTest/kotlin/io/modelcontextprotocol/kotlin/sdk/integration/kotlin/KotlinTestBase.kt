package io.modelcontextprotocol.kotlin.sdk.integration.kotlin

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.integration.utils.Retry
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.PipedInputStream
import java.io.PipedOutputStream
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

    // Transport selection
    protected enum class TransportKind { SSE, STDIO }
    protected open val transportKind: TransportKind = TransportKind.STDIO

    // STDIO-specific fields
    private var stdioServerTransport: StdioServerTransport? = null
    private var stdioClientInput: Source? = null
    private var stdioClientOutput: Sink? = null

    protected abstract fun configureServerCapabilities(): ServerCapabilities
    protected abstract fun configureServer()

    @BeforeEach
    fun setUp() {
        setupServer()
        if (transportKind == TransportKind.SSE) {
            await
                .ignoreExceptions()
                .until {
                    port = runBlocking { serverEngine.engine.resolvedConnectors().first().port }
                    port != 0
                }
        }
        runBlocking {
            setupClient()
        }
    }

    protected suspend fun setupClient() {
        when (transportKind) {
            TransportKind.SSE -> {
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

            TransportKind.STDIO -> {
                val input = checkNotNull(stdioClientInput) { "STDIO client input not initialized" }
                val output = checkNotNull(stdioClientOutput) { "STDIO client output not initialized" }
                val transport = StdioClientTransport(
                    input = input,
                    output = output,
                )
                client = Client(
                    Implementation("test", "1.0"),
                )
                client.connect(transport)
            }
        }
    }

    protected fun setupServer() {
        val capabilities = configureServerCapabilities()

        server = Server(
            Implementation(name = "test-server", version = "1.0"),
            ServerOptions(capabilities = capabilities),
        )

        configureServer()

        if (transportKind == TransportKind.SSE) {
            serverEngine = embeddedServer(ServerCIO, host = host, port = port) {
                install(ServerSSE)
                routing {
                    mcp { server }
                }
            }.start(wait = false)
        } else {
            // Create in-memory stdio pipes: client->server and server->client
            val clientToServerOut = PipedOutputStream()
            val clientToServerIn = PipedInputStream(clientToServerOut)

            val serverToClientOut = PipedOutputStream()
            val serverToClientIn = PipedInputStream(serverToClientOut)

            // Server transport reads from client and writes to client
            val serverTransport = StdioServerTransport(
                inputStream = clientToServerIn.asSource().buffered(),
                outputStream = serverToClientOut.asSink().buffered(),
            )
            stdioServerTransport = serverTransport

            // Prepare client-side streams for later client initialization
            stdioClientInput = serverToClientIn.asSource().buffered()
            stdioClientOutput = clientToServerOut.asSink().buffered()

            // Start server transport by connecting the server
            runBlocking {
                server.createSession(serverTransport)
            }
        }
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
        if (transportKind == TransportKind.SSE) {
            if (::serverEngine.isInitialized) {
                try {
                    serverEngine.stop(500, 1000)
                } catch (e: Exception) {
                    println("Warning: Error during server stop: ${e.message}")
                }
            }
        } else {
            stdioServerTransport?.let {
                try {
                    runBlocking { it.close() }
                } catch (e: Exception) {
                    println("Warning: Error during stdio server stop: ${e.message}")
                } finally {
                    stdioServerTransport = null
                    stdioClientInput = null
                    stdioClientOutput = null
                }
            }
        }
    }
}
