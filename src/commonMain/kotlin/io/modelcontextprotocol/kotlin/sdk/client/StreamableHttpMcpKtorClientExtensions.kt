package io.modelcontextprotocol.kotlin.sdk.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.LIB_VERSION
import io.modelcontextprotocol.kotlin.sdk.shared.IMPLEMENTATION_NAME

/**
 * Returns a new Streamable HTTP transport for the Model Context Protocol using the provided HttpClient.
 *
 * @param url URL of the MCP server.
 * @param requestBuilder Optional lambda to configure the HTTP request.
 * @return A [StreamableHttpClientTransport] configured for MCP communication.
 */
public fun HttpClient.mcpStreamableHttpTransport(
    url: String,
    requestBuilder: HttpRequestBuilder.() -> Unit = {},
): StreamableHttpClientTransport = StreamableHttpClientTransport(this, url, requestBuilder)

/**
 * Creates and connects an MCP client over Streamable HTTP using the provided HttpClient.
 *
 * @param url URL of the MCP server.
 * @param requestBuilder Optional lambda to configure the HTTP request.
 * @return A connected [Client] ready for MCP communication.
 */
public suspend fun HttpClient.mcpStreamableHttp(
    url: String,
    requestBuilder: HttpRequestBuilder.() -> Unit = {},
): Client {
    val transport = mcpStreamableHttpTransport(url, requestBuilder)
    val client = Client(
        Implementation(
            name = IMPLEMENTATION_NAME,
            version = LIB_VERSION
        )
    )
    client.connect(transport)
    return client
}