@file:Suppress("unused", "EnumEntryName")

package io.modelcontextprotocol.kotlin.sdk

import kotlinx.serialization.json.JsonObject

@Deprecated("Use McpException instead", ReplaceWith("McpException"))
public typealias McpError = McpException

/**
 * Represents an error specific to the MCP protocol.
 *
 * @property code The error code.
 * @property message The error message.
 * @property data Additional error data as a JSON object.
 */
public class McpException(public val code: Int, message: String, public val data: JsonObject = EmptyJsonObject) :
    Exception("MCP error $code: \"$message\"")

/**
 * Converts a `JSONRPCError` instance to an [McpException] instance.
 *
 * @return An [McpException] containing the code, message, and data from the `JSONRPCError`.
 */
internal fun JSONRPCError.toMcpException(): McpException = McpException(
    code = this.code.code,
    message = this.message,
    data = this.data,
)
