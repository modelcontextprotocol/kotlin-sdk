import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.Transport

/**
 * In-memory transport for creating clients and servers that talk to each other within the same process.
 */
class InMemoryTransport : Transport {
    private var otherTransport: InMemoryTransport? = null
    private val messageQueue: MutableList<JSONRPCMessage> = mutableListOf()

    override var onClose: (() -> Unit)? = null
    override var onError: ((Throwable) -> Unit)? = null
    override var onMessage: (suspend ((JSONRPCMessage) -> Unit))? = null

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
                onMessage?.invoke(message) // todo?
            }
        }
    }

    override suspend fun close() {
        val other = otherTransport
        otherTransport = null
        other?.close()
        onClose?.invoke()
    }

    override suspend fun send(message: JSONRPCMessage) {
        val other = otherTransport ?: throw IllegalStateException("Not connected")

        if (other.onMessage != null) {
            other.onMessage?.invoke(message) // todo?
        } else {
            other.messageQueue.add(message)
        }
    }
}
