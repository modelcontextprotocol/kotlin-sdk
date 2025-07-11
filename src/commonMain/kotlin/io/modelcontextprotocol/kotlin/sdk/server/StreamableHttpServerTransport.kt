package io.modelcontextprotocol.kotlin.sdk.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.host
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondNullable
import io.ktor.server.sse.ServerSSESession
import io.ktor.util.collections.ConcurrentMap
import io.modelcontextprotocol.kotlin.sdk.ErrorCode
import io.modelcontextprotocol.kotlin.sdk.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.RequestId
import io.modelcontextprotocol.kotlin.sdk.SUPPORTED_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val MCP_SESSION_ID = "mcp-session-id"
private const val MCP_PROTOCOL_VERSION = "mcp-protocol-version"
private const val LAST_EVENT_ID = "Last-Event-ID"

/**
 * Interface for resumability support via event storage
 */
public interface EventStore {
    /**
     * Stores an event for later retrieval
     * @param streamId ID of the stream the event belongs to
     * @param message the JSON-RPC message to store
     * @return the generated event ID for the stored event
     */
    public suspend fun storeEvent(streamId: String, message: JSONRPCMessage): String

    /**
     * Replays events after the specified event ID
     * @param lastEventId The last event ID that was received
     * @param sender Function to send events
     * @return The stream ID for the replayed events
     */
    public suspend fun replayEventsAfter(
        lastEventId: String,
        sender: suspend (eventId: String, message: JSONRPCMessage) -> Unit
    ): String
}

/**
 * Simple holder for an active stream
 * SSE session and corresponding call
 */
private data class ActiveStream(val sse: ServerSSESession, val call: ApplicationCall)

/**
 * Server transport for StreamableHttp: this allows the server to respond to GET, POST and DELETE requests.
 * Server can optionally make use of Server-Sent Events (SSE) to stream multiple server messages.
 *
 * Creates a new StreamableHttp server transport.
 *
 * @param enableJsonResponse If true, the server will return JSON responses instead of starting an SSE stream.
 * This can be useful for simple request/response scenarios without streaming.
 * Default is false (SSE streams are preferred).
 * @param enableDnsRebindingProtection Enable DNS rebinding protection (requires allowedHosts and/or allowedOrigins to be configured).
 * Default is false for backwards compatibility.
 * @param allowedHosts List of allowed host header values for DNS rebinding protection.
 * If not specified, host validation is disabled.
 * @param allowedOrigins List of allowed origin header values for DNS rebinding protection.
 * If not specified, origin validation is disabled.
 * @param eventStore Event store for resumability support.
 * If provided, resumability will be enabled, allowing clients to reconnect and resume messages
 */
@OptIn(ExperimentalAtomicApi::class, ExperimentalUuidApi::class)
public class StreamableHttpServerTransport(
    private val enableJsonResponse: Boolean = false,
    private val enableDnsRebindingProtection: Boolean = false,
    private val allowedHosts: List<String>? = null,
    private val allowedOrigins: List<String>? = null,
    private val eventStore: EventStore? = null,
) : AbstractTransport() {
    private var onSessionInitialized: ((String) -> Unit)? = null
    private var sessionIdGenerator: (() -> String)? = { Uuid.random().toString() }

    private val streams: ConcurrentMap<String, ActiveStream> = ConcurrentMap()
    private val requestToStream: ConcurrentMap<RequestId, String> = ConcurrentMap()
    private val responses: ConcurrentMap<RequestId, JSONRPCMessage> = ConcurrentMap()

    private val started: AtomicBoolean = AtomicBoolean(false)
    private val initialized: AtomicBoolean = AtomicBoolean(false)

    private val sessionMutex = Mutex()
    private val streamMutex = Mutex()

    public var sessionId: String? = null
        private set

    /**
     * A callback for session initialization events
     * This is called when the server initializes a new session.
     * Useful in cases when you need to register multiple mcp sessions
     * and need to keep track of them.
     */
    public fun setSessionInitialized(block: ((String) -> Unit)?) {
        onSessionInitialized = block
    }

    /**
     * Function that generates a session ID for the transport.
     * The session ID SHOULD be globally unique and cryptographically secure
     * (e.g., a securely generated UUID)
     */
    public fun setSessionIdGenerator(block: (() -> String)?) {
        sessionIdGenerator = block
    }

    override suspend fun start() {
        check(started.compareAndSet(expectedValue = false, newValue = true)) {
            "StreamableHttpServerTransport already started! If using Server class, note that connect() calls start() automatically."
        }
    }

    override suspend fun send(message: JSONRPCMessage) {
        val requestId: RequestId? = when (message) {
            is JSONRPCResponse -> message.id
            is JSONRPCError -> message.id
            else -> null
        }

        // Standalone SSE stream
        if (requestId == null) {
            require(message !is JSONRPCResponse && message !is JSONRPCError) {
                "Cannot send a response on a standalone SSE stream unless resuming a previous client request"
            }
            val standaloneStream = streams[STANDALONE_SSE_STREAM_ID] ?: return
            emitOnStream(STANDALONE_SSE_STREAM_ID, standaloneStream, message)
            return
        }

        val streamId = requestToStream[requestId] ?: error("No connection established for request id $requestId")
        val activeStream = streams[streamId] ?: error("No connection established for request id $requestId")

        if (!enableJsonResponse) {
            emitOnStream(streamId, activeStream, message)
        }

        val isTerminal = message is JSONRPCResponse || message is JSONRPCError
        if (!isTerminal) return

        responses[requestId] = message
        val relatedIds = requestToStream.filterValues { it == streamId }.keys

        if (relatedIds.any { it !in responses }) return

        streamMutex.withLock {
            val active = streams[streamId] ?: return
            val call = active.call
            if (enableJsonResponse) {
                call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                sessionId?.let { call.response.header(MCP_SESSION_ID, it) }
                val payload = relatedIds.mapNotNull(responses::remove)
                call.respond(if (payload.size == 1) payload.first() else payload)
            } else {
                active.sse.close()
            }
            streams.remove(streamId)
            relatedIds.forEach { requestToStream -= it }
        }
    }

    override suspend fun close() {
        streamMutex.withLock {
            streams.values.forEach {
                try {
                    it.sse.close()
                } catch (_: Exception) {
                }
            }
            streams.clear()
            requestToStream.clear()
            responses.clear()
        }
        _onClose()
    }

    /**
     * Handles an incoming HTTP request: GET, POST or DELETE
     */
    public suspend fun handleRequest(call: ApplicationCall, session: ServerSSESession) {
        validateHeaders(call)?.let { reason ->
            call.reject(HttpStatusCode.Forbidden, ErrorCode.Unknown(-32000), reason)
            _onError(Error(reason))
            return
        }

        when (call.request.httpMethod) {
            HttpMethod.Post -> handlePostRequest(call, session)
            HttpMethod.Get -> handleGetRequest(call, session)
            HttpMethod.Delete -> handleDeleteRequest(call)
            else -> call.run {
                response.header(HttpHeaders.Allow, "GET, POST, DELETE")
                reject(HttpStatusCode.MethodNotAllowed, ErrorCode.Unknown(-32000), "Method not allowed.")
            }
        }
    }

    public suspend fun handlePostRequest(call: ApplicationCall, sse: ServerSSESession) {
        try {
            val acceptHeader = call.request.headers[HttpHeaders.Accept]
            val acceptsEventStream = acceptHeader.accepts(ContentType.Text.EventStream)
            val acceptsJson = acceptHeader.accepts(ContentType.Application.Json)

            if (!acceptsEventStream || !acceptsJson) {
                call.reject(
                    HttpStatusCode.NotAcceptable, ErrorCode.Unknown(-32000),
                    "Not Acceptable: Client must accept both application/json and text/event-stream"
                )
                return
            }

            if (!call.request.contentType().match(ContentType.Application.Json)) {
                call.reject(
                    HttpStatusCode.UnsupportedMediaType, ErrorCode.Unknown(-32000),
                    "Unsupported Media Type: Content-Type must be application/json"
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
                        HttpStatusCode.BadRequest, ErrorCode.Defined.InvalidRequest,
                        "Invalid Request: Server already initialized"
                    )
                    return
                }
                if (messages.size > 1) {
                    call.reject(
                        HttpStatusCode.BadRequest, ErrorCode.Defined.InvalidRequest,
                        "Invalid Request: Only one initialization request is allowed"
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

            val hasRequests = messages.any { it is JSONRPCRequest }
            if (!hasRequests) {
                call.respondNullable(status = HttpStatusCode.Accepted, message = null)
                messages.forEach { message -> _onMessage(message) }
                return
            }

            val streamId = Uuid.random().toString()
            if (!enableJsonResponse) {
                call.appendSseHeaders()
                sse.send(data = "") // flush headers immediately
            }

            streamMutex.withLock {
                streams[streamId] = ActiveStream(sse, call)
                messages.filterIsInstance<JSONRPCRequest>().forEach { requestToStream[it.id] = streamId }
            }
            sse.coroutineContext.job.invokeOnCompletion { streams -= streamId }

            messages.forEach { message -> _onMessage(message) }

        } catch (e: Exception) {
            call.reject(
                HttpStatusCode.BadRequest,
                ErrorCode.Defined.ParseError,
                "Parse error: ${e.message}"
            )
            _onError(e)
        }
    }

    public suspend fun handleGetRequest(call: ApplicationCall, sse: ServerSSESession) {
        val acceptHeader = call.request.headers[HttpHeaders.Accept]
        if (!acceptHeader.accepts(ContentType.Text.EventStream)) {
            call.reject(
                HttpStatusCode.NotAcceptable, ErrorCode.Unknown(-32000),
                "Not Acceptable: Client must accept text/event-stream"
            )
            return
        }

        if (!validateSession(call) || !validateProtocolVersion(call)) return

        eventStore?.let { store ->
            call.request.headers[LAST_EVENT_ID]?.let { lastEventId ->
                replayEvents(store, lastEventId, call, sse)
                return
            }
        }

        if (STANDALONE_SSE_STREAM_ID in streams) {
            call.reject(
                HttpStatusCode.Conflict, ErrorCode.Unknown(-32000),
                "Conflict: Only one SSE stream is allowed per session"
            )
            return
        }

        call.appendSseHeaders()
        sse.send(data = "")
        streams[STANDALONE_SSE_STREAM_ID] = ActiveStream(sse, call)
        sse.coroutineContext.job.invokeOnCompletion { streams -= STANDALONE_SSE_STREAM_ID }
    }

    public suspend fun handleDeleteRequest(call: ApplicationCall) {
        if (!validateSession(call) || !validateProtocolVersion(call)) return
        close()
        call.respondNullable(status = HttpStatusCode.OK, message = null)
    }

    private suspend fun replayEvents(
        store: EventStore,
        lastId: String,
        call: ApplicationCall,
        session: ServerSSESession
    ) {
        try {
            call.appendSseHeaders()
            val streamId = store.replayEventsAfter(lastId) { eventId, message ->
                try {
                    session.send(
                        event = "message",
                        id = eventId,
                        data = McpJson.encodeToString(message)
                    )
                } catch (e: Exception) {
                    _onError(e)
                }
            }
            streams[streamId] = ActiveStream(session, call)
        } catch (e: Exception) {
            _onError(e)
        }
    }

    private suspend fun validateSession(call: ApplicationCall): Boolean {
        if (sessionIdGenerator == null) return true

        if (!initialized.load()) {
            call.reject(
                HttpStatusCode.BadRequest, ErrorCode.Unknown(-32000),
                "Bad Request: Server not initialized"
            )
            return false
        }

        val headerId = call.request.headers[MCP_SESSION_ID]

        return when {
            headerId == null -> {
                call.reject(
                    HttpStatusCode.BadRequest, ErrorCode.Unknown(-32000),
                    "Bad Request: Mcp-Session-Id header is required"
                )
                false
            }

            headerId != sessionId -> {
                call.reject(
                    HttpStatusCode.NotFound, ErrorCode.Unknown(-32001),
                    "Session not found"
                )
                return false
            }

            else -> true
        }
    }

    private suspend fun validateProtocolVersion(call: ApplicationCall): Boolean {
        val version = call.request.headers[MCP_PROTOCOL_VERSION] ?: LATEST_PROTOCOL_VERSION

        return if (version !in SUPPORTED_PROTOCOL_VERSIONS) {
            call.reject(
                HttpStatusCode.BadRequest, ErrorCode.Unknown(-32000),
                "Bad Request: Unsupported protocol version (supported versions: ${
                    SUPPORTED_PROTOCOL_VERSIONS.joinToString(
                        ", "
                    )
                })"
            )
            false
        } else {
            true
        }
    }

    private fun validateHeaders(call: ApplicationCall): String? {
        if (!enableDnsRebindingProtection) return null
        allowedHosts?.let { hosts ->
            val hostHeader = call.request.host().substringBefore(':').lowercase()
            if (hostHeader !in hosts.map { it.substringBefore(':').lowercase() }) {
                return "Invalid Host header: $hostHeader"
            }
        }
        allowedOrigins?.let { origins ->
            val originHeader = call.request.headers[HttpHeaders.Origin]?.removeSuffix("/")?.lowercase()
            if (originHeader !in origins.map { it.removeSuffix("/").lowercase() }) {
                return "Invalid Origin header: $originHeader"
            }
        }
        return null
    }

    private suspend fun parseBody(call: ApplicationCall): List<JSONRPCMessage>? {
        val body = call.receiveText()
        return when (val element = McpJson.parseToJsonElement(body)) {
            is JsonObject -> listOf(McpJson.decodeFromJsonElement(element))
            is JsonArray -> McpJson.decodeFromJsonElement<List<JSONRPCMessage>>(element)
            else -> {
                call.response.status(HttpStatusCode.BadRequest)
                call.respond(
                    JSONRPCResponse(
                        id = null,
                        error = JSONRPCError(
                            code = ErrorCode.Defined.InvalidRequest,
                            message = "Invalid Request: Server already initialized"
                        )
                    )
                )
                return null
            }
        }
    }

    private fun String?.accepts(mime: ContentType): Boolean {
        if (this == null) return false

        val escaped = Regex.escape(mime.toString())
        val pattern = Regex("""(^|,\s*)$escaped(\s*(;|,|$))""", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(this)
    }

    private suspend fun emitOnStream(streamId: String, active: ActiveStream, message: JSONRPCMessage) {
        val eventId = eventStore?.storeEvent(streamId, message)
        try {
            active.sse.send(event = "message", id = eventId, data = McpJson.encodeToString(message))
        } catch (_: Exception) {
            streams.remove(streamId)
        }
    }

    private suspend fun ApplicationCall.reject(status: HttpStatusCode, code: ErrorCode, message: String) {
        this.response.status(status)
        this.respond(JSONRPCResponse(id = null, error = JSONRPCError(code = code, message = message)))
    }

    private fun ApplicationCall.appendSseHeaders() {
        this.response.headers.append(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
        this.response.headers.append(HttpHeaders.CacheControl, "no-cache, no-transform")
        this.response.headers.append(HttpHeaders.Connection, "keep-alive")
        this.response.headers.append("X-Accel-Buffering", "no")
        sessionId?.let { this.response.header(MCP_SESSION_ID, it) }
        this.response.status(HttpStatusCode.OK)
    }

    private companion object {
        const val STANDALONE_SSE_STREAM_ID = "_GET_stream"
    }
}
