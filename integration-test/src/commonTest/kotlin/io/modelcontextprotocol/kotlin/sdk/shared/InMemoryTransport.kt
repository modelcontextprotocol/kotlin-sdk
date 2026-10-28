package io.modelcontextprotocol.kotlin.sdk.shared

import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage

/**
 * In-memory transport for creating clients and servers that talk to each other within the same process.
 * [send] delivers the message synchronously on the caller's coroutine.
 */
class InMemoryTransport : AbstractTransport() {
    private var otherTransport: InMemoryTransport? = null

    companion object {
        /**
         * Creates a pair of linked in-memory transports that can communicate with each other.
         * One should be passed to a Client and one to a Server.
         */
        fun createLinkedPair(): Pair<InMemoryTransport, InMemoryTransport> {
            val clientTransport = InMemoryTransport()
            val serverTransport = InMemoryTransport()
            clientTransport.otherTransport = serverTransport
            serverTransport.otherTransport = clientTransport
            return Pair(clientTransport, serverTransport)
        }
    }

    override suspend fun start() = Unit

    override suspend fun close() {
        val other = otherTransport
        otherTransport = null
        other?.close()
        invokeOnCloseCallback()
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        val other = checkNotNull(otherTransport) { "Not connected" }

        other._onMessage.invoke(message)
    }
}
