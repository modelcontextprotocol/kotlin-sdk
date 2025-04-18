import shared.runSseMcpServerUsingKtorPlugin
import shared.runSseMcpServerWithPlainConfiguration

/**
 * Start sse-server mcp on port 3001.
 *
 * @param args
 * - "--sse-server-ktor <port>": Runs an SSE MCP server using Ktor plugin (default if no argument is provided).
 * - "--sse-server <port>": Runs an SSE MCP server with a plain configuration.
 */
suspend fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: "--sse-server-ktor"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 3001
    when (command) {
        "--sse-server-ktor" -> runSseMcpServerUsingKtorPlugin(port)
        "--sse-server" -> runSseMcpServerWithPlainConfiguration(port)
        else -> {
            error("Unknown command: $command")
        }
    }
}