package io.modelcontextprotocol.kotlin.sdk.shared

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.coroutineName
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Implements [onClose], [onError] and [onMessage] functions of [Transport] providing
 * corresponding [_onClose], [_onError] and [_onMessage] properties to use for an implementation.
 */
@OptIn(ExperimentalAtomicApi::class)
public abstract class AbstractTransport(
    context: CoroutineContext = EmptyCoroutineContext,
    protected val handlerContext: CoroutineContext = Dispatchers.Default,
) : Transport {
    private val logger: KLogger = KotlinLogging.logger {}

    private val onCloseCalled = AtomicBoolean(false)
    private var _onClose: (() -> Unit) = {}
    private var _onError: ((Throwable) -> Unit) = {}

    // to not skip messages
    private val _onMessageInitialized = CompletableDeferred<Unit>()
    private var _onMessage: (suspend ((JSONRPCMessage) -> Unit)) = {
        _onMessageInitialized.await()
        _onMessage.invoke(it)
    }

    private val inProgressRequests = atomic(persistentSetOf<Job>())

    @Suppress("InjectDispatcher")
    protected val scope: CoroutineScope =
        CoroutineScope(
            Dispatchers.Default + context + SupervisorJob(context[Job]) + CoroutineExceptionHandler { ctx, e ->
                logger.error(e) {
                    "Uncaught error in transport scope from ${ctx[CoroutineName] ?: "unknown coroutine"}"
                }
                handleError(e)
            },
        )

    @Suppress("TooGenericExceptionCaught")
    protected fun handleError(error: Throwable) {
        if (error is CancellationException) {
            throw error
        }

        try {
            _onError.invoke(error)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error.addSuppressed(e)
            logger.error(e) { "Failed to invoke error handler for $error" }
        }
    }

    protected fun handleMessage(message: JSONRPCMessage): Job {
        val name = message.coroutineName
        return scope.launch(handlerContext + name) {
            try {
                doHandle(message, name)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Already handled in doHandle via _onError
            }
        }.also { job ->
            inProgressRequests.update { it.add(job) }
            job.invokeOnCompletion { _ ->
                inProgressRequests.update { it.remove(job) }
            }
        }
    }

    protected suspend fun handleMessageInline(message: JSONRPCMessage) {
        val name = message.coroutineName
        withContext(name) {
            doHandle(message, name)
        }
    }

    private suspend fun doHandle(message: JSONRPCMessage, name: CoroutineName) {
        @Suppress("TooGenericExceptionCaught")
        try {
            _onMessage.invoke(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Error processing message ${name.name}." }
            handleError(e)
            throw e
        }
    }

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
        if (_onMessageInitialized.isCompleted) {
            val old = _onMessage
            _onMessage = { message ->
                old(message)
                block(message)
            }
        } else {
            _onMessage = block
            _onMessageInitialized.complete(Unit)
        }
    }

    protected suspend fun joinInProgressHandlers(): Unit =
        inProgressRequests.getAndSet(persistentSetOf()).forEach { runCatching { it.join() } }

    protected fun cancelInProgressHandlers(message: String, error: Throwable?) {
        inProgressRequests.getAndSet(persistentSetOf()).forEach { it.cancel(message, error) }
    }

    /**
     * Helper to safely cancel and join all in-progress handlers.
     */
    protected suspend fun shutdownHandlers() {
        cancelInProgressHandlers("Closing", null)
        joinInProgressHandlers()
    }

    /**
     * Invokes the `_onClose` callback if it has not been already triggered.
     *
     * This method ensures the `_onClose` callback is executed only once by utilizing
     * an atomic flag (`onCloseCalled`). If the callback has already been executed,
     * the method does nothing. Any exceptions thrown during the execution of the
     * `_onClose` callback are caught and suppressed.
     *
     * Note: This method automatically cancels the transport's internal [scope].
     */
    protected fun invokeOnCloseCallback() {
        if (onCloseCalled.compareAndSet(expectedValue = false, newValue = true)) {
            scope.cancel()
            runCatching { _onClose() }
        }
    }
}
