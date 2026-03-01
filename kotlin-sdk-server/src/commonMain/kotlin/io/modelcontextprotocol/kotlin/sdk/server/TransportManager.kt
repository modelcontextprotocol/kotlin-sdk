package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap

internal class TransportManager<T : AbstractTransport> {
    private val transports: AtomicRef<PersistentMap<String, T>> = atomic(emptyMap<String, T>().toPersistentMap())

    fun hasTransport(sessionId: String): Boolean = transports.value.containsKey(sessionId)

    fun getTransport(sessionId: String): T? = transports.value[sessionId]

    fun addTransport(sessionId: String, transport: T) {
        transports.update { it.put(sessionId, transport) }
    }

    fun removeTransport(sessionId: String) {
        transports.update { it.remove(sessionId) }
    }
}
