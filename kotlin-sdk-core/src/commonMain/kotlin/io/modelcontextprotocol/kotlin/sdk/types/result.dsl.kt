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
     * Example:
     * ```kotlin
     * meta(buildJsonObject {
     *     put("source", "server")
     *     put("timestamp", System.currentTimeMillis())
     * })
     * ```
     */
    public fun meta(meta: JsonObject) {
        this.meta = meta
    }

    /**
     * Sets result metadata using a DSL builder.
     *
     * Metadata can include custom fields for tracking responses and providing
     * additional context to clients.
     *
     * Example:
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
     * Example: `nextCursor = "eyJwYWdlIjogMn0="`
     */
    public var nextCursor: String? = null
}
