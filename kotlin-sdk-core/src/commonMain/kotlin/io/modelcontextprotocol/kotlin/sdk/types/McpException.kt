package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmOverloads

/**
 * Represents an error specific to the MCP protocol.
 *
 * @property code the MCP/JSON-RPC error code
 * @param message the error message; used verbatim as [Exception.message] without an error-code prefix.
 * Defaults to `"MCP error $code"` when not provided.
 * @property data optional additional error payload as a JSON element
 * @param cause the original cause
 */
public class McpException @JvmOverloads public constructor(
    public val code: Int,
    message: String = "MCP error $code",
    public val data: JsonElement? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
