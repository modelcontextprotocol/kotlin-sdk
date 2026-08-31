package io.modelcontextprotocol.kotlin.sdk.shared

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.InternalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.COMPLETE_RESULT_TYPE
import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotification
import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.DEFAULT_NEGOTIATED_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCEmptyMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_MODERN_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.MODERN_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Notification
import io.modelcontextprotocol.kotlin.sdk.types.PROTOCOL_VERSION_META_KEY
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.Progress
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressToken
import io.modelcontextprotocol.kotlin.sdk.types.ProtocolEra
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.Request
import io.modelcontextprotocol.kotlin.sdk.types.RequestEnvelope
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import io.modelcontextprotocol.kotlin.sdk.types.UnsupportedProtocolVersionData
import io.modelcontextprotocol.kotlin.sdk.types.WireResult
import io.modelcontextprotocol.kotlin.sdk.types.checkInboundResult
import io.modelcontextprotocol.kotlin.sdk.types.encodeOutboundResult
import io.modelcontextprotocol.kotlin.sdk.types.fromJSON
import io.modelcontextprotocol.kotlin.sdk.types.isAvailableIn
import io.modelcontextprotocol.kotlin.sdk.types.isModernProtocolVersion
import io.modelcontextprotocol.kotlin.sdk.types.outboundErrorCode
import io.modelcontextprotocol.kotlin.sdk.types.toEnvelopeLenient
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import io.modelcontextprotocol.kotlin.sdk.types.validateEnvelope
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger { }

/** Default implementation name used in MCP handshake. */
public const val IMPLEMENTATION_NAME: String = "mcp-ktor"

/**
 * Callback for progress notifications.
 */
public typealias ProgressCallback = (Progress) -> Unit

/**
 * Additional initialization options.
 *
 * [handlerCoroutineContext] is read once when [Protocol.connect] attaches a transport and stays
 * fixed until the next [Protocol.connect].
 *
 * @property enforceStrictCapabilities whether to restrict emitted requests to only those the remote
 * side advertised support for. Local-side capability checks are unaffected. Defaults to `false` for
 * backward compatibility with SDK versions that did not advertise capabilities correctly.
 * @property timeout default timeout for outgoing requests
 * @property handlerCoroutineContext coroutine context used to run inbound request and notification
 * handlers once initialization has completed. Must contain a real dispatching
 * [kotlin.coroutines.ContinuationInterceptor]. `Dispatchers.Unconfined` and unconfined test
 * dispatchers are unsupported, since handler resumptions would then run on the transport read loop
 * and reintroduce head-of-line blocking. Any [kotlinx.coroutines.Job] in this context is ignored.
 */
public open class ProtocolOptions(
    public var enforceStrictCapabilities: Boolean = false,
    public var timeout: Duration = DEFAULT_REQUEST_TIMEOUT,
    public var handlerCoroutineContext: CoroutineContext = Dispatchers.Default,
    // Internal handler-concurrency bounds, read once at connect and not part of the public surface.
    internal var maxConcurrentHandlers: Int = DEFAULT_MAX_CONCURRENT_HANDLERS,
    internal var maxInFlightHandlers: Int = DEFAULT_MAX_IN_FLIGHT_HANDLERS,
)

/**
 * The default request timeout.
 */
public val DEFAULT_REQUEST_TIMEOUT: Duration = 60.seconds

/** Default cap on concurrently executing inbound handlers per connection. */
internal const val DEFAULT_MAX_CONCURRENT_HANDLERS: Int = 64

/** Default cap on launched-but-not-completed inbound handlers per connection. */
internal const val DEFAULT_MAX_IN_FLIGHT_HANDLERS: Int = 256

/**
 * Options that can be given per request.
 *
 * @param relatedRequestId if present, used to indicate to the transport which incoming request to
 * associate this outgoing message with.
 * @param resumptionToken the resumption token used to continue long-running requests that were interrupted.
 * Allows clients to reconnect and continue from where they left off, if supported by the transport.
 * @param onResumptionToken callback invoked when the resumption token changes, if supported by the transport.
 * Allows clients to persist the latest token for potential reconnection.
 * @property onProgress callback for progress notifications.
 * If set, requests progress notifications from the remote end (if supported);
 * when progress notifications are received, this callback is invoked.
 * @property timeout a timeout for this request.
 * If exceeded, a [McpException] with code `RequestTimeout` is raised from [Protocol.request].
 * If not specified, [DEFAULT_REQUEST_TIMEOUT] is used.
 */
public class RequestOptions(
    relatedRequestId: RequestId? = null,
    resumptionToken: String? = null,
    onResumptionToken: ((String) -> Unit)? = null,
    public val onProgress: ProgressCallback? = null,
    public val timeout: Duration = DEFAULT_REQUEST_TIMEOUT,
) : TransportSendOptions(relatedRequestId, resumptionToken, onResumptionToken) {
    /** Destructuring component for [onProgress]. */
    public operator fun component4(): ProgressCallback? = onProgress

    /** Destructuring component for [timeout]. */
    public operator fun component5(): Duration = timeout

    /** Creates a copy of this [RequestOptions] with the specified fields replaced. */
    public fun copy(
        relatedRequestId: RequestId? = this.relatedRequestId,
        resumptionToken: String? = this.resumptionToken,
        onResumptionToken: ((String) -> Unit)? = this.onResumptionToken,
        onProgress: ProgressCallback? = this.onProgress,
        timeout: Duration = this.timeout,
    ): RequestOptions = RequestOptions(relatedRequestId, resumptionToken, onResumptionToken, onProgress, timeout)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        if (!super.equals(other)) return false

        other as RequestOptions

        return onProgress == other.onProgress && timeout == other.timeout
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + onProgress.hashCode()
        result = 31 * result + timeout.hashCode()
        return result
    }

    override fun toString(): String =
        "RequestOptions(relatedRequestId=$relatedRequestId, resumptionToken=$resumptionToken, onResumptionToken=$onResumptionToken, onProgress=$onProgress, timeout=$timeout)"
}

/**
 * Extra data given to request handlers.
 *
 * One instance exists per in-flight inbound request. It is passed as the second parameter to
 * low-level handlers registered via [Protocol.setRequestHandler] **and** installed into the
 * handler coroutine's [CoroutineContext], so code nested anywhere under the handler can reach it
 * via [currentRequestHandlerExtra] without parameter plumbing.
 *
 * [protocolVersion], [clientCapabilities] and [clientInfo] are always populated, whichever protocol
 * lifecycle the request arrived under, so a handler needs no branch on it. [envelope] is there for
 * code that does need to tell the two apart.
 *
 * @property requestId the JSON-RPC id of the request being handled
 * @property method the request's method
 * @property meta the `_meta` of the request being handled, exactly as received
 */
public class RequestHandlerExtra internal constructor(
    public val requestId: RequestId,
    public val method: Method,
    private val protocol: Protocol,
    internal val capturedTransport: Transport,
    internal val era: ProtocolEra = ProtocolEra.Legacy,
    public val meta: RequestMeta? = null,
    envelope: RequestEnvelope? = null,
    handshakeProtocolVersion: String? = null,
    handshakeClientCapabilities: ClientCapabilities? = null,
    handshakeClientInfo: Implementation? = null,
) : AbstractCoroutineContextElement(Key) {

    /**
     * The per-request protocol envelope, or `null` on a request served under the connection-scoped
     * lifecycle.
     *
     * The envelope's keys also stay in [meta], which reports `_meta` exactly as it arrived.
     */
    @ExperimentalMcpApi
    public val envelope: RequestEnvelope? = envelope

    /**
     * The protocol version governing this request.
     *
     * Comes from the envelope on the request-scoped lifecycle and from the `initialize` handshake on
     * the connection-scoped one, so it is never `null`.
     */
    public val protocolVersion: String = envelope?.protocolVersion
        ?: handshakeProtocolVersion.takeIf { era == ProtocolEra.Legacy }
        ?: DEFAULT_NEGOTIATED_PROTOCOL_VERSION

    /**
     * The capabilities the client declared for this request.
     *
     * On the request-scoped lifecycle these come from the request's own envelope, and never from
     * any other request: a request whose envelope did not reach here declares nothing, rather than
     * borrowing an earlier declaration. On the connection-scoped lifecycle they come from the
     * `initialize` handshake, and are empty when there was none.
     */
    public val clientCapabilities: ClientCapabilities = envelope?.clientCapabilities
        ?: handshakeClientCapabilities.takeIf { era == ProtocolEra.Legacy }
        ?: ClientCapabilities()

    /**
     * The client identity, when the peer reported one.
     *
     * Self-reported and unverified, so confine it to display, logging and debugging rather than
     * behavior or security decisions. Scoped to the request as [clientCapabilities] is.
     */
    public val clientInfo: Implementation? = envelope?.clientInfo
        ?: handshakeClientInfo.takeIf { era == ProtocolEra.Legacy }

    /** Context key for retrieving the extra from a handler coroutine's context. */
    public companion object Key : CoroutineContext.Key<RequestHandlerExtra>

    /**
     * Sends a notification tagged with `relatedRequestId = `[requestId], letting transports
     * associate it with the request being handled (e.g. route it onto the request's SSE stream).
     */
    public suspend fun sendNotification(notification: Notification) {
        protocol.notification(notification, relatedRequestId = requestId)
    }

    /**
     * Sends a request to the peer tagged with `relatedRequestId = `[requestId].
     *
     * Mirrors [Protocol.request]; cancellation of the handler coroutine propagates into this call.
     */
    public suspend fun <T : RequestResult> sendRequest(request: Request, options: RequestOptions? = null): T {
        val base = options ?: RequestOptions()
        return protocol.request(
            request,
            RequestOptions(
                relatedRequestId = requestId,
                resumptionToken = base.resumptionToken,
                onResumptionToken = base.onResumptionToken,
                onProgress = base.onProgress,
                timeout = base.timeout,
            ),
        )
    }
}

/** The [RequestHandlerExtra] of the MCP request being handled by the current coroutine, or `null` outside a handler. */
public suspend fun currentRequestHandlerExtra(): RequestHandlerExtra? = currentCoroutineContext()[RequestHandlerExtra]

/** The `_meta` this request carries, or `null` when it carries none. */
private fun JSONRPCRequest.requestMeta(): RequestMeta? =
    ((params as? JsonObject)?.get("_meta") as? JsonObject)?.let(::RequestMeta)

/**
 * The lifecycle [meta] asks to be served under.
 *
 * A request claims the request-scoped lifecycle if and only if its `_meta` carries
 * [PROTOCOL_VERSION_META_KEY]. Presence of the key is the claim, not the shape of its value, so a
 * malformed claim is reported rather than falling back to the connection-scoped lifecycle. Nothing
 * an earlier request established participates, so one connection may interleave both.
 */
@OptIn(ExperimentalMcpApi::class)
private fun eraOf(meta: RequestMeta?): ProtocolEra =
    if (meta?.get(PROTOCOL_VERSION_META_KEY) != null) ProtocolEra.Modern else ProtocolEra.Legacy

internal val COMPLETED = CompletableDeferred(Unit).also { it.complete(Unit) }

/** Cap on how many recently-cancelled outbound request ids are remembered to quiet late responses/progress. */
private const val CANCELLED_REQUEST_IDS_REMEMBERED = 256

// Bypass set: exempt from both bound tiers so the connection cannot self-block.
// Responses are not listed — they are handled inline before dispatch.
private val CONTROL_METHODS: Set<String> = setOf(
    Method.Defined.Ping.value,
    Method.Defined.NotificationsCancelled.value,
    Method.Defined.NotificationsProgress.value,
    Method.Defined.NotificationsInitialized.value,
)

/**
 * Implements MCP protocol framing on top of a pluggable transport, including
 * features like request/response linking, notifications, and progress.
 *
 * @property options protocol-level configuration; `null` falls back to defaults
 */
public abstract class Protocol(@PublishedApi internal val options: ProtocolOptions?) {
    private val connectionRef: AtomicRef<Connection?> = atomic(null)

    /** The active transport, or `null` if not connected. */
    public val transport: Transport?
        get() = connectionRef.value?.transport

    private val _requestHandlers:
        AtomicRef<PersistentMap<String, suspend (JSONRPCRequest, RequestHandlerExtra) -> RequestResult?>> =
        atomic(persistentMapOf())

    /** Registered request handlers keyed by method name. */
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

    /** Registered notification handlers keyed by method name. */
    public val notificationHandlers: Map<String, suspend (notification: JSONRPCNotification) -> Unit>
        get() = _notificationHandlers.value

    private val _responseHandlers:
        AtomicRef<PersistentMap<RequestId, (response: JSONRPCResponse?, error: Exception?) -> Unit>> =
        atomic(persistentMapOf())

    /** Pending response handlers keyed by request ID. */
    public val responseHandlers: Map<RequestId, (response: JSONRPCResponse?, error: Exception?) -> Unit>
        get() = _responseHandlers.value

    private val _progressHandlers: AtomicRef<PersistentMap<ProgressToken, ProgressCallback>> =
        atomic(persistentMapOf())

    /** Registered progress callbacks keyed by progress token. */
    public val progressHandlers: Map<ProgressToken, ProgressCallback>
        get() = _progressHandlers.value

    private val recentlyCancelledRequestIds: AtomicRef<PersistentList<RequestId>> =
        atomic(persistentListOf())

    /** Remembers a locally-cancelled outbound request id so its late response/progress is quieted. */
    private fun rememberCancelledRequestId(id: RequestId) {
        recentlyCancelledRequestIds.update { current ->
            val appended = current.adding(id)
            if (appended.size > CANCELLED_REQUEST_IDS_REMEMBERED) appended.removingAt(0) else appended
        }
    }

    /** Whether [id] was recently cancelled locally (see [rememberCancelledRequestId]). */
    private fun isRecentlyCancelled(id: RequestId?): Boolean =
        id != null && recentlyCancelledRequestIds.value.contains(id)

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
        setNotificationHandler<CancelledNotification>(Method.Defined.NotificationsCancelled) { notification ->
            handleCancelledNotification(notification)
            COMPLETED
        }

        setNotificationHandler<ProgressNotification>(Method.Defined.NotificationsProgress) { notification ->
            onProgress(notification)
            COMPLETED
        }

        setRequestHandler<PingRequest>(Method.Defined.Ping) { _, _ ->
            EmptyResult()
        }
    }

    /**
     * Enables concurrent dispatch of inbound messages on the current connection.
     *
     * Called by subclasses when the MCP initialization phase completes (the client after the
     * initialize exchange; the server session on receiving `notifications/initialized`). Until
     * then, inbound messages are handled inline in arrival order. Reset by [connect].
     */
    protected fun enableConcurrentDispatch() {
        val connection = connectionRef.value ?: return
        connection.concurrentDispatchEnabled.value = true
    }

    /**
     * Router hook invoked whenever `notifications/initialized` is received, before handler lookup.
     *
     * Runs regardless of which notification handler is registered for the method, so replacing the
     * handler via [setNotificationHandler] cannot disable subclass initialization logic.
     */
    protected open fun onInitializedNotification() {}

    /**
     * Router hook invoked for every inbound notification, before handler lookup.
     *
     * Runs whether or not a handler is registered for the method, and in addition to it, so that
     * subclass bookkeeping cannot be disabled by replacing or omitting the handler.
     */
    protected open suspend fun onNotificationRouted(notification: JSONRPCNotification) {}

    /**
     * Attaches to the given transport, starts it, and starts listening for messages.
     *
     * The Protocol object assumes ownership of the Transport, replacing any callbacks that have
     * already been set, and expects that it is the only user of the Transport instance going forward.
     *
     * @throws IllegalStateException if this Protocol is already connected; call [close] first.
     */
    public open suspend fun connect(transport: Transport) {
        val maxConcurrent = options?.maxConcurrentHandlers ?: DEFAULT_MAX_CONCURRENT_HANDLERS
        val maxInFlight = options?.maxInFlightHandlers ?: DEFAULT_MAX_IN_FLIGHT_HANDLERS
        require(maxConcurrent > 0) { "maxConcurrentHandlers must be positive, but was $maxConcurrent" }
        require(maxInFlight >= maxConcurrent) {
            "maxInFlightHandlers ($maxInFlight) must be >= maxConcurrentHandlers ($maxConcurrent)"
        }
        val handlerContext = (options?.handlerCoroutineContext ?: Dispatchers.Default).minusKey(Job)
        val interceptor = handlerContext[ContinuationInterceptor]
        if (interceptor === Dispatchers.Unconfined ||
            interceptor?.let { it::class.simpleName?.contains("Unconfined") } == true
        ) {
            logger.warn {
                "handlerCoroutineContext uses an unconfined dispatcher ($interceptor); " +
                    "handler resumptions will run on the transport read loop and may block unrelated messages"
            }
        }
        val connection = Connection(
            transport = transport,
            handlerScope = CoroutineScope(SupervisorJob() + handlerContext + CoroutineName("McpProtocol")),
            executionSemaphore = Semaphore(maxConcurrent),
            maxInFlightHandlers = maxInFlight,
        )
        if (!connectionRef.compareAndSet(null, connection)) {
            connection.handlerScope.cancel()
            error("Protocol is already connected; close() the current connection before connecting a new transport")
        }

        transport.onClose {
            doClose(connection)
        }

        transport.onError {
            onError(it)
        }

        transport.onMessage { message ->
            if (connectionRef.value !== connection) {
                logger.trace { "Dropping message received after close: ${message::class.simpleName}" }
                return@onMessage
            }
            when (message) {
                is JSONRPCResponse -> onResponse(message, null)
                is JSONRPCRequest -> dispatchRequest(connection, message)
                is JSONRPCNotification -> dispatchNotification(connection, message)
                is JSONRPCError -> onResponse(null, message)
                is JSONRPCEmptyMessage -> Unit
            }
        }

        logger.info { "Starting transport" }
        try {
            transport.start()
        } catch (cause: Throwable) {
            // Roll back so the Protocol can be reconnected after a failed start; idempotent with
            // a transport-initiated doClose (both CAS on the same connection).
            if (connectionRef.compareAndSet(connection, null)) {
                connection.handlerScope.cancel()
            }
            throw cause
        }
    }

    private fun doClose(connection: Connection) {
        // A stale onClose from a previous transport must not tear down the successor connection.
        if (!connectionRef.compareAndSet(connection, null)) {
            logger.trace { "Ignoring close signal from a stale transport" }
            return
        }
        val handlersToNotify = _responseHandlers.getAndSet(persistentMapOf()).values.toList()
        _progressHandlers.getAndSet(persistentMapOf())
        recentlyCancelledRequestIds.getAndSet(persistentListOf())
        connection.inFlightRequestJobs.getAndSet(persistentMapOf())
        connection.handlerScope.cancel()
        onClose()

        val error = McpException(RPCError.ErrorCode.CONNECTION_CLOSED, "Connection closed")
        for (handler in handlersToNotify) {
            handler(null, error)
        }
    }

    private suspend fun dispatchRequest(connection: Connection, request: JSONRPCRequest) {
        if (!connection.concurrentDispatchEnabled.value) {
            // Serial phase (before the peer completes MCP initialization): inline, in arrival
            // order — the delivering coroutine awaits the handler.
            onRequest(request, connection, trackCancellation = false)
            return
        }
        val bypass = request.method in CONTROL_METHODS
        if (!bypass && !tryAdmit(connection)) {
            rejectOverloadedRequest(connection, request)
            return
        }
        // UNDISPATCHED starts the handler synchronously in the delivering coroutine, preserving
        // arrival order, then moves to handlerCoroutineContext at its first suspension.
        connection.handlerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Register before any suspension point so a following notifications/cancelled can find
            // and cancel even a handler still parked on admission.
            registerInFlight(connection, request.id, coroutineContext.job)
            try {
                if (bypass) {
                    onRequest(request, connection, trackCancellation = true)
                } else {
                    connection.executionSemaphore.withPermit {
                        onRequest(request, connection, trackCancellation = true)
                    }
                }
            } finally {
                if (!bypass) connection.inFlightCount.decrementAndGet()
            }
        }
    }

    private suspend fun dispatchNotification(connection: Connection, notification: JSONRPCNotification) {
        if (notification.method == Method.Defined.NotificationsInitialized.value) {
            onInitializedNotification()
        }
        onNotificationRouted(notification)
        if (!connection.concurrentDispatchEnabled.value) {
            onNotification(notification)
            return
        }
        val bypass = notification.method in CONTROL_METHODS
        if (!bypass && !tryAdmit(connection)) {
            val dropped = IllegalStateException(
                "Dropped notification ${notification.method}: too many in-flight messages",
            )
            logger.warn { dropped.message }
            onError(dropped)
            return
        }
        connection.handlerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (bypass) {
                    onNotification(notification)
                } else {
                    connection.executionSemaphore.withPermit { onNotification(notification) }
                }
            } finally {
                if (!bypass) connection.inFlightCount.decrementAndGet()
            }
        }
    }

    private fun tryAdmit(connection: Connection): Boolean {
        while (true) {
            val current = connection.inFlightCount.value
            if (current >= connection.maxInFlightHandlers) return false
            if (connection.inFlightCount.compareAndSet(current, current + 1)) return true
        }
    }

    private suspend fun rejectOverloadedRequest(connection: Connection, request: JSONRPCRequest) {
        logger.warn {
            "Rejecting request ${request.method} (id: ${request.id}): " +
                "in-flight handler limit (${connection.maxInFlightHandlers}) reached"
        }
        try {
            connection.transport.send(
                JSONRPCError(
                    id = request.id,
                    error = RPCError(
                        code = RPCError.ErrorCode.INTERNAL_ERROR,
                        message = "Server is busy: too many in-flight messages",
                    ),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (cause: Throwable) {
            logger.error(cause) { "Failed to send busy rejection for request id: ${request.id}" }
            onError(cause)
        }
    }

    private fun handleCancelledNotification(notification: CancelledNotification) {
        val connection = connectionRef.value ?: return
        val requestId = notification.params.requestId
        val job = connection.inFlightRequestJobs.value[requestId]
        if (job == null) {
            logger.trace { "Ignoring cancellation for unknown or already-completed request: $requestId" }
            return
        }
        job.cancel("Cancelled by peer: ${notification.params.reason ?: "unknown"}")
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
        } catch (e: CancellationException) {
            throw e
        } catch (cause: Throwable) {
            logger.error(cause) { "Error handling notification: ${notification.method}" }
            onError(cause)
        }
    }

    private suspend fun onRequest(request: JSONRPCRequest, connection: Connection, trackCancellation: Boolean) {
        logger.trace { "Received request: ${request.method} (id: ${request.id})" }

        val capturedTransport = connection.transport
        val meta = request.requestMeta()
        val era = eraOf(meta)
        val method = Method.from(request.method)

        admissionError(era, method, meta)?.let { error ->
            sendError(capturedTransport, request.id, error)
            return
        }

        val handler = requestHandlers[request.method] ?: fallbackRequestHandler
        if (handler === null) {
            logger.trace { "No handler found for request: ${request.method}" }
            sendError(
                capturedTransport,
                request.id,
                RPCError(
                    code = RPCError.ErrorCode.METHOD_NOT_FOUND,
                    message = "Server does not support ${request.method}",
                ),
            )
            return
        }

        val extra = RequestHandlerExtra(
            requestId = request.id,
            method = method,
            protocol = this,
            capturedTransport = capturedTransport,
            era = era,
            meta = meta,
            envelope = if (era == ProtocolEra.Modern) meta?.toEnvelopeLenient() else null,
            handshakeProtocolVersion = negotiatedProtocolVersion,
            handshakeClientCapabilities = declaredClientCapabilities,
            handshakeClientInfo = declaredClientInfo,
        )

        // Registration in the in-flight registry already happened in dispatchRequest's launch
        // block (synchronously, pre-suspension). Here we only need the job for suppression checks.
        val handlerJob = if (trackCancellation) currentCoroutineContext().job else null

        withContext(extra) {
            try {
                val result = handler(request, extra)
                logger.trace { "Request handled successfully: ${request.method} (id: ${request.id})" }
                if (handlerJob?.isCancelled == true) {
                    logger.trace { "Suppressing response for request cancelled by peer (id: ${request.id})" }
                    return@withContext
                }
                capturedTransport.send(
                    JSONRPCResponse(
                        id = request.id,
                        result = encodeResultForEra(era, result),
                    ),
                )
            } catch (e: CancellationException) {
                // Intentional exception to the always-rethrow-CE convention, only at this boundary.
                if (handlerJob == null) throw e // serial phase: inline dispatch is not response-suppressed
                if (handlerJob.isCancelled) throw e // genuine peer or close cancel: suppress the response
                // CE escaped a live handler job (e.g. a leaked inner withTimeout). Answer
                // INTERNAL_ERROR so the peer does not hang until its own timeout.
                respondWithError(capturedTransport, request, e, era)
            } catch (cause: Throwable) {
                if (handlerJob?.isCancelled == true) {
                    logger.trace { "Suppressing error response for request cancelled by peer (id: ${request.id})" }
                    return@withContext
                }
                respondWithError(capturedTransport, request, cause, era)
            }
        }
    }

    /**
     * The identity to stamp into outgoing results, or `null` to stamp none.
     *
     * Only servers report one, and only under the request-scoped lifecycle.
     */
    protected open val outboundServerInfo: Implementation? get() = null

    /**
     * The protocol version this connection settled on, or `null` before it has settled on one.
     *
     * Settled by the `initialize` handshake or by `server/discover`, whichever the peer turned out
     * to speak. It decides the lifecycle of everything this peer sends.
     */
    protected open val negotiatedProtocolVersion: String? get() = null

    /**
     * The capabilities the peer declared for this connection, or `null` when it declared none.
     *
     * Only the connection-scoped lifecycle has such a declaration.
     */
    protected open val declaredClientCapabilities: ClientCapabilities? get() = null

    /** The identity the peer reported for this connection, or `null` when it reported none. */
    protected open val declaredClientInfo: Implementation? get() = null

    /**
     * The lifecycle this connection sends under, decided by what it settled on rather than by
     * whatever it happens to be answering.
     */
    private val outboundEra: ProtocolEra
        get() = if (negotiatedProtocolVersion?.let(::isModernProtocolVersion) == true) {
            ProtocolEra.Modern
        } else {
            ProtocolEra.Legacy
        }

    /**
     * Last chance to rewrite an outbound request before it is sent; a client attaches its
     * per-request protocol envelope here. The default returns [request] untouched.
     */
    protected open fun decorateOutboundRequest(request: JSONRPCRequest): JSONRPCRequest = request

    /**
     * The first rung of the inbound validation ladder [request] fails, or `null` when it passes all
     * of them.
     *
     * Order is the precedence, and a request failing several rungs is answered by the earliest:
     *
     * 1. the envelope behind a present claim is intact — `-32602`, naming every offending key at
     *    once so a peer is not corrected one round trip at a time;
     * 2. the claimed version is one this SDK serves — `-32022`, naming the ones it does;
     * 3. the method exists in the lifecycle being served — `-32601`, **even where a handler is
     *    registered**, because a revision that removes a method removes it structurally.
     *
     * Rung 1 covers a non-string version too: that is a defect of shape rather than an outcome of
     * negotiation, and the distinction matters, because a negotiating client falls back from
     * `-32602` but not from `-32022`.
     *
     * Transports that carry headers add their own rungs in front of this one; nothing here depends
     * on having them.
     */
    @OptIn(ExperimentalMcpApi::class)
    private fun admissionError(era: ProtocolEra, method: Method, meta: RequestMeta?): RPCError? {
        if (era == ProtocolEra.Modern) {
            val issues = validateEnvelope(checkNotNull(meta) { "a modern claim implies request metadata" })
            if (issues.isNotEmpty()) {
                return RPCError(
                    code = RPCError.ErrorCode.INVALID_PARAMS,
                    message = "Invalid request envelope: " +
                        issues.joinToString(", ") { "${it.key} ${it.problem}" },
                )
            }
            val claimed = checkNotNull(meta.claimedProtocolVersion) { "an intact envelope implies a string version" }
            if (claimed !in MODERN_PROTOCOL_VERSIONS) {
                return RPCError(
                    code = RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION,
                    message = "Unsupported protocol version: $claimed",
                    data = McpJson.encodeToJsonElement(
                        UnsupportedProtocolVersionData(supported = MODERN_PROTOCOL_VERSIONS, requested = claimed),
                    ),
                )
            }
        }
        if (!method.isAvailableIn(era)) {
            return RPCError(
                code = RPCError.ErrorCode.METHOD_NOT_FOUND,
                message = "Method ${method.value} does not exist in protocol version " +
                    "${negotiatedVersionFor(era, meta)}",
            )
        }
        return null
    }

    /** The version to name when telling a peer a method does not exist in the lifecycle it asked under. */
    @OptIn(ExperimentalMcpApi::class)
    private fun negotiatedVersionFor(era: ProtocolEra, meta: RequestMeta?): String = when (era) {
        ProtocolEra.Modern -> meta?.claimedProtocolVersion ?: LATEST_MODERN_VERSION
        ProtocolEra.Legacy -> negotiatedProtocolVersion ?: DEFAULT_NEGOTIATED_PROTOCOL_VERSION
    }

    /**
     * Renders [result] for the wire, applying everything that differs between the two lifecycles.
     *
     * A handler returning `null` means "nothing to report", which is an empty result — spelled with
     * an explicit `resultType` on the request-scoped lifecycle and as a bare `{}` on the one that
     * predates the field.
     */
    @OptIn(InternalMcpApi::class)
    private fun encodeResultForEra(era: ProtocolEra, result: RequestResult?): RequestResult {
        val payload = result ?: EmptyResult(
            resultType = COMPLETE_RESULT_TYPE.takeIf { era == ProtocolEra.Modern },
        )
        val json = McpJson.encodeToJsonElement<RequestResult>(payload).jsonObject
        return WireResult(encodeOutboundResult(era, json, outboundServerInfo))
    }

    /**
     * Reads an inbound response back into a typed result.
     *
     * A transport that serializes has already done this. One that does not — an in-memory transport
     * — hands over whatever object the peer passed to `send`, which may be a [WireResult] holding
     * rendered JSON; reading it here makes both kinds of transport behave the same.
     */
    @OptIn(InternalMcpApi::class)
    private fun decodeInboundResponse(response: JSONRPCResponse): JSONRPCResponse {
        val result = response.result
        val decoded = if (result is WireResult) {
            response.copy(result = McpJson.decodeFromJsonElement<RequestResult>(result.json))
        } else {
            response
        }
        checkInboundResult(outboundEra, decoded.result)
        return decoded
    }

    /**
     * Answers [id] with [error]. A send failure is reported to [onError] rather than thrown: a peer
     * that cannot be told why its request was refused is already unreachable.
     */
    private suspend fun sendError(transport: Transport, id: RequestId, error: RPCError) {
        try {
            transport.send(JSONRPCError(id = id, error = error))
        } catch (e: CancellationException) {
            throw e
        } catch (cause: Throwable) {
            logger.error(cause) { "Error sending error response for request id: $id" }
            onError(cause)
        }
    }

    private fun registerInFlight(connection: Connection, id: RequestId, job: Job) {
        val previous = connection.inFlightRequestJobs.getAndUpdate { current -> current.putting(id, job) }[id]
        if (previous != null) {
            logger.warn { "Duplicate in-flight request id $id; replacing the previous handler job" }
        }
        job.invokeOnCompletion {
            // Identity guard: never evict a successor job registered under a reused id.
            connection.inFlightRequestJobs.update { current ->
                if (current[id] === job) current.removing(id) else current
            }
        }
    }

    private suspend fun respondWithError(
        transport: Transport,
        request: JSONRPCRequest,
        cause: Throwable,
        era: ProtocolEra,
    ) {
        logger.error(cause) { "Error handling request: ${request.method} (id: ${request.id})" }
        try {
            val rpcError = if (cause is McpException) {
                RPCError(
                    code = outboundErrorCode(era, cause.code),
                    message = cause.message.orEmpty(),
                    data = cause.data,
                )
            } else {
                RPCError(code = RPCError.ErrorCode.INTERNAL_ERROR, message = cause.message ?: "Internal error")
            }
            transport.send(JSONRPCError(id = request.id, error = rpcError))
        } catch (e: CancellationException) {
            throw e
        } catch (sendError: Throwable) {
            logger.error(sendError) {
                "Failed to send error response for request: ${request.method} (id: ${request.id})"
            }
            onError(sendError)
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
            if (isRecentlyCancelled(progressToken)) {
                logger.trace { "Ignoring progress for a locally cancelled request: $progressToken" }
                return
            }
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
                current.removing(messageId)
            } else {
                current
            }
        }

        val handler = oldResponseHandlers[messageId]

        if (handler != null) {
            messageId?.let { msg -> _progressHandlers.update { it.removing(msg) } }
        } else {
            if (isRecentlyCancelled(messageId)) {
                logger.trace { "Ignoring response for a locally cancelled request: $messageId" }
                return
            }
            onError(
                IllegalStateException(
                    "Received a response for an unknown message ID: ${McpJson.encodeToString(error ?: response)}",
                ),
            )
            return
        }

        if (response != null) {
            val decoded = try {
                decodeInboundResponse(response)
            } catch (cause: McpException) {
                // A result this SDK cannot interpret is not an answer: failing the caller beats
                // handing it a partial result as though it were the whole one.
                handler(null, cause)
                return
            }
            handler(decoded, null)
        } else {
            checkNotNull(error)
            val mcpException = McpException.fromError(
                code = error.error.code,
                message = error.error.message,
                data = error.error.data,
            )
            handler(null, mcpException)
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
        val connection = connectionRef.value ?: error("Not connected")
        val transport = connection.transport

        if (this@Protocol.options?.enforceStrictCapabilities == true) {
            assertCapabilityForMethod(request.method)
        }

        if (!request.method.isAvailableIn(outboundEra)) {
            // Refused here rather than on the wire: a method the negotiated revision removed has no
            // answer a peer could give, so failing locally reports the real cause instead of a
            // method-not-found the caller has to interpret.
            throw McpException(
                code = RPCError.ErrorCode.METHOD_NOT_FOUND,
                message = "Method ${request.method.value} does not exist in protocol version " +
                    "${negotiatedProtocolVersion ?: DEFAULT_NEGOTIATED_PROTOCOL_VERSION}",
            )
        }

        val withProgressToken = request.toJSON().run {
            options?.onProgress?.let { progressHandler ->
                logger.trace { "Registering progress handler for request id: $id" }
                _progressHandlers.update { current ->
                    current.putting(id, progressHandler)
                }

                val paramsObject = (this.params as? JsonObject) ?: JsonObject(emptyMap())
                val metaObject = request.params?.meta?.json ?: JsonObject(emptyMap())

                val updatedMeta = JsonObject(
                    metaObject + ("progressToken" to McpJson.encodeToJsonElement(id)),
                )
                val updatedParams = JsonObject(
                    paramsObject + ("_meta" to updatedMeta),
                )

                this.copy(params = updatedParams)
            } ?: this
        }
        // Decorated after the progress token is in place so a peer attaching a protocol envelope
        // merges onto the final `_meta` rather than one that is about to be replaced.
        val jsonRpcRequest = decorateOutboundRequest(withProgressToken)
        val jsonRpcRequestId = jsonRpcRequest.id

        _responseHandlers.update { current ->
            current.putting(jsonRpcRequestId) { response, error ->
                if (error != null) {
                    result.completeExceptionally(error)
                    return@putting
                }

                try {
                    @Suppress("UNCHECKED_CAST")
                    result.complete(response!!.result as T)
                } catch (e: Throwable) {
                    result.completeExceptionally(e)
                }
            }
        }

        if (connectionRef.value !== connection) {
            // Connection closed or replaced during registration; nothing would complete this handler.
            _responseHandlers.update { it.removing(jsonRpcRequestId) }
            _progressHandlers.update { it.removing(jsonRpcRequestId) }
            throw McpException(RPCError.ErrorCode.CONNECTION_CLOSED, "Connection closed")
        }

        val cancelPending: suspend (reason: Throwable, notifyPeer: Boolean) -> Unit = { reason, notifyPeer ->
            // Remember before removing: a racing onResponse/onProgress that sees the handler gone
            // must also see the id, or it reports a late message as unknown. Order is load-bearing.
            rememberCancelledRequestId(jsonRpcRequestId)
            _responseHandlers.update { current -> current.removing(jsonRpcRequestId) }
            _progressHandlers.update { current -> current.removing(jsonRpcRequestId) }

            if (notifyPeer) {
                val notification = CancelledNotification(
                    params = CancelledNotificationParams(
                        requestId = jsonRpcRequestId,
                        reason = reason.message ?: "Unknown",
                    ),
                )
                transport.send(notification.toJSON(), options)
            }

            result.completeExceptionally(reason)
        }

        // The MCP spec forbids cancelling `initialize`; local cleanup still runs,
        // but no notifications/cancelled goes on the wire (timeout and cancel paths alike).
        val notifyPeerOnCancel = request.method != Method.Defined.Initialize

        val timeout = options?.timeout ?: DEFAULT_REQUEST_TIMEOUT
        try {
            val response = withTimeoutOrNull(timeout) {
                logger.trace { "Sending request message with id: $jsonRpcRequestId" }
                transport.send(jsonRpcRequest, options)
                result.await()
            }
            if (response == null) {
                // Our own request timeout expired. An outer withTimeout's cancellation propagates
                // through withTimeoutOrNull instead of returning null and is handled by the catch below.
                logger.error { "Request timed out after ${timeout.inWholeMilliseconds}ms: ${request.method}" }
                val timeoutError = McpException(
                    code = RPCError.ErrorCode.REQUEST_TIMEOUT,
                    message = "Request timed out",
                    data = JsonObject(mutableMapOf("timeout" to JsonPrimitive(timeout.inWholeMilliseconds))),
                )
                withContext(NonCancellable) {
                    try {
                        cancelPending(timeoutError, notifyPeerOnCancel)
                    } catch (e: Throwable) {
                        logger.warn(e) { "Failed to notify peer about timed-out request" }
                        onError(e)
                    }
                }
                result.cancel()
                throw timeoutError
            }
            return response
        } catch (cause: CancellationException) {
            // Plain caller cancellation, or an outer withTimeout that propagated through
            // withTimeoutOrNull (only our own deadline returns null there). NonCancellable so the
            // cancellation notification still goes out even though the caller's job is cancelled.
            // A secondary send failure must never replace the original CancellationException.
            withContext(NonCancellable) {
                try {
                    cancelPending(cause, notifyPeerOnCancel)
                } catch (e: Throwable) {
                    logger.warn(e) { "Failed to notify peer about cancelled request" }
                    onError(e)
                }
            }
            throw cause
        } finally {
            // Backstop for the failed-send path. The success, timeout and cancellation paths already
            // removed these handlers, so this only bites when the outbound send failed.
            _responseHandlers.update { it.removing(jsonRpcRequestId) }
            _progressHandlers.update { it.removing(jsonRpcRequestId) }
        }
    }

    /**
     * Emits a notification, which is a one-way message that does not expect a response.
     */
    public suspend fun notification(notification: Notification, relatedRequestId: RequestId? = null) {
        logger.trace { "Sending notification: ${notification.method}" }
        val transport = this.transport ?: error("Not connected")
        assertNotificationCapability(notification.method)
        val sendOptions = relatedRequestId?.let { TransportSendOptions(relatedRequestId = it) }
        val jsonRpcNotification = notification.toJSON()

        transport.send(jsonRpcNotification, sendOptions)
    }

    /**
     * Registers a handler to invoke when this protocol object receives a request with the given method.
     *
     * Note that this will replace any previous request handler for the same method.
     *
     * Handlers may run concurrently after initialization and are cancelled cooperatively, so always
     * re-throw [CancellationException]. Replacing a built-in control-method handler (`ping`,
     * `notifications/cancelled`, `notifications/progress`, `notifications/initialized`) with slow
     * code degrades connection liveness.
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
        val wrapped = wrapRequestHandler(method, block)

        _requestHandlers.update { current ->
            current.putting(method.value) { jSONRPCRequest, extraHandler ->
                val request = try {
                    jSONRPCRequest.fromJSON()
                } catch (cause: SerializationException) {
                    throw McpException(
                        code = RPCError.ErrorCode.INVALID_PARAMS,
                        message = "Invalid params",
                        cause = cause,
                    )
                }
                val response = wrapped(request as T, extraHandler)
                response
            }
        }
    }

    /**
     * Subclass hook to wrap an incoming-request handler before it is registered.
     *
     * Called once by [setRequestHandler] during registration. Subclasses may return a
     * new function that, when invoked, performs additional checks (capability gates,
     * schema validation, etc.) before delegating to [block]. The default implementation
     * is the identity.
     */
    @Suppress("UNUSED_PARAMETER")
    protected open fun <T : Request> wrapRequestHandler(
        method: Method,
        block: suspend (T, RequestHandlerExtra) -> RequestResult?,
    ): suspend (T, RequestHandlerExtra) -> RequestResult? = block

    /**
     * Removes the request handler for the given method.
     */
    public fun removeRequestHandler(method: Method) {
        _requestHandlers.update { current -> current.removing(method.value) }
    }

    /**
     * Registers a handler to invoke when this protocol object receives a notification with the given method.
     *
     * Note that this will replace any previous notification handler for the same method.
     *
     * Handlers may run concurrently after initialization and are cancelled cooperatively, so always
     * re-throw [CancellationException]. Replacing a built-in control-method handler (`ping`,
     * `notifications/cancelled`, `notifications/progress`, `notifications/initialized`) with slow
     * code degrades connection liveness. Replacing `notifications/cancelled` in particular disables
     * inbound request cancellation entirely, since that handler is what cancels the running job.
     */
    public fun <T : Notification> setNotificationHandler(method: Method, handler: (notification: T) -> Deferred<Unit>) {
        _notificationHandlers.update { current ->
            current.putting(method.value) {
                @Suppress("UNCHECKED_CAST")
                handler(it.fromJSON() as T)
            }
        }
    }

    /**
     * Removes the notification handler for the given method.
     */
    public fun removeNotificationHandler(method: Method) {
        _notificationHandlers.update { current -> current.removing(method.value) }
    }

    private class Connection(
        val transport: Transport,
        val handlerScope: CoroutineScope,
        val executionSemaphore: Semaphore,
        val maxInFlightHandlers: Int,
    ) {
        /** Init gate: inbound dispatch is inline/serial until this flips. */
        val concurrentDispatchEnabled = atomic(false)

        /** Admission tier: launched-but-not-completed handler jobs (running + parked). */
        val inFlightCount = atomic(0)

        /** In-flight request handler jobs, for `notifications/cancelled`. */
        val inFlightRequestJobs: AtomicRef<PersistentMap<RequestId, Job>> = atomic(persistentMapOf())
    }
}
