package io.modelcontextprotocol.kotlin.sdk.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.LIB_VERSION
import io.modelcontextprotocol.kotlin.sdk.shared.IMPLEMENTATION_NAME

private val logger = KotlinLogging.logger {}

/**
 * Returns a new WebSocket transport for the Model Context Protocol using the provided HttpClient.
 *
 * @param urlString Optional URL of the MCP server.
 * @param requestBuilder Optional lambda to configure the HTTP request.
 * @return A [WebSocketClientTransport] configured for MCP communication.
 */
public fun HttpClient.mcpWebSocketTransport(
    urlString: String? = null,
    requestBuilder: HttpRequestBuilder.() -> Unit = {},
): WebSocketClientTransport = WebSocketClientTransport(this, urlString, requestBuilder)

/**
 * Creates and connects an MCP client over WebSocket using the provided HttpClient.
 *
 * @param urlString Optional URL of the MCP server.
 * @param requestBuilder Optional lambda to configure the HTTP request.
 * @return A connected [Client] ready for MCP communication.
 */
public suspend fun HttpClient.mcpWebSocket(
    urlString: String? = null,
    requestBuilder: HttpRequestBuilder.() -> Unit = {},
): Client {
    val transport = mcpWebSocketTransport(urlString, requestBuilder)
    val client = Client(
        Implementation(
            name = IMPLEMENTATION_NAME,
            version = LIB_VERSION,
        ),
    )
    logger.debug { "Client started to connect to server" }
    client.connect(transport)
    logger.debug { "Client finished to connect to server" }
    return client
}
