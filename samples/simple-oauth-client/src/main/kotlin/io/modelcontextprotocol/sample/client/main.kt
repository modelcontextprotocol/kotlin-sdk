package io.modelcontextprotocol.sample.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import java.awt.Desktop
import java.net.URI

private const val DEFAULT_SERVER_URL = "http://localhost:3000/mcp"

/**
 * Connects to a protected MCP server over Streamable HTTP, signs in with OAuth and lists its tools.
 *
 * Usage: `simple-oauth-client [server-url] [tool-name]`
 *
 * Set `MCP_CLIENT_ID` (and `MCP_CLIENT_SECRET` if needed) to use a pre-registered client
 * instead of dynamic client registration, and `MCP_CALLBACK_PORT` to pin the redirect port.
 */
fun main(args: Array<String>): Unit = runBlocking {
    val serverUrl = args.getOrNull(0) ?: DEFAULT_SERVER_URL
    val toolName = args.getOrNull(1)

    val preRegistered = System.getenv("MCP_CLIENT_ID")?.let {
        ClientCredentials(clientId = it, clientSecret = System.getenv("MCP_CLIENT_SECRET"))
    }
    val callbackPort = System.getenv("MCP_CALLBACK_PORT")?.toInt() ?: 0

    HttpClient(CIO).use { oauthHttp ->
        val oauthFlow = OAuthFlow(
            httpClient = oauthHttp,
            openBrowser = ::openInBrowser,
            preRegistered = preRegistered,
            callbackPort = callbackPort,
        )
        authorizedHttpClient(serverUrl, oauthFlow).use { http ->
            val client = http.mcpStreamableHttp(serverUrl)
            try {
                val server = client.serverVersion
                println("Connected to ${server?.name ?: "server"} ${server?.version.orEmpty()}".trim())

                val tools = client.listTools().tools
                println("Tools:")
                tools.forEach { println("  - ${it.name}: ${it.description.orEmpty()}") }

                if (toolName != null) {
                    val result = client.callTool(name = toolName, arguments = emptyMap())
                    println("Result of $toolName:")
                    result.content.filterIsInstance<TextContent>().forEach { println(it.text) }
                }
            } finally {
                client.close()
            }
        }
    }
}

private fun openInBrowser(url: String) {
    println("Open this URL to sign in:\n$url")
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }
}
