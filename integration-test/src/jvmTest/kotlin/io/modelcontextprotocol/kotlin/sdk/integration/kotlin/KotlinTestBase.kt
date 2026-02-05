package io.modelcontextprotocol.kotlin.sdk.integration.kotlin

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.test.utils.Retry
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
    protected enum class TransportKind { SSE, STDIO, STREAMABLE_HTTP }
    protected open val transportKind: TransportKind = TransportKind.STDIO

    // STDIO-specific fields
    private var stdioServerTransport: StdioServerTransport? = null
    private var stdioClientInput: Source? = null
    private var stdioClientOutput: Sink? = null

    // StreamableHTTP-specific fields
    private var streamableHttpServerTransport: StreamableHttpServerTransport? = null

    protected abstract fun configureServerCapabilities(): ServerCapabilities
    protected abstract fun configureServer()

    @BeforeEach
    fun setUp() {
        setupServer()
        if (transportKind == TransportKind.SSE || transportKind == TransportKind.STREAMABLE_HTTP) {
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

            TransportKind.STREAMABLE_HTTP -> {
                val transport = StreamableHttpClientTransport(
                    client = HttpClient(CIO) {
                        install(SSE)
                    },
                    url = "http://$host:$port/mcp",
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

        when (transportKind) {
            TransportKind.SSE -> {
                serverEngine = embeddedServer(ServerCIO, host = host, port = port) {
                    install(ServerSSE)
                    routing {
                        mcp { server }
                    }
                }.start(wait = false)
            }

            TransportKind.STREAMABLE_HTTP -> {
                // Create StreamableHTTP server transport
                // Using JSON response mode for simpler testing (no SSE session required)
                val transport = StreamableHttpServerTransport(
                    enableJsonResponse = true, // Use JSON response mode for testing
                )
                // Use stateless mode to skip session validation for simpler testing
                transport.setSessionIdGenerator(null)
                streamableHttpServerTransport = transport

                // IMPORTANT: Create server session BEFORE starting the HTTP server
                // This ensures message handlers are set up before any requests come in
                runBlocking {
                    server.createSession(transport)
                }

                // Start embedded server with routing for StreamableHTTP
                serverEngine = embeddedServer(ServerCIO, host = host, port = port) {
                    // Install ContentNegotiation for JSON serialization
                    install(ContentNegotiation) {
                        json(McpJson)
                    }
                    routing {
                        route("/mcp") {
                            post {
                                transport.handlePostRequest(null, call)
                            }
                            get {
                                transport.handleGetRequest(null, call)
                            }
                            delete {
                                transport.handleDeleteRequest(call)
                            }
                        }
                    }
                }.start(wait = false)
            }

            TransportKind.STDIO -> {
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
        when (transportKind) {
            TransportKind.SSE, TransportKind.STREAMABLE_HTTP -> {
                if (::serverEngine.isInitialized) {
                    try {
                        serverEngine.stop(500, 1000)
                    } catch (e: Exception) {
                        println("Warning: Error during server stop: ${e.message}")
                    }
                }
                if (transportKind == TransportKind.STREAMABLE_HTTP) {
                    streamableHttpServerTransport?.let {
                        try {
                            runBlocking { it.close() }
                        } catch (e: Exception) {
                            println("Warning: Error during streamable http server stop: ${e.message}")
                        } finally {
                            streamableHttpServerTransport = null
                        }
                    }
                }
            }

            TransportKind.STDIO -> {
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
}
