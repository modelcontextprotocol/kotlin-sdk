package io.modelcontextprotocol.kotlin.sdk.shared

import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.Method
import kotlinx.coroutines.channels.Channel

/** Shared [Protocol] test double: records [onError] calls, no-ops all capability assertions. */
internal class TestProtocol : Protocol(null) {
    val errors = mutableListOf<Throwable>()

    override fun onError(error: Throwable) {
        errors.add(error)
    }

    override fun assertCapabilityForMethod(method: Method) {
        // noop
    }
    override fun assertNotificationCapability(method: Method) {
        // noop
    }
    override fun assertRequestHandlerCapability(method: Method) {
        // noop
    }
}

/** Shared [Transport] test double: records every sent message (with its options) and replays inbound ones. */
internal class RecordingTransport : Transport {
    private val sentMessages = Channel<JSONRPCMessage>(Channel.UNLIMITED)
    private var onMessageCallback: (suspend (JSONRPCMessage) -> Unit)? = null
    private var onCloseCallback: (() -> Unit)? = null

    val sentWithOptions = mutableListOf<Pair<JSONRPCMessage, TransportSendOptions?>>()

    var closeCallback: (() -> Unit)? = null
        private set

    override suspend fun start() {
        // noop
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        sentWithOptions.add(message to options)
        sentMessages.send(message)
    }

    override suspend fun close() {
        onCloseCallback?.invoke()
    }

    override fun onClose(block: () -> Unit) {
        closeCallback = block
        onCloseCallback = block
    }

    override fun onError(block: (Throwable) -> Unit) {
        // noop
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        onMessageCallback = block
    }

    suspend fun awaitRequest(): JSONRPCRequest {
        val message = sentMessages.receive()
        return message as? JSONRPCRequest
            ?: error("Expected JSONRPCRequest but received ${message::class.simpleName}")
    }

    suspend fun deliver(message: JSONRPCMessage) {
        val callback = onMessageCallback ?: error("onMessage callback not registered")
        callback(message)
    }
}
