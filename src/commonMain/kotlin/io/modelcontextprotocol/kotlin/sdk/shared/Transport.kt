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
    public var onClose: (() -> Unit)?

    /**
     * Callback for when an error occurs.
     *
     * Note that errors are not necessarily fatal; they are used for reporting any kind of
     * exceptional condition out of band.
     */
    public var onError: ((Throwable) -> Unit)?

    /**
     * Callback for when a message (request or response) is received over the connection.
     */
    public var onMessage: (suspend ((JSONRPCMessage) -> Unit))?
}
