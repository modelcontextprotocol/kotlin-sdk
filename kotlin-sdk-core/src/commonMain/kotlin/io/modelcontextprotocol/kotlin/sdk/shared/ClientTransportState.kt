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
    NEW,

    /**
     * Indicates that the protocol
     * is in the [Initialization Phase](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle#initialization).
     */
    INITIALIZING,

    /**
     * Indicates that the initialization phase of the protocol lifecycle has failed.
     *
     * This state suggests that the transport encountered an error or issue during
     * initialization, preventing it from successfully transitioning to a fully operational state.
     */
    INITIALIZATION_FAILED,

    /**
     * Represents the [operational phase](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle#operation)
     * of the protocol lifecycle.
     *
     * During the operation phase, the client and server exchange messages according to the negotiated capabilities.
     */
    OPERATIONAL,

    /**
     * Represents the shutting down phase of the protocol lifecycle.
     *
     * This state indicates that the transport is in the process of shutting down
     * and transitioning to a non-operational state.
     *
     * During this phase, no new outgoing messages should be accepted.
     */
    SHUTTING_DOWN,

    /**
     * Indicates that the shutdown phase of the protocol lifecycle has failed.
     *
     * This state signifies that an error was encountered during the shutdown phase.
     */
    SHUTDOWN_FAILED,

    /**
     * Indicates that the transport has fully stopped.
     *
     * This state represents the final phase of the lifecycle, where the transport is no longer
     * operational and all cleanup routines or resource deallocations have been completed.
     */
    STOPPED,

    ;

    internal companion object {
        val VALID_TRANSITIONS: Map<ClientTransportState, Set<ClientTransportState>> = mapOf(
            NEW to setOf(INITIALIZING, STOPPED),
            INITIALIZING to setOf(OPERATIONAL, INITIALIZATION_FAILED),
            OPERATIONAL to setOf(SHUTTING_DOWN),
            SHUTTING_DOWN to setOf(STOPPED, SHUTDOWN_FAILED),
            // Terminal states allow no transitions
            INITIALIZATION_FAILED to emptySet(),
            STOPPED to emptySet(),
            SHUTDOWN_FAILED to emptySet(),
        )
    }
}
