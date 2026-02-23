package io.modelcontextprotocol.kotlin.sdk.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondNullable
import io.ktor.server.sse.ServerSSESession
import io.ktor.util.collections.ConcurrentMap
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.DEFAULT_NEGOTIATED_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCEmptyMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.REQUEST_TIMEOUT
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.SUPPORTED_PROTOCOL_VERSIONS
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal const val MCP_SESSION_ID_HEADER = "mcp-session-id"
private const val MCP_PROTOCOL_VERSION_HEADER = "mcp-protocol-version"
private const val MCP_RESUMPTION_TOKEN_HEADER = "Last-Event-ID"
private const val MAXIMUM_MESSAGE_SIZE = 4 * 1024 * 1024 // 4 MB

/**
 * A holder for an active request call.
 * If [StreamableHttpServerTransport.Configuration.enableJsonResponse] is true, the session is null.
 * Otherwise, the session is not null.
 */
private data class SessionContext(val session: ServerSSESession?, val call: ApplicationCall)

/**
 * Server transport for Streamable HTTP: this implements the MCP Streamable HTTP transport specification.
 * It supports both SSE streaming and direct HTTP responses.
 *
 * In stateful mode:
 * - Session ID is generated and included in response headers
 * - Session ID is always included in initialization responses
 * - Requests with invalid session IDs are rejected with 404 Not Found
 * - Non-initialization requests without a session ID are rejected with 400 Bad Request
 * - State is maintained in-memory (connections, message history)
 *
 * In stateless mode:
 * - No Session ID is included in any responses
 * - No session validation is performed
 *
 * @param configuration Transport configuration. See [Configuration] for available options.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalAtomicApi::class)
@Suppress("TooManyFunctions")
public class StreamableHttpServerTransport(private val configuration: Configuration) : AbstractTransport() {

    /**
     * Secondary constructor for `StreamableHttpServerTransport` that simplifies initialization by directly taking the
     * configurable parameters without requiring a `Configuration` instance.
     *
     * @param enableJsonResponse Determines whether the server should return JSON responses.
     *          Defaults to `false`.
     * @param enableDnsRebindingProtection Enables DNS rebinding protection.
     *          Defaults to `false`.
     * @param allowedHosts A list of hosts allowed for server communication.
     *          Defaults to `null`, allowing all hosts.
     * @param allowedOrigins A list of allowed origins for CORS (Cross-Origin Resource Sharing).
     *          Defaults to `null`, allowing all origins.
     * @param eventStore The `EventStore` instance for handling resumable events.
     *          Defaults to `null`, disabling resumability.
     * @param retryIntervalMillis Retry interval in milliseconds for event handling or reconnection attempts.
     *          Defaults to `null`.
     */
    @Deprecated(
        "Use constructor with Configuration: StreamableHttpServerTransport(Configuration(enableJsonResponse = ...))",
        level = DeprecationLevel.WARNING,
    )
    public constructor(
        enableJsonResponse: Boolean = false,
        enableDnsRebindingProtection: Boolean = false,
        allowedHosts: List<String>? = null,
        allowedOrigins: List<String>? = null,
        eventStore: EventStore? = null,
        retryIntervalMillis: Long? = null,
    ) : this(
        Configuration(
            enableJsonResponse = enableJsonResponse,
            enableDnsRebindingProtection = enableDnsRebindingProtection,
            allowedHosts = allowedHosts,
            allowedOrigins = allowedOrigins,
            eventStore = eventStore,
            retryInterval = retryIntervalMillis?.milliseconds,
        ),
    )

    /**
     * Configuration for managing various aspects of the StreamableHttpServerTransport.
     *
     * @property enableJsonResponse Determines whether the server should return JSON responses.
     *              Defaults to `false`.
     *
     * @property enableDnsRebindingProtection Enables DNS rebinding protection.
     *              Defaults to `false`.
     *
     * @property allowedHosts A list of hosts allowed for server communication.
     *              Defaults to `null`, allowing all hosts.
     *
     * @property allowedOrigins A list of allowed origins for CORS (Cross-Origin Resource Sharing).
     *              Defaults to `null`, allowing all origins.
     *
     * @property eventStore The `EventStore` instance for handling resumable events.
     *              Defaults to `null`, disabling resumability.
     *
     * @property retryInterval Retry interval for event handling or reconnection attempts.
     *              Defaults to `null`.
     */
    public class Configuration(
        public val enableJsonResponse: Boolean = false,
        public val enableDnsRebindingProtection: Boolean = false,
        public val allowedHosts: List<String>? = null,
        public val allowedOrigins: List<String>? = null,
        public val eventStore: EventStore? = null,
        public val retryInterval: Duration? = null,
    )

    public var sessionId: String? = null
        private set

    private var sessionIdGenerator: (() -> String)? = { Uuid.random().toString() }
    private var onSessionInitialized: ((sessionId: String) -> Unit)? = null
    private var onSessionClosed: ((sessionId: String) -> Unit)? = null

    private val started: AtomicBoolean = AtomicBoolean(false)
    private val initialized: AtomicBoolean = AtomicBoolean(false)

    private val streamsMapping: ConcurrentMap<String, SessionContext> = ConcurrentMap()
    private val requestToStreamMapping: ConcurrentMap<RequestId, String> = ConcurrentMap()
    private val requestToResponseMapping: ConcurrentMap<RequestId, JSONRPCMessage> = ConcurrentMap()

    private val sessionMutex = Mutex()
    private val streamMutex = Mutex()

    private companion object {
        const val STANDALONE_SSE_STREAM_ID = "_GET_stream"
    }

    /**
     * Function that generates a session ID for the transport.
     * The session ID SHOULD be globally unique and cryptographically secure
     * (e.g., a securely generated UUID, a JWT, or a cryptographic hash)
     *
     * Set undefined to disable session management.
     */
    public fun setSessionIdGenerator(block: (() -> String)?) {
        sessionIdGenerator = block
    }

    /**
     * A callback for session initialization events
     * This is called when the server initializes a new session.
     * Useful in cases when you need to register multiple mcp sessions
     * and need to keep track of them.
     */
    public fun setOnSessionInitialized(block: ((String) -> Unit)?) {
        onSessionInitialized = block
    }

    /**
     * A callback for session close events
     * This is called when the server closes a session due to a DELETE request.
     * Useful in cases when you need to clean up resources associated with the session.
     * Note that this is different from the transport closing, if you are handling
     * HTTP requests from multiple nodes you might want to close each
     * StreamableHTTPServerTransport after a request is completed while still keeping the
     * session open/running.
     */
    public fun setOnSessionClosed(block: ((String) -> Unit)?) {
        onSessionClosed = block
    }

    override suspend fun start() {
        check(started.compareAndSet(expectedValue = false, newValue = true)) {
            "StreamableHttpServerTransport already started! If using Server class, " +
                "note that connect() calls start() automatically."
        }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        val responseRequestId: RequestId? = when (message) {
            is JSONRPCResponse -> message.id
            is JSONRPCError -> message.id
            else -> null
        }
        val routingRequestId = responseRequestId ?: options?.relatedRequestId

        // Standalone SSE stream
        if (routingRequestId == null) {
            require(message !is JSONRPCResponse && message !is JSONRPCError) {
                "Cannot send a response on a standalone SSE stream unless resuming a previous client request"
            }
            val standaloneStream = streamsMapping[STANDALONE_SSE_STREAM_ID] ?: return
            emitOnStream(STANDALONE_SSE_STREAM_ID, standaloneStream.session, message)
            return
        }

        val streamId = requestToStreamMapping[routingRequestId]
            ?: error("No connection established for request id $routingRequestId")
        val activeStream = streamsMapping[streamId]

        if (!configuration.enableJsonResponse) {
            activeStream?.let { stream ->
                emitOnStream(streamId, stream.session, message)
            }
        }

        val isTerminated = message is JSONRPCResponse || message is JSONRPCError
        if (!isTerminated) return

        requestToResponseMapping[responseRequestId!!] = message
        val relatedIds = requestToStreamMapping.filterValues { it == streamId }.keys

        if (relatedIds.any { it !in requestToResponseMapping }) return

        streamMutex.withLock {
            if (activeStream == null) error("No connection established for request ID: $routingRequestId")

            if (configuration.enableJsonResponse) {
                activeStream.call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                sessionId?.let { activeStream.call.response.header(MCP_SESSION_ID_HEADER, it) }
                val responses = relatedIds.mapNotNull { requestToResponseMapping[it] }
                val payload = if (responses.size == 1) {
                    responses.first()
                } else {
                    responses
                }
                activeStream.call.respond(payload)
            } else {
                activeStream.session?.close()
            }

            // Clean up
            relatedIds.forEach { requestId ->
                requestToResponseMapping.remove(requestId)
                requestToStreamMapping.remove(requestId)
            }
        }
    }

    override suspend fun close() {
        streamMutex.withLock {
            streamsMapping.values.forEach {
                try {
                    it.session?.close()
                } catch (_: Exception) {
                }
            }
            streamsMapping.clear()
            requestToStreamMapping.clear()
            requestToResponseMapping.clear()
            invokeOnCloseCallback()
        }
    }

    /**
     * Handles an incoming HTTP request, whether GET, POST or DELETE
     */
    public suspend fun handleRequest(session: ServerSSESession?, call: ApplicationCall) {
        validateHeaders(call)?.let { reason ->
            call.reject(HttpStatusCode.Forbidden, RPCError.ErrorCode.CONNECTION_CLOSED, reason)
            _onError(Error(reason))
            return
        }

        when (call.request.httpMethod) {
            HttpMethod.Post -> handlePostRequest(session, call)

            HttpMethod.Get -> handleGetRequest(session, call)

            HttpMethod.Delete -> handleDeleteRequest(call)

            else -> call.run {
                response.header(HttpHeaders.Allow, "GET, POST, DELETE")
                reject(HttpStatusCode.MethodNotAllowed, RPCError.ErrorCode.CONNECTION_CLOSED, "Method not allowed.")
            }
        }
    }

    /**
     * Handles POST requests containing JSON-RPC messages
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "TooGenericExceptionCaught")
    public suspend fun handlePostRequest(session: ServerSSESession?, call: ApplicationCall) {
        try {
            if (!configuration.enableJsonResponse && session == null) {
                error("Server session can't be null for SSE responses")
            }

            val acceptHeader = call.request.header(HttpHeaders.Accept)
            val isAcceptEventStream = acceptHeader.accepts(ContentType.Text.EventStream)
            val isAcceptJson = acceptHeader.accepts(ContentType.Application.Json)

            if (!isAcceptEventStream || !isAcceptJson) {
                call.reject(
                    HttpStatusCode.NotAcceptable,
                    RPCError.ErrorCode.CONNECTION_CLOSED,
                    "Not Acceptable: Client must accept both application/json and text/event-stream",
                )
                return
            }

            if (!call.request.contentType().match(ContentType.Application.Json)) {
                call.reject(
                    HttpStatusCode.UnsupportedMediaType,
                    RPCError.ErrorCode.CONNECTION_CLOSED,
                    "Unsupported Media Type: Content-Type must be application/json",
                )
                return
            }

            val messages = parseBody(call) ?: return
            val isInitializationRequest = messages.any {
                it is JSONRPCRequest && it.method == Method.Defined.Initialize.value
            }

            if (isInitializationRequest) {
                if (initialized.load() && sessionId != null) {
                    call.reject(
                        HttpStatusCode.BadRequest,
                        RPCError.ErrorCode.INVALID_REQUEST,
                        "Invalid Request: Server already initialized",
                    )
                    return
                }
                if (messages.size > 1) {
                    call.reject(
                        HttpStatusCode.BadRequest,
                        RPCError.ErrorCode.INVALID_REQUEST,
                        "Invalid Request: Only one initialization request is allowed",
                    )
                    return
                }

                sessionMutex.withLock {
                    if (sessionId != null) return@withLock
                    sessionId = sessionIdGenerator?.invoke()
                    initialized.store(true)
                    sessionId?.let { onSessionInitialized?.invoke(it) }
                }
            } else {
                if (!validateSession(call) || !validateProtocolVersion(call)) return
            }

            val hasRequest = messages.any { it is JSONRPCRequest }
            if (!hasRequest) {
                call.respondNullable(status = HttpStatusCode.Accepted, message = null)
                messages.forEach { message -> _onMessage(message) }
                return
            }

            val streamId = Uuid.random().toString()
            if (!configuration.enableJsonResponse) {
                call.appendSseHeaders()
                flushSse(session) // flush headers immediately
                maybeSendPrimingEvent(streamId, session)
            }

            streamMutex.withLock {
                streamsMapping[streamId] = SessionContext(session, call)
                messages.filterIsInstance<JSONRPCRequest>().forEach { requestToStreamMapping[it.id] = streamId }
            }
            call.coroutineContext.job.invokeOnCompletion { streamsMapping.remove(streamId) }

            messages.forEach { message -> _onMessage(message) }
        } catch (e: Exception) {
            call.reject(
                HttpStatusCode.BadRequest,
                RPCError.ErrorCode.PARSE_ERROR,
                "Parse error: ${e.message}",
            )
            _onError(e)
        }
    }

    @Suppress("ReturnCount")
    public suspend fun handleGetRequest(session: ServerSSESession?, call: ApplicationCall) {
        if (configuration.enableJsonResponse) {
            call.reject(
                HttpStatusCode.MethodNotAllowed,
                RPCError.ErrorCode.CONNECTION_CLOSED,
                "Method not allowed.",
            )
            return
        }
        val sseSession = session ?: error("Server session can't be null for streaming GET requests")

        val acceptHeader = call.request.header(HttpHeaders.Accept)
        if (!acceptHeader.accepts(ContentType.Text.EventStream)) {
            call.reject(
                HttpStatusCode.NotAcceptable,
                RPCError.ErrorCode.CONNECTION_CLOSED,
                "Not Acceptable: Client must accept text/event-stream",
            )
            return
        }

        if (!validateSession(call) || !validateProtocolVersion(call)) return

        configuration.eventStore?.let { store ->
            call.request.header(MCP_RESUMPTION_TOKEN_HEADER)?.let { lastEventId ->
                replayEvents(store, lastEventId, sseSession)
                return
            }
        }

        if (STANDALONE_SSE_STREAM_ID in streamsMapping) {
            call.reject(
                HttpStatusCode.Conflict,
                RPCError.ErrorCode.CONNECTION_CLOSED,
                "Conflict: Only one SSE stream is allowed per session",
            )
            return
        }

        call.appendSseHeaders()
        flushSse(sseSession) // flush headers immediately
        streamsMapping[STANDALONE_SSE_STREAM_ID] = SessionContext(sseSession, call)
        maybeSendPrimingEvent(STANDALONE_SSE_STREAM_ID, sseSession)
        sseSession.coroutineContext.job.invokeOnCompletion {
            streamsMapping.remove(STANDALONE_SSE_STREAM_ID)
        }
    }

    public suspend fun handleDeleteRequest(call: ApplicationCall) {
        if (!validateSession(call) || !validateProtocolVersion(call)) return
        sessionId?.let { onSessionClosed?.invoke(it) }
        close()
        call.respondNullable(status = HttpStatusCode.OK, message = null)
    }

    /**
     * Closes the SSE stream associated with the given [requestId], prompting the client to reconnect.
     * Useful for implementing polling behavior for long-running operations.
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    public suspend fun closeSseStream(requestId: RequestId) {
        if (configuration.enableJsonResponse) return
        val streamId = requestToStreamMapping[requestId] ?: return
        val sessionContext = streamsMapping[streamId] ?: return

        try {
            sessionContext.session?.close()
        } catch (e: Exception) {
            _onError(e)
        } finally {
            streamsMapping.remove(streamId)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun replayEvents(store: EventStore, lastEventId: String, session: ServerSSESession) {
        val call: ApplicationCall = session.call

        try {
            var lookupSupported = true
            val lookupStreamId = try {
                store.getStreamIdForEventId(lastEventId)
            } catch (_: NotImplementedError) {
                lookupSupported = false
                null
            } catch (_: UnsupportedOperationException) {
                lookupSupported = false
                null
            }

            if (lookupSupported) {
                val streamId = lookupStreamId
                    ?: run {
                        call.reject(
                            HttpStatusCode.BadRequest,
                            RPCError.ErrorCode.CONNECTION_CLOSED,
                            "Invalid event ID format",
                        )
                        return
                    }

                if (streamId in streamsMapping) {
                    call.reject(
                        HttpStatusCode.Conflict,
                        RPCError.ErrorCode.CONNECTION_CLOSED,
                        "Conflict: Stream already has an active connection",
                    )
                    return
                }
            }

            call.appendSseHeaders()
            flushSse(session) // flush headers immediately

            val streamId = store.replayEventsAfter(lastEventId) { eventId, message ->
                try {
                    session.send(
                        event = "message",
                        id = eventId,
                        data = McpJson.encodeToString(message),
                    )
                } catch (e: Exception) {
                    _onError(IllegalStateException("Failed to replay event: ${e.message}", e))
                }
            }

            streamsMapping[streamId] = SessionContext(session, call)

            session.coroutineContext.job.invokeOnCompletion { throwable ->
                streamsMapping.remove(streamId)
                throwable?.let { _onError(it) }
            }
        } catch (e: Exception) {
            _onError(e)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun validateSession(call: ApplicationCall): Boolean {
        if (sessionIdGenerator == null) return true

        if (!initialized.load()) {
            call.reject(
                HttpStatusCode.BadRequest,
                RPCError.ErrorCode.CONNECTION_CLOSED,
                "Bad Request: Server not initialized",
            )
            return false
        }

        val sessionHeaderValues = call.request.headers.getAll(MCP_SESSION_ID_HEADER)

        if (sessionHeaderValues.isNullOrEmpty()) {
            call.reject(
                HttpStatusCode.BadRequest,
                RPCError.ErrorCode.CONNECTION_CLOSED,
                "Bad Request: Mcp-Session-Id header is required",
            )
            return false
        }

        if (sessionHeaderValues.size > 1) {
            call.reject(
                HttpStatusCode.BadRequest,
                RPCError.ErrorCode.CONNECTION_CLOSED,
                "Bad Request: Mcp-Session-Id header must be a single value",
            )
            return false
        }

        val headerId = sessionHeaderValues.single()

        return when (headerId) {
            sessionId -> true

            else -> {
                call.reject(
                    HttpStatusCode.NotFound,
                    REQUEST_TIMEOUT,
                    "Session not found",
                )
                false
            }
        }
    }

    private suspend fun validateProtocolVersion(call: ApplicationCall): Boolean {
        val protocolVersions = call.request.headers.getAll(MCP_PROTOCOL_VERSION_HEADER)
        val version = protocolVersions?.lastOrNull() ?: DEFAULT_NEGOTIATED_PROTOCOL_VERSION

        return when (version) {
            !in SUPPORTED_PROTOCOL_VERSIONS -> {
                call.reject(
                    HttpStatusCode.BadRequest,
                    RPCError.ErrorCode.CONNECTION_CLOSED,
                    "Bad Request: Unsupported protocol version (supported versions: ${
                        SUPPORTED_PROTOCOL_VERSIONS.joinToString(
                            ", ",
                        )
                    })",
                )
                false
            }

            else -> true
        }
    }

    @Suppress("ReturnCount")
    private fun validateHeaders(call: ApplicationCall): String? {
        if (!configuration.enableDnsRebindingProtection) return null

        configuration.allowedHosts?.let { hosts ->
            val hostHeader = call.request.headers[HttpHeaders.Host]?.lowercase()
            val allowedHostsLowercase = hosts.map { it.lowercase() }

            if (hostHeader == null || hostHeader !in allowedHostsLowercase) {
                return "Invalid Host header: $hostHeader"
            }
        }

        configuration.allowedOrigins?.let { origins ->
            val originHeader = call.request.headers[HttpHeaders.Origin]?.lowercase()
            val allowedOriginsLowercase = origins.map { it.lowercase() }

            if (originHeader == null || originHeader !in allowedOriginsLowercase) {
                return "Invalid Origin header: $originHeader"
            }
        }

        return null
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun flushSse(session: ServerSSESession?) {
        try {
            session?.send(data = "")
        } catch (e: Exception) {
            _onError(e)
        }
    }

    @Suppress("ReturnCount", "MagicNumber")
    private suspend fun parseBody(call: ApplicationCall): List<JSONRPCMessage>? {
        val contentLength = call.request.header(HttpHeaders.ContentLength)?.toIntOrNull() ?: 0
        if (contentLength > MAXIMUM_MESSAGE_SIZE) {
            call.reject(
                HttpStatusCode.PayloadTooLarge,
                RPCError.ErrorCode.INVALID_REQUEST,
                "Invalid Request: message size exceeds maximum of ${MAXIMUM_MESSAGE_SIZE / (1024 * 1024)} MB",
            )
            return null
        }

        val body = call.receiveText()
        if (body.length > MAXIMUM_MESSAGE_SIZE) {
            call.reject(
                HttpStatusCode.PayloadTooLarge,
                RPCError.ErrorCode.INVALID_REQUEST,
                "Invalid Request: message size exceeds maximum of ${MAXIMUM_MESSAGE_SIZE / (1024 * 1024)} MB",
            )
            return null
        }

        return when (val element = McpJson.parseToJsonElement(body)) {
            is JsonObject -> listOf(McpJson.decodeFromJsonElement(element))

            is JsonArray -> McpJson.decodeFromJsonElement<List<JSONRPCMessage>>(element)

            else -> {
                call.reject(
                    HttpStatusCode.BadRequest,
                    RPCError.ErrorCode.INVALID_REQUEST,
                    "Invalid Request: unable to parse JSON body",
                )
                null
            }
        }
    }

    private fun String?.accepts(mime: ContentType): Boolean =
        this?.lowercase()?.contains(mime.toString().lowercase()) == true

    private suspend fun emitOnStream(streamId: String, session: ServerSSESession?, message: JSONRPCMessage) {
        val eventId = configuration.eventStore?.storeEvent(streamId, message)
        try {
            session?.send(event = "message", id = eventId, data = McpJson.encodeToString(message))
        } catch (_: Exception) {
            streamsMapping.remove(streamId)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun maybeSendPrimingEvent(streamId: String, session: ServerSSESession?) {
        val store = configuration.eventStore ?: return
        val sseSession = session ?: return
        try {
            val primingEventId = store.storeEvent(streamId, JSONRPCEmptyMessage)
            sseSession.send(
                id = primingEventId,
                retry = configuration.retryInterval?.inWholeMilliseconds,
                data = "",
            )
        } catch (e: Exception) {
            _onError(e)
        }
    }

    private fun ApplicationCall.appendSseHeaders() {
        this.response.headers.append(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
        this.response.headers.append(HttpHeaders.CacheControl, "no-cache, no-transform")
        this.response.headers.append(HttpHeaders.Connection, "keep-alive")
        sessionId?.let { this.response.headers.append(MCP_SESSION_ID_HEADER, it) }
        this.response.status(HttpStatusCode.OK)
    }
}

internal suspend fun ApplicationCall.reject(status: HttpStatusCode, code: Int, message: String) {
    this.response.status(status)
    this.respond(JSONRPCError(id = null, error = RPCError(code = code, message = message)))
}
