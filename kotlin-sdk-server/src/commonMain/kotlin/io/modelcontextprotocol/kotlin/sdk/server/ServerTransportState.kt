package io.modelcontextprotocol.kotlin.sdk.server

/**
 * States of a server transport within the protocol lifecycle.
 *
 * These states model the lifecycle of a server-side transport connection,
 * from creation through active message exchange to shutdown.
 *
 * Unlike [io.modelcontextprotocol.kotlin.sdk.shared.ClientTransportState],
 * server transports do not support reconnection —
 * when a client disconnects, the server transport closes. Session recovery
 * on reconnect is handled at the [io.modelcontextprotocol.kotlin.sdk.server.Server] level.
 */
public enum class ServerTransportState {

    /**
     * The transport has just been created.
     *
     * This is the initial state.
     */
    New,

    /**
     * The transport is being initialized (starting I/O, establishing connections).
     */
    Initializing,

    /**
     * Initialization failed. Terminal state.
     */
    InitializationFailed,

    /**
     * The transport is actively serving requests.
     */
    Active,

    /**
     * The transport is in the process of shutting down.
     * No new outgoing messages should be accepted.
     */
    ShuttingDown,

    /**
     * Shutdown encountered an error. Terminal state.
     */
    ShutdownFailed,

    /**
     * The transport has fully stopped. Terminal state.
     */
    Stopped,

    ;

    internal companion object {
        val VALID_TRANSITIONS: Map<ServerTransportState, Set<ServerTransportState>> = mapOf(
            New to setOf(Initializing, Stopped),
            Initializing to setOf(Active, InitializationFailed),
            Active to setOf(ShuttingDown),
            ShuttingDown to setOf(Stopped, ShutdownFailed),
            // Terminal states allow no transitions
            InitializationFailed to emptySet(),
            Stopped to emptySet(),
            ShutdownFailed to emptySet(),
        )
    }
}
