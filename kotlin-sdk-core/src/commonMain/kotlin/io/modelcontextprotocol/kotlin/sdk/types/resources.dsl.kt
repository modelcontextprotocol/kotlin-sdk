package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Creates a [ListResourcesRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [cursor][ListResourcesRequestBuilder.cursor] - Pagination cursor for fetching next page
 * - [meta][ListResourcesRequestBuilder.meta] - Metadata for the request
 *
 * Example without pagination:
 * ```kotlin
 * val request = buildListResourcesRequest { }
 * ```
 *
 * Example with pagination:
 * ```kotlin
 * val request = buildListResourcesRequest {
 *     cursor = "eyJwYWdlIjogMn0="
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list resources request
 * @return A configured [ListResourcesRequest] instance
 * @see ListResourcesRequestBuilder
 * @see ListResourcesRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
internal inline fun buildListResourcesRequest(block: ListResourcesRequestBuilder.() -> Unit): ListResourcesRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ListResourcesRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [ListResourcesRequest] instances.
 *
 * This builder retrieves a list of available resources, with optional pagination support.
 * All fields are optional.
 *
 * ## Optional
 * - [cursor] - Pagination cursor (inherited from [PaginatedRequestBuilder])
 * - [meta] - Metadata for the request (inherited from [RequestBuilder])
 *
 * @see buildListResourcesRequest
 * @see ListResourcesRequest
 * @see PaginatedRequestBuilder
 */
@McpDsl
public class ListResourcesRequestBuilder @PublishedApi internal constructor() : PaginatedRequestBuilder() {
    @PublishedApi
    override fun build(): ListResourcesRequest {
        val params = paginatedRequestParams(cursor = cursor, meta = meta)
        return ListResourcesRequest(params)
    }
}

/**
 * Creates a [ReadResourceRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [uri][ReadResourceRequestBuilder.uri] - The URI of the resource to read
 *
 * ## Optional
 * - [meta][ReadResourceRequestBuilder.meta] - Metadata for the request
 *
 * Example:
 * ```kotlin
 * val request = buildReadResourceRequest {
 *     uri = "file:///path/to/resource.txt"
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the read resource request
 * @return A configured [ReadResourceRequest] instance
 * @see ReadResourceRequestBuilder
 * @see ReadResourceRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
internal inline fun buildReadResourceRequest(block: ReadResourceRequestBuilder.() -> Unit): ReadResourceRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ReadResourceRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [ReadResourceRequest] instances.
 *
 * This builder reads the contents of a specific resource by URI.
 *
 * ## Required
 * - [uri] - The URI of the resource to read
 *
 * ## Optional
 * - [meta] - Metadata for the request
 *
 * @see buildReadResourceRequest
 * @see ReadResourceRequest
 */
@McpDsl
public class ReadResourceRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    /**
     * The URI of the resource to read. This is a required field.
     *
     * Example: `uri = "file:///path/to/resource.txt"`
     */
    public var uri: String? = null

    @PublishedApi
    override fun build(): ReadResourceRequest {
        val uri = requireNotNull(uri) {
            "Missing required field 'uri'. Example: uri = \"file:///path/to/resource.txt\""
        }

        val params = ReadResourceRequestParams(uri = uri, meta = meta)
        return ReadResourceRequest(params)
    }
}

/**
 * Creates a [SubscribeRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [uri][SubscribeRequestBuilder.uri] - The URI of the resource to subscribe to
 *
 * ## Optional
 * - [meta][SubscribeRequestBuilder.meta] - Metadata for the request
 *
 * Example:
 * ```kotlin
 * val request = buildSubscribeRequest {
 *     uri = "file:///path/to/resource.txt"
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the subscribe request
 * @return A configured [SubscribeRequest] instance
 * @see SubscribeRequestBuilder
 * @see SubscribeRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
internal inline fun buildSubscribeRequest(block: SubscribeRequestBuilder.() -> Unit): SubscribeRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return SubscribeRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [SubscribeRequest] instances.
 *
 * This builder subscribes to updates for a specific resource by URI.
 *
 * ## Required
 * - [uri] - The URI of the resource to subscribe to
 *
 * ## Optional
 * - [meta] - Metadata for the request
 *
 * @see buildSubscribeRequest
 * @see SubscribeRequest
 */
@McpDsl
public class SubscribeRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    /**
     * The URI of the resource to subscribe to. This is a required field.
     *
     * Example: `uri = "file:///path/to/resource.txt"`
     */
    public var uri: String? = null

    @PublishedApi
    override fun build(): SubscribeRequest {
        val uri = requireNotNull(uri) {
            "Missing required field 'uri'. Example: uri = \"file:///path/to/resource.txt\""
        }

        val params = SubscribeRequestParams(uri = uri, meta = meta)
        return SubscribeRequest(params)
    }
}

/**
 * Creates an [UnsubscribeRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [uri][UnsubscribeRequestBuilder.uri] - The URI of the resource to unsubscribe from
 *
 * ## Optional
 * - [meta][UnsubscribeRequestBuilder.meta] - Metadata for the request
 *
 * Example:
 * ```kotlin
 * val request = buildUnsubscribeRequest {
 *     uri = "file:///path/to/resource.txt"
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the unsubscribe request
 * @return A configured [UnsubscribeRequest] instance
 * @see UnsubscribeRequestBuilder
 * @see UnsubscribeRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
internal inline fun buildUnsubscribeRequest(block: UnsubscribeRequestBuilder.() -> Unit): UnsubscribeRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return UnsubscribeRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [UnsubscribeRequest] instances.
 *
 * This builder unsubscribes from updates for a specific resource by URI.
 *
 * ## Required
 * - [uri] - The URI of the resource to unsubscribe from
 *
 * ## Optional
 * - [meta] - Metadata for the request
 *
 * @see buildUnsubscribeRequest
 * @see UnsubscribeRequest
 */
@McpDsl
public class UnsubscribeRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    /**
     * The URI of the resource to unsubscribe from. This is a required field.
     *
     * Example: `uri = "file:///path/to/resource.txt"`
     */
    public var uri: String? = null

    @PublishedApi
    override fun build(): UnsubscribeRequest {
        val uri = requireNotNull(uri) {
            "Missing required field 'uri'. Example: uri = \"file:///path/to/resource.txt\""
        }

        val params = UnsubscribeRequestParams(uri = uri, meta = meta)
        return UnsubscribeRequest(params)
    }
}

/**
 * Creates a [ListResourceTemplatesRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [cursor][ListResourceTemplatesRequestBuilder.cursor] - Pagination cursor for fetching next page
 * - [meta][ListResourceTemplatesRequestBuilder.meta] - Metadata for the request
 *
 * Example without pagination:
 * ```kotlin
 * val request = buildListResourceTemplatesRequest { }
 * ```
 *
 * Example with pagination:
 * ```kotlin
 * val request = buildListResourceTemplatesRequest {
 *     cursor = "eyJwYWdlIjogMn0="
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list resource templates request
 * @return A configured [ListResourceTemplatesRequest] instance
 * @see ListResourceTemplatesRequestBuilder
 * @see ListResourceTemplatesRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
internal inline fun buildListResourceTemplatesRequest(
    block: ListResourceTemplatesRequestBuilder.() -> Unit,
): ListResourceTemplatesRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ListResourceTemplatesRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [ListResourceTemplatesRequest] instances.
 *
 * This builder retrieves a list of available resource templates, with optional pagination support.
 * All fields are optional.
 *
 * ## Optional
 * - [cursor] - Pagination cursor (inherited from [PaginatedRequestBuilder])
 * - [meta] - Metadata for the request (inherited from [RequestBuilder])
 *
 * @see buildListResourceTemplatesRequest
 * @see ListResourceTemplatesRequest
 * @see PaginatedRequestBuilder
 */
@McpDsl
public class ListResourceTemplatesRequestBuilder @PublishedApi internal constructor() : PaginatedRequestBuilder() {
    @PublishedApi
    override fun build(): ListResourceTemplatesRequest {
        val params = paginatedRequestParams(cursor = cursor, meta = meta)
        return ListResourceTemplatesRequest(params)
    }
}
