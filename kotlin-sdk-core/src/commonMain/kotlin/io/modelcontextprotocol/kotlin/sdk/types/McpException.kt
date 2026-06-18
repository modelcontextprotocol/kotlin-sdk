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
public open class McpException @JvmOverloads public constructor(
    public val code: Int,
    message: String = "MCP error $code",
    public val data: JsonElement? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    internal companion object {
        /**
         * Reconstructs the most specific [McpException] subtype for a JSON-RPC error.
         *
         * Recognizes [RPCError.ErrorCode.URL_ELICITATION_REQUIRED] errors and returns a
         * [UrlElicitationRequiredException] when [data] carries valid URL-mode elicitations;
         * otherwise returns a plain [McpException]. Never throws — a malformed payload simply
         * degrades to a plain [McpException].
         */
        fun fromError(code: Int, message: String, data: JsonElement?): McpException {
            if (code == RPCError.ErrorCode.URL_ELICITATION_REQUIRED && data != null) {
                UrlElicitationRequiredException.fromDataOrNull(message, data)?.let { return it }
            }
            return McpException(code = code, message = message, data = data)
        }
    }
}
