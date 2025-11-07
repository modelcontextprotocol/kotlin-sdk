package io.modelcontextprotocol.kotlin.sdk.shared

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import kotlinx.coroutines.CompletableDeferred

/**
 * Implements [onClose], [onError] and [onMessage] functions of [Transport] providing
 * corresponding [_onClose], [_onError] and [_onMessage] properties to use for an implementation.
 */
@Suppress("PropertyName")
public abstract class AbstractTransport : Transport {
    protected var _onClose: (() -> Unit) = {}
        private set
    protected var _onError: ((Throwable) -> Unit) = {}
        private set

    // to not skip messages
    private val _onMessageInitialized = CompletableDeferred<Unit>()
    protected var _onMessage: (suspend ((JSONRPCMessage) -> Unit)) = {
        _onMessageInitialized.await()
        _onMessage.invoke(it)
    }
        private set

    override fun onClose(block: () -> Unit) {
        val old = _onClose
        _onClose = {
            old()
            block()
        }
    }

    override fun onError(block: (Throwable) -> Unit) {
        val old = _onError
        _onError = { e ->
            old(e)
            block(e)
        }
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        val old: suspend (JSONRPCMessage) -> Unit = when (_onMessageInitialized.isCompleted) {
            true -> _onMessage
            false -> { _ -> }
        }

        _onMessage = { message ->
            old(message)
            block(message)
        }

        _onMessageInitialized.complete(Unit)
    }
}
