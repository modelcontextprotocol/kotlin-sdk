package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Creates a [PingRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [meta][PingRequestBuilder.meta] - Metadata for the request
 *
 * Example with no parameters:
 * ```kotlin
 * val request = buildPingRequest { }
 * ```
 *
 * Example with metadata:
 * ```kotlin
 * val request = buildPingRequest {
 *     meta {
 *         put("timestamp", JsonPrimitive(System.currentTimeMillis()))
 *     }
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the ping request
 * @return A configured [PingRequest] instance
 * @see PingRequestBuilder
 * @see PingRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
internal inline fun buildPingRequest(block: PingRequestBuilder.() -> Unit): PingRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return PingRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [PingRequest] instances.
 *
 * This builder creates ping requests to check connection status with the server.
 * All fields are optional.
 *
 * ## Optional
 * - [meta] - Metadata for the request
 *
 * @see buildPingRequest
 * @see PingRequest
 */
@McpDsl
public class PingRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    @PublishedApi
    override fun build(): PingRequest {
        val params = meta?.let { BaseRequestParams(meta = it) }
        return PingRequest(params)
    }
}
