package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MockTransport : Transport {
    private val _sentMessages = mutableListOf<JSONRPCMessage>()
    private val _receivedMessages = mutableListOf<JSONRPCMessage>()
    private val mutex = Mutex()

    suspend fun getSentMessages() = mutex.withLock { _sentMessages.toList() }
    suspend fun getReceivedMessages() = mutex.withLock { _receivedMessages.toList() }

    private var onMessageBlock: (suspend (JSONRPCMessage) -> Unit)? = null
    private var onCloseBlock: (() -> Unit)? = null
    private var onErrorBlock: ((Throwable) -> Unit)? = null

    override suspend fun start() = Unit

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        mutex.withLock {
            _sentMessages += message
        }

        // Auto-respond to initialization and tool calls
        when (message) {
            is JSONRPCRequest -> {
                when (message.method) {
                    "initialize" -> {
                        val initResponse = JSONRPCResponse(
                            id = message.id,
                            result = InitializeResult(
                                protocolVersion = "2024-11-05",
                                capabilities = ServerCapabilities(
                                    tools = ServerCapabilities.Tools(listChanged = null),
                                ),
                                serverInfo = Implementation("mock-server", "1.0.0"),
                            ),
                        )
                        onMessageBlock?.invoke(initResponse)
                    }

                    "tools/call" -> {
                        val toolResponse = JSONRPCResponse(
                            id = message.id,
                            result = CallToolResult(
                                content = listOf(),
                                isError = false,
                            ),
                        )
                        onMessageBlock?.invoke(toolResponse)
                    }
                }
            }

            else -> {
                // Handle other message types if needed
            }
        }
    }

    override suspend fun close() {
        onCloseBlock?.invoke()
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        onMessageBlock = { message ->
            mutex.withLock {
                _receivedMessages += message
            }
            block(message)
        }
    }

    override fun onClose(block: () -> Unit) {
        onCloseBlock = block
    }

    override fun onError(block: (Throwable) -> Unit) {
        onErrorBlock = block
    }

    fun setupInitializationResponse() {
        // This method helps set up the mock for proper initialization
    }
}
