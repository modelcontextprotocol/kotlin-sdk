import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpSseTransport
import io.modelcontextprotocol.sample.server.runSseMcpServerUsingKtorPlugin
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

object TestEnvironment {

    val server = runSseMcpServerUsingKtorPlugin(0, wait = false)
    val client: Client

    init {
        client = runBlocking {
            val port = server.engine.resolvedConnectors().single().port
            initClient(port)
        }

        Runtime.getRuntime().addShutdownHook(
            Thread {
                println("🏁 Shutting down server")
                server.stop(500, 700, TimeUnit.MILLISECONDS)
                println("☑️ Shutdown complete")
            },
        )
    }

    private suspend fun initClient(port: Int): Client {
        val client = Client(
            Implementation(name = "test-client", version = "0.1.0"),
        )

        val httpClient = HttpClient(CIO) {
            install(SSE)
        }

        // Create a transport wrapper that captures the session ID and received messages
        val transport = httpClient.mcpSseTransport {
            url {
                this.host = "127.0.0.1"
                this.port = port
            }
        }
        client.connect(transport)
        return client
    }
}
