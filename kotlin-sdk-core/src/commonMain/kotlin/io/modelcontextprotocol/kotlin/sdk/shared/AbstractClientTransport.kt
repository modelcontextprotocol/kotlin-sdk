package io.modelcontextprotocol.kotlin.sdk.shared

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.CONNECTION_CLOSED
import io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.INTERNAL_ERROR
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalAtomicApi::class)
public abstract class AbstractClientTransport : AbstractTransport() {

    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Current transport state. Thread-safe via atomic reference.
     * Initial state is [ClientTransportState.NEW].
     */
    protected val state: AtomicReference<ClientTransportState> = AtomicReference(ClientTransportState.NEW)

    /**
     * Performs transport-specific initialization (connections, I/O streams, background coroutines).
     *
     * Called by [start] during [ClientTransportState.INITIALIZING]. On exception, state transitions to
     * [ClientTransportState.INITIALIZATION_FAILED], [closeResources] is called, and exception propagates.
     */
    protected abstract suspend fun initialize()

    /**
     * Atomically transitions state from `from` to `to`.
     *
     * Validates transition per [ClientTransportState.VALID_TRANSITIONS] and performs atomic compare-and-exchange.
     *
     * @throws IllegalArgumentException if transition not allowed
     * @throws IllegalStateException if actual state doesn't match `from`
     */
    protected fun stateTransition(from: ClientTransportState, to: ClientTransportState) {
        require(to in ClientTransportState.VALID_TRANSITIONS.getValue(from)) {
            "Invalid transition: $from → $to"
        }
        val actualState = state.compareAndExchange(
            expectedValue = from,
            newValue = to,
        )
        check(actualState == from) {
            "Can't change state: expected transport state $from, but found $actualState."
        }
    }

    /**
     * Verifies that the current transport state matches the expected state and throws an exception if it does not.
     *
     * This method ensures that the transport's state is in alignment with the caller’s expectations before
     * proceeding with further operations. It is often used to enforce state invariants within the transport's
     * lifecycle mechanics, enabling robust error handling and debugging in case of unexpected state transitions.
     *
     * @param expected The expected transport state. An exception will be thrown if the actual state does not match this value.
     * @throws IllegalStateException If the current state does not match the expected state.
     */
    protected fun checkState(
        expected: ClientTransportState,
        lazyMessage: (ClientTransportState) -> Any = {
            "Expected transport state $expected, but found $it"
        },
    ) {
        val actualState = state.load()
        check(actualState == expected) { lazyMessage }
    }

    /**
     * Initiates the transport by transitioning through the required states to prepare it for operation.
     *
     * This method transitions the transport state from `NEW` to `INITIALIZING`, performs initialization
     * logic specific to the transport implementation, and then transitions the state to `OPERATIONAL`
     * upon successful initialization. If an exception occurs during initialization, the state is transitioned
     * to `INITIALIZATION_FAILED`, resources are cleaned up, and the exception is rethrown.
     *
     * This method operates within a suspend context to allow for asynchronous initialization tasks.
     *
     * Throws exception if the initialization process fails and transfer state to [ClientTransportState.INITIALIZATION_FAILED].
     */
    public final override suspend fun start() {
        stateTransition(from = ClientTransportState.NEW, to = ClientTransportState.INITIALIZING)
        try {
            initialize()
            stateTransition(from = ClientTransportState.INITIALIZING, to = ClientTransportState.OPERATIONAL)
        } catch (e: Exception) {
            state.store(ClientTransportState.INITIALIZATION_FAILED)
            closeResources()
            throw e
        }
    }

    public final override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        // fast path - state check to avoid nonsense operations
        if (state.load() != ClientTransportState.OPERATIONAL) {
            throw McpException(
                code = CONNECTION_CLOSED,
                message = "Transport is not started",
            )
        }
        // limitation: state could still change at the time of use
        try {
            performSend(message, options)
        } catch (e: CancellationException) {
            throw e // Always propagate cancellation
        } catch (e: Throwable) {
            _onError(e)
            if (e is McpException) {
                throw e
            } else {
                throw McpException(
                    code = INTERNAL_ERROR,
                    message = "Error while sending message: ${e.message}",
                    cause = e,
                )
            }
        }
    }

    /**
     * Transmits a JSON-RPC message via the underlying protocol.
     *
     * The method is responsible for transmitting a given JSON-RPC message to the intended destination.
     * It is implemented by concrete subclasses of the transport to handle specific transmission
     * mechanisms.
     *
     * Called by [send] after verifying transport [state].
     *
     * @param message The JSON-RPC message to be sent. Must comply with the JSON-RPC 2.0 specification.
     * @param options Optional parameters that influence how the message is transmitted, such as
     *                timeout settings or delivery guarantees. May be null if no specific options
     *                are required.
     */
    protected abstract suspend fun performSend(message: JSONRPCMessage, options: TransportSendOptions? = null)

    /**
     * Closes the client transport gracefully, transitioning its state to a non-operational state.
     *
     * The method is designed to operate within a suspendable context, allowing support for asynchronous
     * behavior during the resource cleanup and state transitions.
     *
     * Transitions [ClientTransportState.OPERATIONAL] → [ClientTransportState.SHUTTING_DOWN] → [ClientTransportState.STOPPED].
     * Calls [closeResources] then invokes [_onClose] callbacks exactly once.
     * On [kotlin.coroutines.cancellation.CancellationException], transitions to [ClientTransportState.UNKNOWN] and propagates.
     *
     * The method also handles unexpected exceptions gracefully during resource cleanup by transitioning
     * the transport to the `SHUTDOWN_FAILED` state.
     */
    public final override suspend fun close() {
        when (val currentState = state.load()) {
            // fast track - idempotency check for terminal and shutdown states
            ClientTransportState.INITIALIZING,
            ClientTransportState.INITIALIZATION_FAILED,
            ClientTransportState.SHUTTING_DOWN,
            ClientTransportState.SHUTDOWN_FAILED,
            ClientTransportState.STOPPED,
            ClientTransportState.UNKNOWN,
            -> return

            ClientTransportState.NEW -> {
                stateTransition(currentState, ClientTransportState.STOPPED)
                return
            }

            // Only OPERATIONAL state can transition to SHUTTING_DOWN
            ClientTransportState.OPERATIONAL -> stateTransition(currentState, ClientTransportState.SHUTTING_DOWN)
        }
        try {
            closeResources()
            stateTransition(from = ClientTransportState.SHUTTING_DOWN, to = ClientTransportState.STOPPED)
        } catch (e: CancellationException) {
            state.store(ClientTransportState.UNKNOWN)
            throw e // Always propagate cancellation
        } catch (e: Exception) {
            // Ignore errors during cleanup
            logger.error(e) { "Error during transport shutdown" }
            stateTransition(from = ClientTransportState.SHUTTING_DOWN, to = ClientTransportState.SHUTDOWN_FAILED)
        } finally {
            invokeOnCloseCallback()
        }
    }

    /**
     * Releases transport-specific resources (coroutines, connections, streams).
     *
     * Called by [close] during [ClientTransportState.SHUTTING_DOWN].
     * May be called even if [initialize] failed partially.
     * **DO NOT** call [_onClose] from this method - [close] handles it in the finally block.
     */
    protected open suspend fun closeResources() {
        // noop
    }
}
