package io.modelcontextprotocol.kotlin.sdk.shared

import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * In-memory transport for creating clients and servers that talk to each other within the same process.
 */
class InMemoryTransport(
    context: CoroutineContext = EmptyCoroutineContext,
    handlerContext: CoroutineContext = Dispatchers.Default,
) : AbstractTransport(context, handlerContext) {
    private var otherTransport: InMemoryTransport? = null
    private val messageQueue: MutableList<JSONRPCMessage> = mutableListOf()

    /**
     * Creates a pair of linked in-memory transports that can communicate with each other.
     * One should be passed to a Client and one to a Server.
     */
    companion object {
        fun createLinkedPair(
            context: CoroutineContext = EmptyCoroutineContext,
            handlerContext: CoroutineContext = Dispatchers.Default,
        ): Pair<InMemoryTransport, InMemoryTransport> {
            val clientTransport = InMemoryTransport(context, handlerContext)
            val serverTransport = InMemoryTransport(context, handlerContext)
            clientTransport.otherTransport = serverTransport
            serverTransport.otherTransport = clientTransport
            return Pair(clientTransport, serverTransport)
        }
    }

    override suspend fun start() {
        // Process any messages that were queued before start was called
        while (messageQueue.isNotEmpty()) {
            messageQueue.removeFirstOrNull()?.let { message ->
                handleMessageInline(message) // todo?
            }
        }
    }

    override suspend fun close() {
        val other = otherTransport
        otherTransport = null
        shutdownHandlers()
        other?.close()
        invokeOnCloseCallback()
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        val other = checkNotNull(otherTransport) { "Not connected" }

        // necessary to propagate the caller's context - sometimes test, sometimes not
        other.handleMessageInline(message)
    }
}
