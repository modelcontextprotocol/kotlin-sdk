import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.engine.EmbeddedServer
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpSseTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class TestEnvironment(private val serverConfig: McpServerType) {

    val server: EmbeddedServer<*, *> = serverConfig.serverFactory(0)
    val client: Client

    init {
        client = runBlocking {
            val port = server.engine.resolvedConnectors().single().port
            initClient(port, serverConfig)
        }

        Runtime.getRuntime().addShutdownHook(
            Thread {
                println("🏁 Shutting down server (${serverConfig.name})")
                server.stop(500, 700, TimeUnit.MILLISECONDS)
                println("☑️ Shutdown complete")
            },
        )
    }

    private suspend fun initClient(port: Int, config: McpServerType): Client {
        val client = Client(
            Implementation(name = "test-client", version = "0.1.0"),
        )

        val httpClient = HttpClient(CIO) {
            install(SSE)
        }

        val transport = httpClient.mcpSseTransport("http://127.0.0.1:$port/${config.sseEndpoint}")
        client.connect(transport)
        return client
    }
}
