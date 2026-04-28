package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError

/**
 * Validates SEP-1577 client-side enforcement: an incoming `sampling/createMessage`
 * request that carries `tools` or `toolChoice` requires the client to have advertised
 * the `sampling.tools` sub-capability. When the capability is missing, throws an
 * [McpException] with JSON-RPC error code `InvalidParams`, matching TypeScript SDK
 * client enforcement.
 */
internal fun validateSamplingToolsCapability(request: CreateMessageRequest, capabilities: ClientCapabilities) {
    val params = request.params
    if (params.tools == null && params.toolChoice == null) return
    if (capabilities.sampling?.tools != null) return
    val field = if (params.tools != null) "tools" else "toolChoice"
    throw McpException(
        code = RPCError.ErrorCode.INVALID_PARAMS,
        message = "Client does not support sampling with tools but request contains $field parameter",
    )
}
