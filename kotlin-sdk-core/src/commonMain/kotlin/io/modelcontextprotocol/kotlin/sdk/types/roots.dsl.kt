package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi

/**
 * Creates a [ListRootsRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [meta][ListRootsRequestBuilder.meta] - Metadata for the request
 *
 * Example with no parameters:
 * ```kotlin
 * val request = ListRootsRequest { }
 * ```
 *
 * Example with metadata:
 * ```kotlin
 * val request = ListRootsRequest {
 *     meta {
 *         put("context", "initialization")
 *     }
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list roots request
 * @return A configured [ListRootsRequest] instance
 * @see ListRootsRequestBuilder
 * @see ListRootsRequest
 */
@ExperimentalMcpApi
public inline operator fun ListRootsRequest.Companion.invoke(
    block: ListRootsRequestBuilder.() -> Unit,
): ListRootsRequest = ListRootsRequestBuilder().apply(block).build()

/**
 * DSL builder for constructing [ListRootsRequest] instances.
 *
 * This builder retrieves the list of root URIs provided by the client.
 * All fields are optional.
 *
 * ## Optional
 * - [meta] - Metadata for the request
 *
 * @see ListRootsRequest
 */
@McpDsl
public class ListRootsRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    @PublishedApi
    override fun build(): ListRootsRequest {
        val params = meta?.let { BaseRequestParams(meta = it) }
        return ListRootsRequest(params)
    }
}
