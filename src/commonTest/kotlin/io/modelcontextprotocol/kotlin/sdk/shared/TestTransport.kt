package io.modelcontextprotocol.kotlin.sdk.shared

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.Transport

/**
 * A simple fake Transport for testing, capturing the onMessage callback
 * so we can trigger messages manually.
 */
internal class TestTransport : Transport {
    private var onCloseCallback: (() -> Unit)? = null
    private var onErrorCallback: ((Throwable) -> Unit)? = null
    private var onMessageCallback: (suspend (JSONRPCMessage) -> Unit)? = null

    override fun onClose(callback: () -> Unit) {
        onCloseCallback = callback
    }

    override fun onError(callback: (Throwable) -> Unit) {
        onErrorCallback = callback
    }

    override fun onMessage(callback: suspend (JSONRPCMessage) -> Unit) {
        onMessageCallback = callback
    }

    override suspend fun start() {
        // no-op for test
    }

    override suspend fun close() {
        onCloseCallback?.invoke()
    }

    override suspend fun send(message: JSONRPCMessage) {
        // we don’t need to do anything with outbound messages in these tests,
        // unless you want to record them for verification
    }

    suspend fun simulateMessage(message: JSONRPCMessage) {
        onMessageCallback?.invoke(message)
    }
}
