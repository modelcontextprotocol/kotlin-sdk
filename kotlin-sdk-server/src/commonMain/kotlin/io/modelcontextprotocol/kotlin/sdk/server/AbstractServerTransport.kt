package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KLogger
import io.modelcontextprotocol.kotlin.sdk.InternalMcpApi
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.CONNECTION_CLOSED
import io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.INTERNAL_ERROR
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

/**
 * Abstract base class for server-side transport implementations.
 *
 * Manages the transport lifecycle via [ServerTransportState] state machine,
 * enforcing valid state transitions and gating operations on the current state.
 *
 * Subclasses implement three hooks:
 * - [initialize] for transport-specific startup
 * - [performSend] for transport-specific message transmission
 * - [closeResources] for transport-specific cleanup
 */
@OptIn(ExperimentalAtomicApi::class, InternalMcpApi::class)
public abstract class AbstractServerTransport : AbstractTransport() {

    protected abstract val logger: KLogger

    private val _state: AtomicReference<ServerTransportState> = AtomicReference(ServerTransportState.New)

    @InternalMcpApi
    protected val state: ServerTransportState
        get() = _state.load()

    @InternalMcpApi
    protected fun updateState(new: ServerTransportState) {
        _state.store(new)
    }

    @InternalMcpApi
    protected fun stateTransition(from: ServerTransportState, to: ServerTransportState) {
        require(to in ServerTransportState.VALID_TRANSITIONS.getValue(from)) {
            "Invalid transition: $from → $to"
        }
        val actualState = _state.compareAndExchange(
            expectedValue = from,
            newValue = to,
        )
        check(actualState == from) {
            "Can't change state: expected transport state $from, but found $actualState."
        }
    }

    protected fun checkState(
        expected: ServerTransportState,
        lazyMessage: (ServerTransportState) -> Any = {
            "Expected transport state $expected, but found $it"
        },
    ) {
        val actualState = state
        check(actualState == expected) { lazyMessage(actualState) }
    }

    /**
     * Performs transport-specific initialization.
     *
     * Called by [start] during [ServerTransportState.Initializing].
     */
    protected abstract suspend fun initialize()

    /**
     * Transmits a JSON-RPC message via the underlying transport.
     *
     * Called by [send] after verifying the transport [state] is [ServerTransportState.Active].
     */
    protected abstract suspend fun performSend(message: JSONRPCMessage, options: TransportSendOptions? = null)

    /**
     * Releases transport-specific resources.
     *
     * Called by [close] during [ServerTransportState.ShuttingDown].
     */
    protected abstract suspend fun closeResources()

    public override suspend fun start() {
        stateTransition(from = ServerTransportState.New, to = ServerTransportState.Initializing)
        @Suppress("TooGenericExceptionCaught")
        try {
            initialize()
            stateTransition(from = ServerTransportState.Initializing, to = ServerTransportState.Active)
        } catch (e: Exception) {
            _state.store(ServerTransportState.InitializationFailed)
            closeResources()
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    public override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        if (state != ServerTransportState.Active) {
            throw McpException(
                code = CONNECTION_CLOSED,
                message = "Transport is not active",
            )
        }
        try {
            performSend(message, options)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _onError(e)
            @Suppress("InstanceOfCheckForException")
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

    @Suppress("TooGenericExceptionCaught")
    public override suspend fun close() {
        val performClose: Boolean
        when (state) {
            ServerTransportState.Active -> {
                stateTransition(ServerTransportState.Active, ServerTransportState.ShuttingDown)
                performClose = true
            }

            ServerTransportState.New -> {
                stateTransition(ServerTransportState.New, ServerTransportState.Stopped)
                performClose = false
            }

            else -> {
                performClose = false
            }
        }
        if (performClose) {
            try {
                closeResources()
                stateTransition(from = ServerTransportState.ShuttingDown, to = ServerTransportState.Stopped)
            } catch (e: CancellationException) {
                stateTransition(from = ServerTransportState.ShuttingDown, to = ServerTransportState.ShutdownFailed)
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Error during transport shutdown" }
                stateTransition(from = ServerTransportState.ShuttingDown, to = ServerTransportState.ShutdownFailed)
            } finally {
                invokeOnCloseCallback()
            }
        }
    }
}
