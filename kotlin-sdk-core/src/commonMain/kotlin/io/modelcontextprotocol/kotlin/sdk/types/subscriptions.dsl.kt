package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

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
 * val request = SubscribeRequest {
 *     uri = "file:///path/to/resource.txt"
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the subscribe request
 * @return A configured [SubscribeRequest] instance
 * @see SubscribeRequestBuilder
 * @see SubscribeRequest
 */
@ExperimentalMcpApi
public inline operator fun SubscribeRequest.Companion.invoke(
    block: SubscribeRequestBuilder.() -> Unit,
): SubscribeRequest = SubscribeRequestBuilder().apply(block).build()

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
@Deprecated(
    message = "Use SubscribeRequest { } instead",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("SubscribeRequest{apply(block)}"),
)
public inline fun buildSubscribeRequest(block: SubscribeRequestBuilder.() -> Unit): SubscribeRequest {
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
 * val request = UnsubscribeRequest {
 *     uri = "file:///path/to/resource.txt"
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the unsubscribe request
 * @return A configured [UnsubscribeRequest] instance
 * @see UnsubscribeRequestBuilder
 * @see UnsubscribeRequest
 */
@ExperimentalMcpApi
public inline operator fun UnsubscribeRequest.Companion.invoke(
    block: UnsubscribeRequestBuilder.() -> Unit,
): UnsubscribeRequest = UnsubscribeRequestBuilder().apply(block).build()

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
@Deprecated(
    message = "Use UnsubscribeRequest { } instead",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("UnsubscribeRequest{apply(block)}"),
)
public inline fun buildUnsubscribeRequest(block: UnsubscribeRequestBuilder.() -> Unit): UnsubscribeRequest {
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
