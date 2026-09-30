package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

/**
 * In-memory server stand-in: answers `initialize` with [serverCapabilities] and `tools/call` with an
 * empty result, and records every request the client sends.
 */
class MockTransport(
    private val serverCapabilities: ServerCapabilities = ServerCapabilities(tools = ServerCapabilities.Tools()),
) : Transport {
    private val requests = mutableListOf<JSONRPCRequest>()
    private var onMessageBlock: (suspend (JSONRPCMessage) -> Unit)? = null
    private var onCloseBlock: (() -> Unit)? = null

    fun lastRequest(): JSONRPCRequest = requests.last()

    override suspend fun start() = Unit

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        if (message !is JSONRPCRequest) return
        requests += message
        val result = when (message.method) {
            "initialize" -> InitializeResult(
                protocolVersion = LATEST_PROTOCOL_VERSION,
                capabilities = serverCapabilities,
                serverInfo = Implementation("mock-server", "1.0.0"),
            )

            "tools/call" -> CallToolResult(content = emptyList())

            else -> return
        }
        onMessageBlock?.invoke(JSONRPCResponse(id = message.id, result = result))
    }

    override suspend fun close() {
        onCloseBlock?.invoke()
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        onMessageBlock = block
    }

    override fun onClose(block: () -> Unit) {
        onCloseBlock = block
    }

    override fun onError(block: (Throwable) -> Unit) = Unit
}
