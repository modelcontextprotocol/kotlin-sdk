package io.modelcontextprotocol.kotlin.sdk.shared

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage

/**
 * Describes the minimal contract for a MCP transport that a client or server can communicate over.
 */
public interface Transport {
    /**
     * Starts processing messages on the transport, including any connection steps that might need to be taken.
     *
     * This method should only be called after callbacks are installed, or else messages may be lost.
     *
     * NOTE: This method should not be called explicitly when using Client, Server, or Protocol classes,
     * as they will implicitly call start().
     */
    public suspend fun start()

    /**
     * Sends a JSON-RPC message (request or response).
     */
    public suspend fun send(message: JSONRPCMessage)

    /**
     * Closes the connection.
     */
    public suspend fun close()

    /**
     * Callback for when the connection is closed for any reason.
     *
     * This should be invoked when close() is called as well.
     */
    public fun onClose(block: () -> Unit)

    /**
     * Callback for when an error occurs.
     *
     * Note that errors are not necessarily fatal; they are used for reporting any kind of
     * exceptional condition out of band.
     */
    public fun onError(block: (Throwable) -> Unit)

    /**
     * Callback for when a message (request or response) is received over the connection.
     */
    public fun onMessage(block: suspend (JSONRPCMessage) -> Unit)
}

/**
 * Implements [onClose], [onError] and [onMessage] functions of [Transport] providing
 * corresponding [_onClose], [_onError] and [_onMessage] properties to use for an implementation.
 */
@Suppress("PropertyName")
public abstract class AbstractTransport : Transport {
    protected var _onClose: (() -> Unit) = {}
    protected var _onError: ((Throwable) -> Unit) = {}
    protected var _onMessage: (suspend ((JSONRPCMessage) -> Unit)) = {}

    override fun onClose(block: () -> Unit) {
        val old = _onClose
        _onClose = {
            old()
            block()
        }
    }

    override fun onError(block: (Throwable) -> Unit) {
        val old = _onError
        _onError = { e ->
            old(e)
            block(e)
        }
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        val old = _onMessage
        _onMessage = { message ->
            old(message)
            block(message)
        }
    }
}
