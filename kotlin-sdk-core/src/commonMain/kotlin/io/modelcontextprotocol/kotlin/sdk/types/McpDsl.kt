package io.modelcontextprotocol.kotlin.sdk.types

/**
 * DSL marker annotation for MCP builder classes.
 *
 * This annotation is used to prevent accidental access to outer DSL scopes
 * within nested DSL blocks, ensuring type-safe and unambiguous builder usage.
 *
 * @see DslMarker
 */
@DslMarker
public annotation class McpDsl
