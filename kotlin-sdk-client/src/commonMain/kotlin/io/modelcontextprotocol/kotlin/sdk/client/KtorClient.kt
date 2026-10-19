package io.modelcontextprotocol.kotlin.sdk.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.modelcontextprotocol.kotlin.sdk.LIB_VERSION
import io.modelcontextprotocol.kotlin.sdk.shared.IMPLEMENTATION_NAME
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlin.time.Duration

/**
 * Returns a new SSE transport for the Model Context Protocol using the provided HttpClient.
 *
 * @param urlString Optional URL of the MCP server.
 * @param reconnectionTime Optional duration to wait before attempting to reconnect.
 * @param requestBuilder Optional lambda to configure the HTTP request.
 * @return A [SseClientTransport] configured for MCP communication.
 */
@Deprecated(
    "The SSE transport is deprecated in the MCP specification (SEP-2596) " +
        "and will be removed in a future release. Use mcpStreamableHttpTransport instead.",
    level = DeprecationLevel.WARNING,
)
@Suppress("DEPRECATION")
public fun HttpClient.mcpSseTransport(
    urlString: String? = null,
    reconnectionTime: Duration? = null,
    requestBuilder: HttpRequestBuilder.() -> Unit = {},
): SseClientTransport = SseClientTransport(this, urlString, reconnectionTime, requestBuilder)

/**
 * Creates and connects an MCP client over SSE using the provided HttpClient.
 *
 * @param urlString Optional URL of the MCP server.
 * @param reconnectionTime Optional duration to wait before attempting to reconnect.
 * @param requestBuilder Optional lambda to configure the HTTP request.
 * @return A connected [Client] ready for MCP communication.
 */
@Deprecated(
    "The SSE transport is deprecated in the MCP specification (SEP-2596) " +
        "and will be removed in a future release. Use mcpStreamableHttp instead.",
    level = DeprecationLevel.WARNING,
)
@Suppress("DEPRECATION")
public suspend fun HttpClient.mcpSse(
    urlString: String? = null,
    reconnectionTime: Duration? = null,
    requestBuilder: HttpRequestBuilder.() -> Unit = {},
): Client {
    val transport = mcpSseTransport(urlString, reconnectionTime, requestBuilder)
    val client = Client(
        Implementation(
            name = IMPLEMENTATION_NAME,
            version = LIB_VERSION,
        ),
    )
    client.connect(transport)
    return client
}
