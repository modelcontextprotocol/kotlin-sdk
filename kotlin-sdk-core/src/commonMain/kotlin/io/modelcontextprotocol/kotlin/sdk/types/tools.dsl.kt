package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Creates a [CallToolRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [name][CallToolRequestBuilder.name] - The name of the tool to call
 *
 * ## Optional
 * - [arguments][CallToolRequestBuilder.arguments] - Arguments to pass to the tool
 * - [meta][CallToolRequestBuilder.meta] - Metadata for the request
 *
 * Example without arguments:
 * ```kotlin
 * val request = buildCallToolRequest {
 *     name = "getCurrentTime"
 * }
 * ```
 *
 * Example with arguments:
 * ```kotlin
 * val request = buildCallToolRequest {
 *     name = "searchDatabase"
 *     arguments {
 *         put("query", "users")
 *         put("limit", 10)
 *     }
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the call tool request
 * @return A configured [CallToolRequest] instance
 * @see CallToolRequestBuilder
 * @see CallToolRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
public inline fun buildCallToolRequest(block: CallToolRequestBuilder.() -> Unit): CallToolRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return CallToolRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [CallToolRequest] instances.
 *
 * This builder invokes a specific tool by name with optional arguments.
 *
 * ## Required
 * - [name] - The name of the tool to call
 *
 * ## Optional
 * - [arguments] - Arguments to pass to the tool
 * - [meta] - Metadata for the request
 *
 * @see buildCallToolRequest
 * @see CallToolRequest
 */
@McpDsl
public class CallToolRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    /**
     * The name of the tool to call. This is a required field.
     *
     * Example: `name = "getCurrentTime"`
     */
    public var name: String? = null

    private var arguments: JsonObject? = null

    /**
     * Sets tool arguments directly from a JsonObject.
     *
     * Example:
     * ```kotlin
     * arguments(buildJsonObject {
     *     put("query", "users")
     * })
     * ```
     */
    public fun arguments(arguments: JsonObject) {
        this.arguments = arguments
    }

    /**
     * Sets tool arguments using a DSL builder.
     *
     * This is the recommended way to provide tool arguments.
     *
     * Example:
     * ```kotlin
     * arguments {
     *     put("query", "SELECT * FROM users")
     *     put("limit", 100)
     *     put("includeDeleted", false)
     * }
     * ```
     *
     * @param block Lambda for building the arguments JsonObject
     */
    public fun arguments(block: JsonObjectBuilder.() -> Unit): Unit = arguments(buildJsonObject(block))

    @PublishedApi
    override fun build(): CallToolRequest {
        val name = requireNotNull(name) {
            "Missing required field 'name'. Example: name = \"toolName\""
        }

        val params = CallToolRequestParams(name = name, arguments = arguments, meta = meta)
        return CallToolRequest(params)
    }
}

/**
 * Creates a [ListToolsRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [cursor][ListToolsRequestBuilder.cursor] - Pagination cursor for fetching next page
 * - [meta][ListToolsRequestBuilder.meta] - Metadata for the request
 *
 * Example without pagination:
 * ```kotlin
 * val request = buildListToolsRequest { }
 * ```
 *
 * Example with pagination:
 * ```kotlin
 * val request = buildListToolsRequest {
 *     cursor = "eyJwYWdlIjogMn0="
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list tools request
 * @return A configured [ListToolsRequest] instance
 * @see ListToolsRequestBuilder
 * @see ListToolsRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
public inline fun buildListToolsRequest(block: ListToolsRequestBuilder.() -> Unit): ListToolsRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ListToolsRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [ListToolsRequest] instances.
 *
 * This builder retrieves a list of available tools, with optional pagination support.
 * All fields are optional.
 *
 * ## Optional
 * - [cursor] - Pagination cursor (inherited from [PaginatedRequestBuilder])
 * - [meta] - Metadata for the request (inherited from [RequestBuilder])
 *
 * @see buildListToolsRequest
 * @see ListToolsRequest
 * @see PaginatedRequestBuilder
 */
@McpDsl
public class ListToolsRequestBuilder @PublishedApi internal constructor() : PaginatedRequestBuilder() {
    @PublishedApi
    override fun build(): ListToolsRequest {
        val params = paginatedRequestParams(cursor = cursor, meta = meta)
        return ListToolsRequest(params)
    }
}
