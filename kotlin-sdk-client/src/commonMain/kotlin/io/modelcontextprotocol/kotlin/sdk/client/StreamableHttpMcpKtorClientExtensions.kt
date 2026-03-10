package io.modelcontextprotocol.kotlin.sdk.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.modelcontextprotocol.kotlin.sdk.LIB_VERSION
import io.modelcontextprotocol.kotlin.sdk.shared.IMPLEMENTATION_NAME
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Returns a new Streamable HTTP transport for the Model Context Protocol using the provided HttpClient.
 *
 * @param url URL of the MCP server.
 * @param reconnectionOptions Options for controlling SSE reconnection behavior.
 * @param requestBuilder Optional lambda to configure the HTTP request.
 * @return A [StreamableHttpClientTransport] configured for MCP communication.
 */
public fun HttpClient.mcpStreamableHttpTransport(
    url: String,
    reconnectionOptions: ReconnectionOptions = ReconnectionOptions(),
    requestBuilder: HttpRequestBuilder.() -> Unit = {},
): StreamableHttpClientTransport =
    StreamableHttpClientTransport(this, url, reconnectionOptions, requestBuilder = requestBuilder)

/**
 * Returns a new Streamable HTTP transport for the Model Context Protocol using the provided HttpClient.
 *
 * @param url URL of the MCP server.
 * @param reconnectionTime Optional duration to wait before attempting to reconnect.
 * @param requestBuilder Optional lambda to configure the HTTP request.
 * @return A [StreamableHttpClientTransport] configured for MCP communication.
 */
@Deprecated(
    "Use overload with ReconnectionOptions",
    replaceWith = ReplaceWith(
        "mcpStreamableHttpTransport(url, ReconnectionOptions(initialReconnectionDelay = reconnectionTime ?: 1.seconds), requestBuilder)",
    ),
)
public fun HttpClient.mcpStreamableHttpTransport(
    url: String,
    reconnectionTime: Duration?,
    requestBuilder: HttpRequestBuilder.() -> Unit = {},
): StreamableHttpClientTransport = StreamableHttpClientTransport(
    this,
    url,
    ReconnectionOptions(initialReconnectionDelay = reconnectionTime ?: 1.seconds),
    requestBuilder = requestBuilder,
)

/**
 * Creates and connects an MCP client over Streamable HTTP using the provided HttpClient.
 *
 * @param url URL of the MCP server.
 * @param reconnectionOptions Options for controlling SSE reconnection behavior.
 * @param requestBuilder Optional lambda to configure the HTTP request.
 * @return A connected [Client] ready for MCP communication.
 */
public suspend fun HttpClient.mcpStreamableHttp(
    url: String,
    reconnectionOptions: ReconnectionOptions = ReconnectionOptions(),
    requestBuilder: HttpRequestBuilder.() -> Unit = {},
): Client {
    val transport = mcpStreamableHttpTransport(url, reconnectionOptions, requestBuilder)
    val client = Client(Implementation(name = IMPLEMENTATION_NAME, version = LIB_VERSION))
    client.connect(transport)
    return client
}

/**
 * Creates and connects an MCP client over Streamable HTTP using the provided HttpClient.
 *
 * @param url URL of the MCP server.
 * @param reconnectionTime Optional duration to wait before attempting to reconnect.
 * @param requestBuilder Optional lambda to configure the HTTP request.
 * @return A connected [Client] ready for MCP communication.
 */
@Deprecated(
    "Use overload with ReconnectionOptions",
    replaceWith = ReplaceWith(
        "mcpStreamableHttp(url, ReconnectionOptions(initialReconnectionDelay = reconnectionTime ?: 1.seconds), requestBuilder)",
    ),
)
public suspend fun HttpClient.mcpStreamableHttp(
    url: String,
    reconnectionTime: Duration?,
    requestBuilder: HttpRequestBuilder.() -> Unit = {},
): Client {
    @Suppress("DEPRECATION")
    val transport = mcpStreamableHttpTransport(url, reconnectionTime, requestBuilder)
    val client = Client(Implementation(name = IMPLEMENTATION_NAME, version = LIB_VERSION))
    client.connect(transport)
    return client
}
