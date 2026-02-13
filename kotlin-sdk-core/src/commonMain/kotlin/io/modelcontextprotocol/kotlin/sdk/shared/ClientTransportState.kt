package io.modelcontextprotocol.kotlin.sdk.shared

/**
 * States of a client transport within the protocol lifecycle
 *
 * These states are generally aligned with the corresponding phases
 * defined under the MCP [Protocol Lifecycle](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle).
 */
public enum class ClientTransportState {

    /**
     * The transport has just been created.
     *
     * This is the Initial state
     */
    New,

    /**
     * Indicates that the protocol
     * is in the [Initialization Phase](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle#initialization).
     */
    Initializing,

    /**
     * Indicates that the initialization phase of the protocol lifecycle has failed.
     *
     * This state suggests that the transport encountered an error or issue during
     * initialization, preventing it from successfully transitioning to a fully operational state.
     */
    InitializationFailed,

    /**
     * Represents the [operational phase](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle#operation)
     * of the protocol lifecycle.
     *
     * During the operation phase, the client and server exchange messages according to the negotiated capabilities.
     */
    Operational,

    /**
     * Represents the shutting down phase of the protocol lifecycle.
     *
     * This state indicates that the transport is in the process of shutting down
     * and transitioning to a non-operational state.
     *
     * During this phase, no new outgoing messages should be accepted.
     */
    ShuttingDown,

    /**
     * Indicates that the shutdown phase of the protocol lifecycle has failed.
     *
     * This state signifies that an error was encountered during the shutdown phase.
     */
    ShutdownFailed,

    /**
     * Indicates that the transport has fully stopped.
     *
     * This state represents the final phase of the lifecycle, where the transport is no longer
     * operational and all cleanup routines or resource deallocations have been completed.
     */
    Stopped,

    ;

    internal companion object {
        val VALID_TRANSITIONS: Map<ClientTransportState, Set<ClientTransportState>> = mapOf(
            New to setOf(Initializing, Stopped),
            Initializing to setOf(Operational, InitializationFailed),
            Operational to setOf(ShuttingDown),
            ShuttingDown to setOf(Stopped, ShutdownFailed),
            // Terminal states allow no transitions
            InitializationFailed to emptySet(),
            Stopped to emptySet(),
            ShutdownFailed to emptySet(),
        )
    }
}
