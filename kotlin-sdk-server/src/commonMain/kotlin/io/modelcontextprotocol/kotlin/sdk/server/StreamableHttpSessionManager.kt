package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}

/** How long a stateful Streamable HTTP session may stay idle before [mcpStreamableHttp] closes it. */
internal val DEFAULT_SESSION_IDLE_TIMEOUT: Duration = 30.minutes

/** How many stateful Streamable HTTP sessions [mcpStreamableHttp] keeps open at once. */
internal const val DEFAULT_MAX_SESSIONS: Int = 10_000

private val MIN_EXPIRY_CHECK_INTERVAL: Duration = 10.milliseconds
private val MAX_EXPIRY_CHECK_INTERVAL: Duration = 5.seconds

/**
 * Tracks the sessions of a stateful [mcpStreamableHttp] endpoint and bounds how many it keeps and for how long.
 *
 * A session is idle while none of the requests run through [withSession] is in flight. Once it has been idle
 * for [idleTimeout], [closeExpiredSessions] forgets it, so its id answers `404`, and closes its transport, which
 * closes the server session and any GET stream on it too. At most [maxSessions] sessions are open at once,
 * counting the ones still initializing.
 *
 * @param T the transport a session is served by, a [StreamableHttpServerTransport] outside tests
 */
internal class StreamableHttpSessionManager<T : Transport>(
    private val idleTimeout: Duration = DEFAULT_SESSION_IDLE_TIMEOUT,
    val maxSessions: Int = DEFAULT_MAX_SESSIONS,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    init {
        require(idleTimeout.isPositive()) { "sessionIdleTimeout must be positive, but was $idleTimeout" }
        require(maxSessions > 0) { "maxSessions must be positive, but was $maxSessions" }
    }

    private val sessions: AtomicRef<PersistentMap<String, Session>> = atomic(persistentMapOf())

    /** Registered sessions plus reservations for sessions that are still initializing. */
    private val openCount = atomic(0)

    /** Whether idle sessions expire at all; `false` when [idleTimeout] is infinite. */
    val expires: Boolean get() = idleTimeout.isFinite()

    /** How often [closeExpiredSessionsPeriodically] looks for expired sessions. */
    val expiryCheckInterval: Duration = idleTimeout.coerceIn(MIN_EXPIRY_CHECK_INTERVAL, MAX_EXPIRY_CHECK_INTERVAL)

    operator fun contains(sessionId: String): Boolean = sessionId in sessions.value

    /** Claims room for a new session, or returns `false` when [maxSessions] sessions are already open. */
    fun tryReserve(): Boolean {
        openCount.loop { count ->
            if (count >= maxSessions) return false
            if (openCount.compareAndSet(count, count + 1)) return true
        }
    }

    /** Gives back a reservation whose session never initialized. */
    fun cancelReservation() {
        openCount.decrementAndGet()
    }

    /**
     * Turns a reservation into a session reachable as [sessionId]. The session starts with its initializing
     * request in flight, which the caller must [release].
     */
    fun register(sessionId: String, transport: T) {
        val session = Session(sessionId, transport)
        sessions.update { it.putting(sessionId, session) }
    }

    /** Ends a request on the session that [transport] serves as [sessionId]. Does nothing once it is gone. */
    fun release(sessionId: String, transport: T) {
        find(sessionId, transport)?.release()
    }

    /**
     * Runs [block] with the transport of [sessionId] and keeps the session from expiring until it returns.
     * Returns `false` without running [block] when no such session is open.
     */
    suspend fun withSession(sessionId: String, block: suspend (T) -> Unit): Boolean {
        val session = sessions.value[sessionId]
        if (session == null || !session.tryAcquire()) return false
        try {
            block(session.transport)
        } finally {
            session.release()
        }
        return true
    }

    /**
     * Returns the transport of [sessionId] and restarts its idle timer, or `null` when no such session is open.
     *
     * Unlike [withSession], this does not keep the session from expiring afterwards. It serves GET streams:
     * the engine may never notice that the client of a stream went away, so a stream must not keep its
     * session open. Expiry closes the stream along with the session.
     */
    fun touch(sessionId: String): T? {
        val session = sessions.value[sessionId] ?: return null
        return if (session.touch()) session.transport else null
    }

    /** Forgets the session that [transport] serves as [sessionId]. Does nothing once it is gone. */
    fun remove(sessionId: String, transport: T) {
        find(sessionId, transport)?.let { forget(it) }
    }

    /** Closes every session that has been idle for [idleTimeout], each on its own so that none waits on another. */
    suspend fun closeExpiredSessions(): Unit = coroutineScope {
        forgetExpiredSessions().forEach { session -> launch { close(session) } }
    }

    /**
     * Closes expired sessions every [expiryCheckInterval] until cancelled. Each sweep runs on its own, so a close
     * that hangs, for instance on a client that stopped reading its stream, cannot hold up the next ones.
     */
    suspend fun closeExpiredSessionsPeriodically(): Unit = coroutineScope {
        while (true) {
            delay(expiryCheckInterval)
            launch {
                // A failed sweep must not end expiry for good; the next one tries again.
                try {
                    closeExpiredSessions()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Failed to expire idle StreamableHttp sessions" }
                }
            }
        }
    }

    private fun find(sessionId: String, transport: T): Session? =
        sessions.value[sessionId]?.takeIf { it.transport === transport }

    /** Forgets every session that has been idle for [idleTimeout], so its id answers `404`, and returns them. */
    private fun forgetExpiredSessions(): List<Session> = buildList {
        for (session in sessions.value.values) {
            if (session.tryExpire() && forget(session)) {
                logger.info { "Closing StreamableHttp session ${session.id} after $idleTimeout without activity" }
                add(session)
            }
        }
    }

    /** Stops routing requests to [session] and frees its slot. Returns `false` if it was already forgotten. */
    private fun forget(session: Session): Boolean {
        session.markClosed()
        sessions.loop { current ->
            if (current[session.id] !== session) return false
            if (sessions.compareAndSet(current, current.removing(session.id))) {
                openCount.decrementAndGet()
                return true
            }
        }
    }

    private suspend fun close(session: Session) {
        try {
            session.transport.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Failed to close expired StreamableHttp session ${session.id}" }
        }
    }

    /** A registered session: its transport plus the requests in flight for it. */
    private inner class Session(val id: String, val transport: T) {
        // A single immutable snapshot so that expiry and a starting request race on one compare-and-set.
        private val activity = atomic(Activity(inFlight = 1, lastActive = timeSource.markNow(), closed = false))

        fun tryAcquire(): Boolean {
            activity.loop { current ->
                if (current.closed) return false
                if (activity.compareAndSet(current, current.copy(inFlight = current.inFlight + 1))) return true
            }
        }

        fun release() {
            val now = timeSource.markNow()
            activity.update { it.copy(inFlight = it.inFlight - 1, lastActive = now) }
        }

        fun touch(): Boolean {
            val now = timeSource.markNow()
            activity.loop { current ->
                if (current.closed) return false
                if (activity.compareAndSet(current, current.copy(lastActive = now))) return true
            }
        }

        /** Marks the session closed if it has been idle for [idleTimeout]. */
        fun tryExpire(): Boolean {
            activity.loop { current ->
                if (current.closed || current.inFlight > 0 || current.lastActive.elapsedNow() < idleTimeout) {
                    return false
                }
                if (activity.compareAndSet(current, current.copy(closed = true))) return true
            }
        }

        fun markClosed() {
            activity.update { it.copy(closed = true) }
        }
    }

    private data class Activity(val inFlight: Int, val lastActive: TimeMark, val closed: Boolean)
}
