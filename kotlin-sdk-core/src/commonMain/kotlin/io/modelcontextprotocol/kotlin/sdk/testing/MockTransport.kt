package io.modelcontextprotocol.kotlin.sdk.testing

import io.ktor.util.collections.ConcurrentSet
import io.modelcontextprotocol.kotlin.sdk.ErrorCode
import io.modelcontextprotocol.kotlin.sdk.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.RequestResult
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

private typealias RequestPredicate = (JSONRPCRequest) -> Boolean
private typealias RequestHandler = suspend (JSONRPCRequest) -> JSONRPCResponse

/**
 * A mock transport implementation for testing JSON-RPC communication.
 *
 * This class simulates transport that can be used to test server and client interactions by
 * allowing the registration of handlers for incoming requests and the ability to record
 * messages sent and received.
 *
 * The mock transport supports:
 * - Recording all sent and received messages (via `getSentMessages` and `getReceivedMessages`)
 * - Registering request handlers that respond to specific message predicates (e.g., by method)
 * - Setting up responses that can be either successful or with errors
 * - Waiting for specific messages to be received
 *
 * Note: This class is designed to be used as a test helper and should not be used in production.
 */
@Suppress("TooManyFunctions")
public open class MockTransport(configurer: MockTransport.() -> Unit = {}) : Transport {
    private val _sentMessages = mutableListOf<JSONRPCMessage>()
    private val _receivedMessages = mutableListOf<JSONRPCMessage>()

    private val requestHandlers = ConcurrentSet<Pair<RequestPredicate, RequestHandler>>()
    private val mutex = Mutex()

    public suspend fun getSentMessages(): List<JSONRPCMessage> = mutex.withLock { _sentMessages.toList() }

    public suspend fun getReceivedMessages(): List<JSONRPCMessage> = mutex.withLock { _receivedMessages.toList() }

    private var onMessageBlock: (suspend (JSONRPCMessage) -> Unit)? = null
    private var onCloseBlock: (() -> Unit)? = null
    private var onErrorBlock: ((Throwable) -> Unit)? = null

    init {
        configurer.invoke(this)
    }

    override suspend fun start(): Unit = Unit

    override suspend fun send(message: JSONRPCMessage) {
        mutex.withLock {
            _sentMessages += message
        }

        // Auto-respond to using preconfigured request handlers
        when (message) {
            is JSONRPCRequest -> {
                val response = requestHandlers.firstOrNull {
                    it.first.invoke(message)
                }?.second?.invoke(message)

                checkNotNull(response) {
                    "No request handler found for $message."
                }
                onMessageBlock?.invoke(response)
            }

            else -> {
                // TODO("Not implemented yet")
            }
        }
    }

    override suspend fun close() {
        onCloseBlock?.invoke()
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        onMessageBlock = { message ->
            mutex.withLock {
                _receivedMessages += message
            }
            block(message)
        }
    }

    override fun onClose(block: () -> Unit) {
        onCloseBlock = block
    }

    override fun onError(block: (Throwable) -> Unit) {
        onErrorBlock = block
    }

    public fun setupInitializationResponse() {
        // This method helps set up the mock for proper initialization
    }

    /**
     * Registers a handler that will be called when a message matching the given predicate is received.
     *
     * The handler is expected to return a `RequestResult` which will be used as the response to the request.
     *
     * @param predicate A predicate that matches the incoming `JSONRPCMessage`
     * for which the handler should be triggered.
     * @param block A function that processes the incoming `JSONRPCMessage` and returns a `RequestResult`
     * to be used as the response.
     */
    public fun onMessageReply(predicate: RequestPredicate, block: RequestHandler) {
        requestHandlers.add(Pair(predicate, block))
    }

    /**
     * Registers a handler for responses to a specific method.
     *
     * This method allows registering a handler that will be called when a message with the specified method
     * is received. The handler is expected to return a `RequestResult` which is the response to the request.
     *
     * @param method The method (from the `Method` enum) that the handler should respond to.
     * @param block A function that processes the incoming `JSONRPCRequest` and returns a `RequestResult`.
     *              The returned `RequestResult` will be used as the result of the response.
     */
    public fun <T : RequestResult> onMessageReplyResult(method: Method, block: (JSONRPCRequest) -> T) {
        onMessageReply(
            predicate = {
                it.method == method.value
            },
            block = {
                JSONRPCResponse(
                    id = it.id,
                    result = block.invoke(it),
                )
            },
        )
    }

    /**
     * Registers a handler that will be called when a request with the specified method is received
     * and an error response is to be generated.
     *
     * This handler is used to respond to requests with a specific method by returning an error response.
     * The handler is triggered when a request message with the given `method` is received.
     *
     * @param method The method (from the `Method` enum) that the handler should respond to with an error.
     * @param block A function that processes the incoming `JSONRPCRequest` and returns a `JSONRPCError`
     * to be used as the error response.
     *              The default block returns an internal error with the message "Expected error".
     */
    public fun onMessageReplyError(
        method: Method,
        block: (JSONRPCRequest) -> JSONRPCError = {
            JSONRPCError(
                code = ErrorCode.Defined.InternalError,
                message = "Expected error",
            )
        },
    ) {
        onMessageReply(
            predicate = {
                it.method == method.value
            },
            block = {
                JSONRPCResponse(
                    id = it.id,
                    error = block.invoke(it),
                )
            },
        )
    }

    /**
     * Waits for a JSON-RPC message that matches the given predicate in the received messages.
     *
     * @param poolInterval The interval at which the function polls the received messages. Default is 50 milliseconds.
     * @param timeout The maximum time to wait for a matching message. Default is 3 seconds.
     * @param timeoutMessage The error message to throw when the timeout is reached.
     * Default is "No message received matching predicate".
     * @param predicate A predicate function that returns true if the message matches the criteria.
     * @return The first JSON-RPC message that matches the predicate.
     */
    @OptIn(ExperimentalTime::class)
    public suspend fun awaitMessage(
        poolInterval: Duration = 50.milliseconds,
        timeout: Duration = 3.seconds,
        timeoutMessage: String = "No message received matching predicate",
        predicate: (JSONRPCMessage) -> Boolean,
    ): JSONRPCMessage {
        val clock = Clock.System
        val startTime = clock.now()
        val finishTime = startTime + timeout
        while (clock.now() < finishTime) {
            val found = mutex.withLock {
                _receivedMessages.firstOrNull { predicate(it) }
            }
            if (found != null) {
                return found
            }
            delay(poolInterval)
        }
        error(timeoutMessage)
    }
}
