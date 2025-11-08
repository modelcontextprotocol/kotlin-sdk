package io.modelcontextprotocol.kotlin.sdk.shared

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotification
import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Notification
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.Progress
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressToken
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.Request
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import io.modelcontextprotocol.kotlin.sdk.types.fromJSON
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger { }

public const val IMPLEMENTATION_NAME: String = "mcp-ktor"

/**
 * Callback for progress notifications.
 */
public typealias ProgressCallback = (Progress) -> Unit

@OptIn(ExperimentalSerializationApi::class)
@Deprecated(
    message = "Use McpJson instead",
    replaceWith = ReplaceWith("McpJson", "io.modelcontextprotocol.kotlin.sdk.types.McpJson"),
    level = DeprecationLevel.WARNING,
)
public val McpJson: Json by lazy {
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
        explicitNulls = false
    }
}

/**
 * Additional initialization options.
 */
public open class ProtocolOptions(
    /**
     * Whether to restrict emitted requests to only those that the remote side has indicated
     * that they can handle, through their advertised capabilities.
     *
     * Note that this DOES NOT affect checking of _local_ side capabilities, as it is
     * considered a logic error to mis-specify those.
     *
     * Currently, this defaults to false, for backwards compatibility with SDK versions
     * that did not advertise capabilities correctly.
     * In the future, this will default to true.
     */
    public var enforceStrictCapabilities: Boolean = false,

    public var timeout: Duration = DEFAULT_REQUEST_TIMEOUT,
)

/**
 * The default request timeout.
 */
public val DEFAULT_REQUEST_TIMEOUT: Duration = 60.seconds

/**
 * Options that can be given per request.
 */
public data class RequestOptions(
    /**
     * If set, requests progress notifications from the remote end (if supported).
     * When progress notifications are received, this callback will be invoked.
     */
    val onProgress: ProgressCallback? = null,

    /**
     * A timeout for this request. If exceeded, an McpError with code `RequestTimeout`
     * will be raised from request().
     *
     * If not specified, `DEFAULT_REQUEST_TIMEOUT` will be used as the timeout.
     */
    val timeout: Duration = DEFAULT_REQUEST_TIMEOUT,
)

/**
 * Extra data given to request handlers.
 */
public class RequestHandlerExtra

internal val COMPLETED = CompletableDeferred(Unit).also { it.complete(Unit) }

/**
 * Implements MCP protocol framing on top of a pluggable transport, including
 * features like request/response linking, notifications, and progress.
 */
public abstract class Protocol(@PublishedApi internal val options: ProtocolOptions?) {
    public var transport: Transport? = null
        private set

    private val _requestHandlers:
        AtomicRef<PersistentMap<String, suspend (JSONRPCRequest, RequestHandlerExtra) -> RequestResult?>> =
        atomic(persistentMapOf())
    public val requestHandlers: Map<
        String,
        suspend (
            request: JSONRPCRequest,
            extra: RequestHandlerExtra,
        ) -> RequestResult?,
        >
        get() = _requestHandlers.value

    private val _notificationHandlers =
        atomic(persistentMapOf<String, suspend (notification: JSONRPCNotification) -> Unit>())
    public val notificationHandlers: Map<String, suspend (notification: JSONRPCNotification) -> Unit>
        get() = _notificationHandlers.value

    private val _responseHandlers:
        AtomicRef<PersistentMap<RequestId, (response: JSONRPCResponse?, error: Exception?) -> Unit>> =
        atomic(persistentMapOf())
    public val responseHandlers: Map<RequestId, (response: JSONRPCResponse?, error: Exception?) -> Unit>
        get() = _responseHandlers.value

    private val _progressHandlers: AtomicRef<PersistentMap<ProgressToken, ProgressCallback>> =
        atomic(persistentMapOf())
    public val progressHandlers: Map<ProgressToken, ProgressCallback>
        get() = _progressHandlers.value

    /**
     * Callback for when the connection is closed for any reason.
     *
     * This is invoked when close() is called as well.
     */
    public open fun onClose() {}

    /**
     * Callback for when an error occurs.
     *
     * Note that errors are not necessarily fatal they are used
     * for reporting any kind of exceptional condition out of a band.
     */
    public open fun onError(error: Throwable) {}

    /**
     * A handler to invoke for any request types that do not have their own handler installed.
     */
    public var fallbackRequestHandler: (
        suspend (request: JSONRPCRequest, extra: RequestHandlerExtra) -> RequestResult?
    )? =
        null

    /**
     * A handler to invoke for any notification types that do not have their own handler installed.
     */
    public var fallbackNotificationHandler: (suspend (notification: JSONRPCNotification) -> Unit)? = null

    init {
        setNotificationHandler<ProgressNotification>(Method.Defined.NotificationsProgress) { notification ->
            onProgress(notification)
            COMPLETED
        }

        setRequestHandler<PingRequest>(Method.Defined.Ping) { _, _ ->
            EmptyResult()
        }
    }

    /**
     * Attaches to the given transport, starts it, and starts listening for messages.
     *
     * The Protocol object assumes ownership of the Transport, replacing any callbacks that have already been set, and expects that it is the only user of the Transport instance going forward.
     */
    public open suspend fun connect(transport: Transport) {
        this.transport = transport
        transport.onClose {
            doClose()
        }

        transport.onError {
            onError(it)
        }

        transport.onMessage { message ->
            when (message) {
                is JSONRPCResponse -> onResponse(message, null)
                is JSONRPCRequest -> onRequest(message)
                is JSONRPCNotification -> onNotification(message)
                is JSONRPCError -> onResponse(null, message)
            }
        }

        logger.info { "Starting transport" }
        return transport.start()
    }

    private fun doClose() {
        val handlersToNotify = _responseHandlers.value.values.toList()
        _responseHandlers.getAndSet(persistentMapOf())
        _progressHandlers.getAndSet(persistentMapOf())
        transport = null
        onClose()

        val error = McpException(RPCError.ErrorCode.CONNECTION_CLOSED, "Connection closed")
        for (handler in handlersToNotify) {
            handler(null, error)
        }
    }

    private suspend fun onNotification(notification: JSONRPCNotification) {
        logger.trace { "Received notification: ${notification.method}" }

        val handler = notificationHandlers[notification.method] ?: fallbackNotificationHandler

        if (handler == null) {
            logger.trace { "No handler found for notification: ${notification.method}" }
            return
        }
        try {
            handler(notification)
        } catch (cause: Throwable) {
            logger.error(cause) { "Error handling notification: ${notification.method}" }
            onError(cause)
        }
    }

    private suspend fun onRequest(request: JSONRPCRequest) {
        logger.trace { "Received request: ${request.method} (id: ${request.id})" }

        val handler = requestHandlers[request.method] ?: fallbackRequestHandler

        if (handler === null) {
            logger.trace { "No handler found for request: ${request.method}" }
            try {
                transport?.send(
                    JSONRPCError(
                        id = request.id,
                        error = RPCError(
                            code = RPCError.ErrorCode.METHOD_NOT_FOUND,
                            message = "Server does not support ${request.method}",
                        ),
                    ),
                )
            } catch (cause: Throwable) {
                logger.error(cause) { "Error sending method not found response" }
                onError(cause)
            }
            return
        }

        try {
            val result = handler(request, RequestHandlerExtra())
            logger.trace { "Request handled successfully: ${request.method} (id: ${request.id})" }

            transport?.send(
                JSONRPCResponse(
                    id = request.id,
                    result = result ?: EmptyResult(),
                ),
            )
        } catch (cause: Throwable) {
            logger.error(cause) { "Error handling request: ${request.method} (id: ${request.id})" }

            try {
                transport?.send(
                    JSONRPCError(
                        id = request.id,
                        error = RPCError(
                            code = RPCError.ErrorCode.INTERNAL_ERROR,
                            message = cause.message ?: "Internal error",
                        ),
                    ),
                )
            } catch (sendError: Throwable) {
                logger.error(sendError) {
                    "Failed to send error response for request: ${request.method} (id: ${request.id})"
                }
                // Optionally implement fallback behavior here
            }
        }
    }

    private fun onProgress(notification: ProgressNotification) {
        logger.trace {
            "Received progress notification: token=${notification.params.progressToken}, progress=${notification.params.progress}/${notification.params.total}"
        }
        val progress = notification.params.progress
        val total = notification.params.total
        val message = notification.params.message
        val progressToken = notification.params.progressToken

        val handler = _progressHandlers.value[progressToken]
        if (handler == null) {
            val error = Error(
                "Received a progress notification for an unknown token: ${McpJson.encodeToString(notification)}",
            )
            logger.error { error.message }
            onError(error)
            return
        }

        handler.invoke(Progress(progress, total, message))
    }

    private fun onResponse(response: JSONRPCResponse?, error: JSONRPCError?) {
        val messageId = response?.id ?: error?.id

        val oldResponseHandlers = _responseHandlers.getAndUpdate { current ->
            if (messageId != null && messageId in current) {
                current.remove(messageId)
            } else {
                current
            }
        }

        val handler = oldResponseHandlers[messageId]

        if (handler != null) {
            messageId?.let { msg -> _progressHandlers.update { it.remove(msg) } }
        } else {
            onError(Error("Received a response for an unknown message ID: ${McpJson.encodeToString(response)}"))
            return
        }

        if (response != null) {
            handler(response, null)
        } else {
            check(error != null)
            val error = McpException(
                code = error.error.code,
                message = error.error.message,
                data = error.error.data,
            )
            handler(null, error)
        }
    }

    /**
     * Closes the connection.
     */
    public suspend fun close() {
        transport?.close()
    }

    /**
     * A method to check if a capability is supported by the remote side, for the given method to be called.
     *
     * This should be implemented by subclasses.
     */
    protected abstract fun assertCapabilityForMethod(method: Method)

    /**
     * A method to check if a notification is supported by the local side, for the given method to be sent.
     *
     * This should be implemented by subclasses.
     */
    protected abstract fun assertNotificationCapability(method: Method)

    /**
     * A method to check if a request handler is supported by the local side, for the given method to be handled.
     *
     * This should be implemented by subclasses.
     */
    public abstract fun assertRequestHandlerCapability(method: Method)

    /**
     * Sends a request and waits for a response.
     *
     * Do not use this method to emit notifications! Use notification() instead.
     */
    public suspend fun <T : RequestResult> request(request: Request, options: RequestOptions? = null): T {
        logger.trace { "Sending request: ${request.method}" }
        val result = CompletableDeferred<T>()
        val transport = transport ?: throw Error("Not connected")

        if (this@Protocol.options?.enforceStrictCapabilities == true) {
            assertCapabilityForMethod(request.method)
        }

        val message = request.toJSON()
        val messageId = message.id

        if (options?.onProgress != null) {
            logger.trace { "Registering progress handler for request id: $messageId" }
            _progressHandlers.update { current ->
                current.put(messageId, options.onProgress)
            }
        }

        _responseHandlers.update { current ->
            current.put(messageId) { response, error ->
                if (error != null) {
                    result.completeExceptionally(error)
                    return@put
                }

                try {
                    @Suppress("UNCHECKED_CAST")
                    result.complete(response!!.result as T)
                } catch (error: Throwable) {
                    result.completeExceptionally(error)
                }
            }
        }

        val cancel: suspend (Throwable) -> Unit = { reason: Throwable ->
            _responseHandlers.update { current -> current.remove(messageId) }
            _progressHandlers.update { current -> current.remove(messageId) }

            val notification = CancelledNotification(
                params = CancelledNotificationParams(
                    requestId = messageId,
                    reason = reason.message ?: "Unknown",
                ),
            )

            val serialized = JSONRPCNotification(
                notification.method.value,
                params = McpJson.encodeToJsonElement(notification),
            )
            transport.send(serialized)

            result.completeExceptionally(reason)
        }

        val timeout = options?.timeout ?: DEFAULT_REQUEST_TIMEOUT
        try {
            withTimeout(timeout) {
                logger.trace { "Sending request message with id: $messageId" }
                this@Protocol.transport?.send(message)
            }
            return result.await()
        } catch (cause: TimeoutCancellationException) {
            logger.error { "Request timed out after ${timeout.inWholeMilliseconds}ms: ${request.method}" }
            cancel(
                McpException(
                    code = RPCError.ErrorCode.REQUEST_TIMEOUT,
                    message = "Request timed out",
                    data = JsonObject(mutableMapOf("timeout" to JsonPrimitive(timeout.inWholeMilliseconds))),
                ),
            )
            result.cancel(cause)
            throw cause
        }
    }

    /**
     * Emits a notification, which is a one-way message that does not expect a response.
     */
    public suspend fun notification(notification: Notification) {
        logger.trace { "Sending notification: ${notification.method}" }
        val transport = this.transport ?: error("Not connected")
        assertNotificationCapability(notification.method)

        val message = notification.toJSON()
        transport.send(message)
    }

    /**
     * Registers a handler to invoke when this protocol object receives a request with the given method.
     *
     * Note that this will replace any previous request handler for the same method.
     */
    public inline fun <reified T : Request> setRequestHandler(
        method: Method,
        noinline block: suspend (T, RequestHandlerExtra) -> RequestResult?,
    ) {
        setRequestHandlerInternal(method, block)
    }

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Request> setRequestHandlerInternal(
        method: Method,
        block: suspend (T, RequestHandlerExtra) -> RequestResult?,
    ) {
        assertRequestHandlerCapability(method)

        _requestHandlers.update { current ->
            current.put(method.value) { jSONRPCRequest, extraHandler ->
                val request = jSONRPCRequest.fromJSON()
                val response = block(request as T, extraHandler)
                response
            }
        }
    }

    /**
     * Removes the request handler for the given method.
     */
    public fun removeRequestHandler(method: Method) {
        _requestHandlers.update { current -> current.remove(method.value) }
    }

    /**
     * Registers a handler to invoke when this protocol object receives a notification with the given method.
     *
     * Note that this will replace any previous notification handler for the same method.
     */
    public fun <T : Notification> setNotificationHandler(method: Method, handler: (notification: T) -> Deferred<Unit>) {
        _notificationHandlers.update { current ->
            current.put(method.value) {
                @Suppress("UNCHECKED_CAST")
                handler(it.fromJSON() as T)
            }
        }
    }

    /**
     * Removes the notification handler for the given method.
     */
    public fun removeNotificationHandler(method: Method) {
        _notificationHandlers.update { current -> current.remove(method.value) }
    }
}
