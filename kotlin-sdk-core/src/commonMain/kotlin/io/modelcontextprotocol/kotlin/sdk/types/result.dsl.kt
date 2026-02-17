package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject

/**
 * Base DSL builder for constructing MCP result instances.
 *
 * This abstract class provides common functionality for all result builders,
 * including optional metadata support.
 *
 * All concrete result builder classes extend this base to inherit [meta] functionality.
 *
 * @see RequestResult
 * @see ServerResult
 */
@McpDsl
public abstract class ResultBuilder {
    protected var meta: JsonObject? = null

    /**
     * Sets result metadata directly from a JsonObject.
     *
     * **Note:** Prefer using the DSL lambda variant [meta] for more idiomatic Kotlin code.
     * This overload is provided for cases where you already have a constructed JsonObject.
     *
     * Example:
     * ```kotlin
     * val existingMeta = buildJsonObject {
     *     put("source", "server")
     * }
     * meta(existingMeta)
     * ```
     *
     * @see meta
     */
    public fun meta(meta: JsonObject) {
        this.meta = meta
    }

    /**
     * Sets result metadata using a DSL builder.
     *
     * **This is the preferred way to set metadata.** The DSL syntax is more idiomatic
     * and integrates better with Kotlin's type-safe builders.
     *
     * Metadata can include custom fields for tracking responses and providing
     * additional context to clients.
     *
     * Example (preferred):
     * ```kotlin
     * listToolsResult {
     *     tools { /* ... */ }
     *     meta {
     *         put("serverVersion", "1.0.0")
     *         put("cached", true)
     *         put("generatedAt", System.currentTimeMillis())
     *     }
     * }
     * ```
     *
     * @param builderAction Lambda for building result metadata
     */
    public fun meta(builderAction: JsonObjectBuilder.() -> Unit) {
        meta = buildJsonObject(builderAction)
    }

    internal abstract fun build(): RequestResult
}

/**
 * Base DSL builder for constructing paginated result instances.
 *
 * Extends [ResultBuilder] to add pagination support via [nextCursor].
 *
 * @see ResultBuilder
 * @see PaginatedResult
 */
@McpDsl
public abstract class PaginatedResultBuilder : ResultBuilder() {
    /**
     * Optional pagination cursor for fetching the next page of results.
     *
     * If present, indicates there may be more results available.
     * Clients can pass this cursor in a subsequent paginated request.
     *
     * **Design note:** This field is nullable to distinguish between "no next page" (`null`)
     * and "next page exists" (non-null string). When `null`, the field is omitted from the
     * serialized JSON, keeping the protocol efficient.
     *
     * Example: `nextCursor = "eyJwYWdlIjogMn0="`
     */
    public var nextCursor: String? = null
}
