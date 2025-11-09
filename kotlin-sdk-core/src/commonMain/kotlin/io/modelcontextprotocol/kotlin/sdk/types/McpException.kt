package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.json.JsonElement

/**
 * Represents an error specific to the MCP protocol.
 *
 * @property code The error code.
 * @property message The error message.
 * @property data Additional error data as a JSON object.
 */
public class McpException(public val code: Int, message: String, public val data: JsonElement? = null) : Exception() {
    override val message: String = "MCP error $code: $message"
}
