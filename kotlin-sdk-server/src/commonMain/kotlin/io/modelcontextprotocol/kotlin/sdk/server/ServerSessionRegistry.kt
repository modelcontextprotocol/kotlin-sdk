package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentMapOf

internal typealias ServerSessionKey = String

/**
 * Represents a registry for managing server sessions.
 */
internal class ServerSessionRegistry {

    private val logger = KotlinLogging.logger {}

    /**
     * Atomic variable used to maintain a thread-safe registry of sessions.
     * Stores a persistent map where each session is identified by its unique key.
     */
    private val registry = atomic(persistentMapOf<String, ServerSession>())

    /**
     * Returns a read-only view of the current server sessions.
     */
    internal val sessions: Map<ServerSessionKey, ServerSession>
        get() = registry.value

    /**
     * Returns a server session by its ID.
     * @param sessionId The ID of the session to retrieve.
     * @throws IllegalArgumentException If the session doesn't exist.
     */
    internal fun getSession(sessionId: ServerSessionKey): ServerSession =
        sessions[sessionId] ?: throw IllegalArgumentException("Session not found: $sessionId")

    /**
     * Returns a server session by its ID, or null if it doesn't exist.
     * @param sessionId The ID of the session to retrieve.
     */
    internal fun getSessionOrNull(sessionId: ServerSessionKey): ServerSession? = sessions[sessionId]

    /**
     * Registers a server session.
     * @param session The session to register.
     */
    internal fun addSession(session: ServerSession) {
        logger.info { "Adding session: ${session.sessionId}" }
        registry.update { sessions -> sessions.put(session.sessionId, session) }
    }

    /**
     * Removes a server session by its ID.
     * @param sessionId The ID of the session to remove.
     */
    internal fun removeSession(sessionId: ServerSessionKey) {
        logger.info { "Removing session: $sessionId" }
        registry.update { sessions -> sessions.remove(sessionId) }
    }
}
