package io.modelcontextprotocol.kotlin.sdk.shared

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage

/**
 * In-memory transport for creating clients and servers that talk to each other within the same process.
 */
class InMemoryTransport : AbstractTransport() {
    private var otherTransport: InMemoryTransport? = null
    private val messageQueue: MutableList<JSONRPCMessage> = mutableListOf()

    /**
     * Creates a pair of linked in-memory transports that can communicate with each other.
     * One should be passed to a Client and one to a Server.
     */
    companion object {
        fun createLinkedPair(): Pair<InMemoryTransport, InMemoryTransport> {
            val clientTransport = InMemoryTransport()
            val serverTransport = InMemoryTransport()
            clientTransport.otherTransport = serverTransport
            serverTransport.otherTransport = clientTransport
            return Pair(clientTransport, serverTransport)
        }
    }

    override suspend fun start() {
        // Process any messages that were queued before start was called
        while (messageQueue.isNotEmpty()) {
            messageQueue.removeFirstOrNull()?.let { message ->
                _onMessage.invoke(message) // todo?
            }
        }
    }

    override suspend fun close() {
        val other = otherTransport
        otherTransport = null
        other?.close()
        _onClose.invoke()
    }

    override suspend fun send(message: JSONRPCMessage) {
        val other = otherTransport ?: throw IllegalStateException("Not connected")

        other._onMessage.invoke(message)
    }
}