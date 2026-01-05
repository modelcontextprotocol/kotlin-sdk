package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Creates a [GetPromptRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [name][GetPromptRequestBuilder.name] - The name of the prompt to retrieve
 *
 * ## Optional
 * - [arguments][GetPromptRequestBuilder.arguments] - Arguments to pass to the prompt
 * - [meta][GetPromptRequestBuilder.meta] - Metadata for the request
 *
 * Example without arguments:
 * ```kotlin
 * val request = buildGetPromptRequest {
 *     name = "greeting"
 * }
 * ```
 *
 * Example with arguments:
 * ```kotlin
 * val request = buildGetPromptRequest {
 *     name = "userProfile"
 *     arguments = mapOf(
 *         "userId" to "123",
 *         "includeDetails" to "true"
 *     )
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the get prompt request
 * @return A configured [GetPromptRequest] instance
 * @see GetPromptRequestBuilder
 * @see GetPromptRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
public inline fun buildGetPromptRequest(block: GetPromptRequestBuilder.() -> Unit): GetPromptRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return GetPromptRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [GetPromptRequest] instances.
 *
 * This builder retrieves a specific prompt by name, optionally with arguments.
 *
 * ## Required
 * - [name] - The name of the prompt to retrieve
 *
 * ## Optional
 * - [arguments] - Arguments to pass to the prompt
 * - [meta] - Metadata for the request
 *
 * @see buildGetPromptRequest
 * @see GetPromptRequest
 */
@McpDsl
public class GetPromptRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    /**
     * The name of the prompt to retrieve. This is a required field.
     *
     * Example: `name = "greeting"`
     */
    public var name: String? = null

    /**
     * Optional arguments to pass to the prompt.
     *
     * Example:
     * ```kotlin
     * arguments = mapOf("userId" to "123", "lang" to "en")
     * ```
     */
    public var arguments: Map<String, String>? = null

    @PublishedApi
    override fun build(): GetPromptRequest {
        val name = requireNotNull(name) {
            "Missing required field 'name'. Example: name = \"promptName\""
        }

        val params = GetPromptRequestParams(name = name, arguments = arguments, meta = meta)
        return GetPromptRequest(params)
    }
}

/**
 * Creates a [ListPromptsRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [cursor][ListPromptsRequestBuilder.cursor] - Pagination cursor for fetching next page
 * - [meta][ListPromptsRequestBuilder.meta] - Metadata for the request
 *
 * Example without pagination:
 * ```kotlin
 * val request = buildListPromptsRequest { }
 * ```
 *
 * Example with pagination:
 * ```kotlin
 * val request = buildListPromptsRequest {
 *     cursor = "eyJwYWdlIjogMn0="
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list prompts request
 * @return A configured [ListPromptsRequest] instance
 * @see ListPromptsRequestBuilder
 * @see ListPromptsRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
public inline fun buildListPromptsRequest(block: ListPromptsRequestBuilder.() -> Unit): ListPromptsRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ListPromptsRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [ListPromptsRequest] instances.
 *
 * This builder retrieves a list of available prompts, with optional pagination support.
 * All fields are optional.
 *
 * ## Optional
 * - [cursor] - Pagination cursor (inherited from [PaginatedRequestBuilder])
 * - [meta] - Metadata for the request (inherited from [RequestBuilder])
 *
 * @see buildListPromptsRequest
 * @see ListPromptsRequest
 * @see PaginatedRequestBuilder
 */
@McpDsl
public class ListPromptsRequestBuilder @PublishedApi internal constructor() : PaginatedRequestBuilder() {
    @PublishedApi
    override fun build(): ListPromptsRequest {
        val params = paginatedRequestParams(cursor = cursor, meta = meta)
        return ListPromptsRequest(params)
    }
}
