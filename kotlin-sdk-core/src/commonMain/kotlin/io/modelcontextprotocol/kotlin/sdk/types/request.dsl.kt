package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Base DSL builder for constructing MCP request instances.
 *
 * This abstract class provides common functionality for all request builders,
 * including optional metadata support.
 *
 * All concrete request builder classes extend this base to inherit [meta] functionality.
 *
 * @see RequestMetaBuilder
 * @see PaginatedRequestBuilder
 */
@McpDsl
public abstract class RequestBuilder {
    protected var meta: RequestMeta? = null

    /**
     * Sets request metadata using a DSL builder.
     *
     * Metadata can include progress tokens and custom fields for tracking requests.
     *
     * Example:
     * ```kotlin
     * initializeRequest {
     *     protocolVersion = "2024-11-05"
     *     meta {
     *         progressToken("progress-123")
     *         put("customField", "value")
     *     }
     *     // ... other configuration
     * }
     * ```
     *
     * @param builderAction Lambda for building request metadata
     * @see RequestMetaBuilder
     */
    public fun meta(builderAction: RequestMetaBuilder.() -> Unit) {
        meta = RequestMetaBuilder().apply(builderAction).build()
    }

    internal abstract fun build(): Request
}

/**
 * DSL builder for constructing [RequestMeta] instances.
 *
 * This builder creates metadata for MCP requests, supporting progress tokens
 * and custom key-value pairs.
 *
 * Example:
 * ```kotlin
 * meta {
 *     progressToken("progress-123")
 *     put("requestId", "req-456")
 *     put("priority", 1)
 *     putJsonObject("context") {
 *         put("source", "api")
 *     }
 * }
 * ```
 *
 * @see RequestMeta
 * @see RequestBuilder.meta
 */
@McpDsl
public class RequestMetaBuilder internal constructor() {
    private val content: MutableMap<String, JsonElement> = linkedMapOf()

    /**
     * Sets the progress token as a string.
     *
     * Progress tokens are used for out-of-band progress notifications.
     *
     * Example: `progressToken("progress-123")`
     */
    public fun progressToken(value: String) {
        content["progressToken"] = JsonPrimitive(value)
    }

    /**
     * Sets the progress token as a `Long`.
     *
     * Example: `progressToken(123L)`
     */
    public fun progressToken(value: Long) {
        content["progressToken"] = JsonPrimitive(value)
    }

    /**
     * Sets the progress token as an Int.
     *
     * Example: `progressToken(123)`
     */
    public fun progressToken(value: Int) {
        content["progressToken"] = JsonPrimitive(value)
    }

    /**
     * Adds a custom metadata field with a JsonElement value.
     *
     * @param key The metadata field name
     * @param value The JsonElement value
     * @return The previous value associated with the key, or null
     */
    public fun put(key: String, value: JsonElement): JsonElement? = content.put(key, value)

    /**
     * Adds a custom metadata field with a String value.
     *
     * Example: `put("requestId", "req-456")`
     */
    public fun put(key: String, value: String): JsonElement? = put(key, JsonPrimitive(value))

    /**
     * Adds a custom metadata field with a Number value.
     *
     * Example: `put("priority", 1)`
     */
    public fun put(key: String, value: Number): JsonElement? = put(key, JsonPrimitive(value))

    /**
     * Adds a custom metadata field with a Boolean value.
     *
     * Example: `put("urgent", true)`
     */
    public fun put(key: String, value: Boolean): JsonElement? = put(key, JsonPrimitive(value))

    /**
     * Adds a custom metadata field with a null value.
     *
     * Example: `put("optionalField", null)`
     */
    @Suppress("UNUSED_PARAMETER")
    public fun put(key: String, value: Nothing?): JsonElement? = put(key, JsonNull)

    /**
     * Adds a custom metadata field with a JsonObject value using a DSL builder.
     *
     * Example:
     * ```kotlin
     * putJsonObject("context") {
     *     put("source", "api")
     *     put("version", 2)
     * }
     * ```
     *
     * @param key The metadata field name
     * @param builderAction Lambda for building the JsonObject
     */
    public fun putJsonObject(key: String, builderAction: JsonObjectBuilder.() -> Unit): JsonElement? =
        put(key, buildJsonObject(builderAction))

    /**
     * Adds a custom metadata field with a JsonArray value using a DSL builder.
     *
     * Example:
     * ```kotlin
     * putJsonArray("tags") {
     *     add("important")
     *     add("urgent")
     * }
     * ```
     *
     * @param key The metadata field name
     * @param builderAction Lambda for building the JsonArray
     */
    public fun putJsonArray(key: String, builderAction: JsonArrayBuilder.() -> Unit): JsonElement? =
        put(key, buildJsonArray(builderAction))

    internal fun build(): RequestMeta = RequestMeta(JsonObject(content))
}

/**
 * Base DSL builder for constructing paginated MCP request instances.
 *
 * This abstract class extends [RequestBuilder] and adds pagination support
 * through an optional cursor field.
 *
 * Example:
 * ```kotlin
 * listPromptsRequest {
 *     cursor = "eyJwYWdlIjogMn0="
 * }
 * ```
 *
 * @see RequestBuilder
 */
@McpDsl
public abstract class PaginatedRequestBuilder : RequestBuilder() {
    /**
     * Optional pagination cursor for fetching the next page of results.
     *
     * The cursor value is typically obtained from the previous page's response.
     *
     * Example: `cursor = "eyJwYWdlIjogMn0="`
     */
    public var cursor: String? = null
}
