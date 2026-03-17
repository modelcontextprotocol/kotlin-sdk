package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmOverloads

/**
 * Represents an error specific to the MCP protocol.
 *
 * @property code The MCP/JSON‑RPC error code.
 * @property data Optional additional error payload as a JSON element; `null` when not provided.
 * @param message The error message. Used verbatim as [Exception.message] — no error code prefix is prepended.
 *   Defaults to `"MCP error $code"` when not provided.
 * @param cause The original cause.
 */
public class McpException @JvmOverloads public constructor(
    public val code: Int,
    message: String = "MCP error $code",
    public val data: JsonElement? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
