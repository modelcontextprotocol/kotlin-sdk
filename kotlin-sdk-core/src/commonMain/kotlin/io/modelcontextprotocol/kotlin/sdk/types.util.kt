package io.modelcontextprotocol.kotlin.sdk

import io.modelcontextprotocol.kotlin.sdk.types.error
import io.modelcontextprotocol.kotlin.sdk.types.success
import kotlinx.serialization.json.JsonObject

@Deprecated(
    message = "Use `io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject` instead",
    replaceWith = ReplaceWith("EmptyJsonObject", "io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject"),
    level = DeprecationLevel.WARNING,
)
public val EmptyJsonObject: JsonObject = io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject

/**
 * Creates a [CallToolResult] with single [TextContent] and [meta].
 */
@Deprecated(
    message = "Use `CallToolResult.success` instead",
    replaceWith = ReplaceWith("CallToolResult.Companion.success"),
    level = DeprecationLevel.WARNING,
)
public fun CallToolResult.ok(
    content: String,
    meta: JsonObject = EmptyJsonObject,
): io.modelcontextprotocol.kotlin.sdk.types.CallToolResult =
    io.modelcontextprotocol.kotlin.sdk.types.CallToolResult.success(content, meta)

/**
 * Creates a [CallToolResult] with single [TextContent] and [meta], with `isError` being true.
 */
@Deprecated(
    message = "Use `CallToolResult.error` instead",
    replaceWith = ReplaceWith("CallToolResult.Companion.error"),
    level = DeprecationLevel.WARNING,
)
public fun CallToolResult.error(
    content: String,
    meta: JsonObject = EmptyJsonObject,
): io.modelcontextprotocol.kotlin.sdk.types.CallToolResult =
    io.modelcontextprotocol.kotlin.sdk.types.CallToolResult.error(content, meta)
