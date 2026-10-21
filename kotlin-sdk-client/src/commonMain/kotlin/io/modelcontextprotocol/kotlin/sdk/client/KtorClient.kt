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
 * @param endpointDiscoveryTimeout Maximum time to wait for the SSE connection and endpoint event.
 * @param requestBuilder Optional lambda to configure the HTTP request.
 * @return A [SseClientTransport] configured for MCP communication.
 */
public fun HttpClient.mcpSseTransport(
    urlString: String? = null,
    reconnectionTime: Duration? = null,
    endpointDiscoveryTimeout: Duration = DEFAULT_ENDPOINT_DISCOVERY_TIMEOUT,
    requestBuilder: HttpRequestBuilder.() -> Unit = {},
): SseClientTransport = SseClientTransport(this, urlString, reconnectionTime, endpointDiscoveryTimeout, requestBuilder)

/**
 * Creates and connects an MCP client over SSE using the provided HttpClient.
 *
 * @param urlString Optional URL of the MCP server.
 * @param reconnectionTime Optional duration to wait before attempting to reconnect.
 * @param endpointDiscoveryTimeout Maximum time to wait for the SSE connection and endpoint event.
 * @param requestBuilder Optional lambda to configure the HTTP request.
 * @return A connected [Client] ready for MCP communication.
 */
public suspend fun HttpClient.mcpSse(
    urlString: String? = null,
    reconnectionTime: Duration? = null,
    endpointDiscoveryTimeout: Duration = DEFAULT_ENDPOINT_DISCOVERY_TIMEOUT,
    requestBuilder: HttpRequestBuilder.() -> Unit = {},
): Client {
    val transport = mcpSseTransport(urlString, reconnectionTime, endpointDiscoveryTimeout, requestBuilder)
    val client = Client(
        Implementation(
            name = IMPLEMENTATION_NAME,
            version = LIB_VERSION,
        ),
    )
    client.connect(transport)
    return client
}
