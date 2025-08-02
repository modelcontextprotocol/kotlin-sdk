package io.modelcontextprotocol.kotlin.sdk.shared

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

public const val MCP_SUBPROTOCOL: String = "mcp"

/**
 * Abstract class representing a WebSocket transport for the Model Context Protocol (MCP).
 * Handles communication over a WebSocket session.
 */
@OptIn(ExperimentalAtomicApi::class)
public abstract class WebSocketMcpTransport : AbstractTransport() {
    private val scope by lazy {
        CoroutineScope(session.coroutineContext + SupervisorJob())
    }

    private val initialized: AtomicBoolean = AtomicBoolean(false)

    /**
     * The WebSocket session used for communication.
     */
    protected abstract val session: WebSocketSession

    /**
     * Initializes the WebSocket session
     */
    protected abstract suspend fun initializeSession()

    override suspend fun start() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
            error(
                "WebSocketClientTransport already started! " +
                        "If using Client class, note that connect() calls start() automatically.",
            )
        }

        initializeSession()

        scope.launch(CoroutineName("WebSocketMcpTransport.collect#${hashCode()}")) {
            while (true) {
                val message = try {
                    session.incoming.receive()
                } catch (_: ClosedReceiveChannelException) {
                    return@launch
                }

                if (message !is Frame.Text) {
                    val e = IllegalArgumentException("Expected text frame, got ${message::class.simpleName}: $message")
                    _onError.invoke(e)
                    throw e
                }

                try {
                    val message = McpJson.decodeFromString<JSONRPCMessage>(message.readText())
                    _onMessage.invoke(message)
                } catch (e: Exception) {
                    _onError.invoke(e)
                    throw e
                }
            }
        }

        @OptIn(InternalCoroutinesApi::class)
        session.coroutineContext.job.invokeOnCompletion {
            if (it != null) {
                _onError.invoke(it)
            } else {
                _onClose.invoke()
            }
        }
    }

    override suspend fun send(message: JSONRPCMessage) {
        if (!initialized.load()) {
            error("Not connected")
        }

        session.outgoing.send(Frame.Text(McpJson.encodeToString(message)))
    }

    override suspend fun close() {
        if (!initialized.load()) {
            error("Not connected")
        }

        session.close()
        session.coroutineContext.job.join()
    }
}
