package org.kotlinlang.mcp.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class TtlCache<K : Any, V : Any>(
    private val ttl: Duration,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private class Entry<V>(val value: V, val createdAt: TimeMark)

    private val map = ConcurrentHashMap<K, Entry<V>>()
    private val inFlight = ConcurrentHashMap<K, CompletableDeferred<V>>()

    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (entry.createdAt.elapsedNow() >= ttl) {
            map.remove(key, entry)
            return null
        }
        return entry.value
    }

    fun put(key: K, value: V) {
        map[key] = Entry(value, timeSource.markNow())
        evictExpired()
    }

    private fun evictExpired() {
        map.entries.removeIf { it.value.createdAt.elapsedNow() >= ttl }
    }

    suspend fun getOrPut(key: K, loader: suspend () -> V): V {
        while (true) {
            get(key)?.let { return it }

            val deferred = CompletableDeferred<V>()
            val existing = inFlight.putIfAbsent(key, deferred)
            if (existing != null) {
                try {
                    return existing.await()
                } catch (_: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    // The loader's scope was cancelled, not ours — retry
                    continue
                }
            }

            try {
                val value = loader()
                put(key, value)
                deferred.complete(value)
                return value
            } catch (e: CancellationException) {
                deferred.cancel()
                throw e
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
                throw e
            } finally {
                inFlight.remove(key, deferred)
            }
        }
    }
}
