import io.ktor.server.engine.EmbeddedServer
import io.modelcontextprotocol.sample.server.runSseMcpServerUsingKtorPlugin
import io.modelcontextprotocol.sample.server.runSseMcpServerWithPlainConfiguration

enum class McpServerType(
    val sseEndpoint: String,
    val serverFactory: (port: Int) -> EmbeddedServer<*, *>
) {
    KTOR_PLUGIN(
        sseEndpoint = "",
        serverFactory = { port -> runSseMcpServerUsingKtorPlugin(port, wait = false) }
    ),
    PLAIN_CONFIGURATION(
        sseEndpoint = "/sse",
        serverFactory = { port -> runSseMcpServerWithPlainConfiguration(port, wait = false) }
    )
}
