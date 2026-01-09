package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Creates a [ListRootsRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [meta][ListRootsRequestBuilder.meta] - Metadata for the request
 *
 * Example with no parameters:
 * ```kotlin
 * val request = buildListRootsRequest { }
 * ```
 *
 * Example with metadata:
 * ```kotlin
 * val request = buildListRootsRequest {
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
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
public inline fun buildListRootsRequest(block: ListRootsRequestBuilder.() -> Unit): ListRootsRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ListRootsRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [ListRootsRequest] instances.
 *
 * This builder retrieves the list of root URIs provided by the client.
 * All fields are optional.
 *
 * ## Optional
 * - [meta] - Metadata for the request
 *
 * @see buildListRootsRequest
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
