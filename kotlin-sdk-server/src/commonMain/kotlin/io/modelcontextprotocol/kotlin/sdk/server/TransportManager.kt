package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap

/**
 * Manages active transports keyed by session ID.
 *
 * Each invocation of [mcpStreamableHttp] or [mcpStatelessStreamableHttp] creates its own
 * [TransportManager] with an independent session namespace. Registering the same endpoint
 * function twice on the same route tree results in two disjoint session spaces — sessions
 * established through one registration are invisible to the other.
 */
internal class TransportManager<T : AbstractTransport>(transports: Map<String, T> = emptyMap()) {
    private val transports: AtomicRef<PersistentMap<String, T>> = atomic(transports.toPersistentMap())

    fun getTransport(sessionId: String): T? = transports.value[sessionId]

    fun addTransport(sessionId: String, transport: T) {
        transports.update { it.put(sessionId, transport) }
    }

    fun removeTransport(sessionId: String) {
        transports.update { it.remove(sessionId) }
    }
}
