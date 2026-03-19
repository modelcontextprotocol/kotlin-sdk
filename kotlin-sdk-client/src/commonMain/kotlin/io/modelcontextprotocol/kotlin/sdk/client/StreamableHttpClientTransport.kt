package io.modelcontextprotocol.kotlin.sdk.client

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.ClientSSESession
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sseSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val MCP_SESSION_ID_HEADER = "mcp-session-id"
private const val MCP_PROTOCOL_VERSION_HEADER = "mcp-protocol-version"
private const val MCP_RESUMPTION_TOKEN_HEADER = "Last-Event-ID"

/**
 * Error class for Streamable HTTP transport errors.
 */
public class StreamableHttpError(public val code: Int? = null, message: String? = null) :
    Exception("Streamable HTTP error: $message")

private sealed interface ConnectResult {
    data class Success(val session: ClientSSESession) : ConnectResult
    data object NonRetryable : ConnectResult
    data object Failed : ConnectResult
}

/**
 * Client transport for Streamable HTTP: this implements the MCP Streamable HTTP transport specification.
 * It will connect to a server using HTTP POST for sending messages and HTTP GET with Server-Sent Events
 * for receiving messages.
 */
@Suppress("TooManyFunctions")
public class StreamableHttpClientTransport(
    private val client: HttpClient,
    private val url: String,
    private val reconnectionOptions: ReconnectionOptions = ReconnectionOptions(),
    private val requestBuilder: HttpRequestBuilder.() -> Unit = {},
) : AbstractClientTransport() {

    @Deprecated(
        "Use constructor with ReconnectionOptions",
        replaceWith = ReplaceWith(
            "StreamableHttpClientTransport(client, url, " +
                "ReconnectionOptions(initialReconnectionDelay = reconnectionTime ?: 1.seconds), requestBuilder)",
            "kotlin.time.Duration.Companion.seconds",
            "io.modelcontextprotocol.kotlin.sdk.client.ReconnectionOptions",
        ),
    )
    public constructor(
        client: HttpClient,
        url: String,
        reconnectionTime: Duration?,
        requestBuilder: HttpRequestBuilder.() -> Unit = {},
    ) : this(client, url, ReconnectionOptions(initialReconnectionDelay = reconnectionTime ?: 1.seconds), requestBuilder)

    override val logger: KLogger = KotlinLogging.logger {}

    public var sessionId: String? = null
        private set
    public var protocolVersion: String? = null

    private var sseJob: Job? = null

    /** Result of an SSE stream collection. Reconnect when [hasPrimingEvent] is true and [receivedResponse] is false. */
    private data class SseStreamResult(
        val hasPrimingEvent: Boolean,
        val receivedResponse: Boolean,
        val lastEventId: String? = null,
        val serverRetryDelay: Duration? = null,
    )

    override suspend fun initialize() {
        logger.debug { "Client transport is starting..." }
    }

    /**
     * Sends a single message with optional resumption support
     */
    @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod", "TooGenericExceptionCaught", "ThrowsCount")
    override suspend fun performSend(message: JSONRPCMessage, options: TransportSendOptions?) {
        logger.debug { "Client sending message via POST to $url: ${McpJson.encodeToString(message)}" }

        // If we have a resumption token, reconnect the SSE stream with it
        options?.resumptionToken?.let { token ->
            startSseSession(
                resumptionToken = token,
                onResumptionToken = options.onResumptionToken,
                replayMessageId = if (message is JSONRPCRequest) message.id else null,
            )
            return
        }

        val jsonBody = McpJson.encodeToString(message)
        val response = client.post(url) {
            applyCommonHeaders(this)
            headers.append(HttpHeaders.Accept, "${ContentType.Application.Json}, ${ContentType.Text.EventStream}")
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
            requestBuilder()
        }

        response.headers[MCP_SESSION_ID_HEADER]?.let { sessionId = it }

        if (response.status == HttpStatusCode.Accepted) {
            if (message is JSONRPCNotification && message.method == "notifications/initialized") {
                startSseSession(onResumptionToken = options?.onResumptionToken)
            }
            return
        }

        if (!response.status.isSuccess()) {
            val error = StreamableHttpError(response.status.value, response.bodyAsText())
            handleError(error)
            throw error
        }

        when (response.contentType()?.withoutParameters()) {
            ContentType.Application.Json -> response.bodyAsText().takeIf { it.isNotEmpty() }?.let { json ->
                runCatching { McpJson.decodeFromString<JSONRPCMessage>(json) }
                    .onSuccess {
                        handleMessage(it)
                    }
                    .onFailure {
                        handleError(it)
                        throw it
                    }
            }

            ContentType.Text.EventStream -> {
                val replayMessageId = if (message is JSONRPCRequest) message.id else null
                val result = handleInlineSse(response, replayMessageId, options?.onResumptionToken)
                if (result.hasPrimingEvent && !result.receivedResponse) {
                    startSseSession(
                        resumptionToken = result.lastEventId,
                        replayMessageId = replayMessageId,
                        onResumptionToken = options?.onResumptionToken,
                        initialServerRetryDelay = result.serverRetryDelay,
                    )
                }
            }

            else -> {
                val body = response.bodyAsText()
                if (response.contentType() == null && body.isBlank()) return

                val ct = response.contentType()?.toString() ?: "<none>"
                val error = StreamableHttpError(-1, "Unexpected content type: $ct")
                handleError(error)
                throw error
            }
        }
    }

    /**
     * Sends one or more messages with optional resumption support.
     * This is the main send method that matches the TypeScript implementation.
     */
    public suspend fun send(
        message: JSONRPCMessage,
        resumptionToken: String?,
        onResumptionToken: ((String) -> Unit)? = null,
    ): Unit = send(
        message = message,
        options = TransportSendOptions(
            resumptionToken = resumptionToken,
            onResumptionToken = onResumptionToken,
        ),
    )

    override suspend fun closeResources() {
        logger.debug { "Client transport closing." }
        shutdownHandlers()
        sseJob?.cancelAndJoin()
    }

    /**
     * Terminates the current session by sending a DELETE request to the server.
     */
    public suspend fun terminateSession() {
        if (sessionId == null) return
        logger.debug { "Terminating session: $sessionId" }
        val response = client.delete(url) {
            applyCommonHeaders(this)
            requestBuilder()
        }

        // 405 means server doesn't support explicit session termination
        if (!response.status.isSuccess() && response.status != HttpStatusCode.MethodNotAllowed) {
            val error = StreamableHttpError(
                response.status.value,
                "Failed to terminate session: ${response.status.description}",
            )
            logger.error(error) { "Failed to terminate session" }
            handleError(error)
            throw error
        }

        sessionId = null
        logger.debug { "Session terminated successfully" }
    }

    private fun startSseSession(
        resumptionToken: String? = null,
        replayMessageId: RequestId? = null,
        onResumptionToken: ((String) -> Unit)? = null,
        initialServerRetryDelay: Duration? = null,
    ) {
        // Cancel-and-replace: cancel() signals the previous job, join() inside
        // the new coroutine ensures it completes before we start collecting.
        // This is intentionally non-suspend to avoid blocking performSend.
        val previousJob = sseJob
        previousJob?.cancel()
        sseJob = scope.launch(CoroutineName("StreamableHttpTransport.collect#${hashCode()}")) {
            previousJob?.join()
            var lastEventId = resumptionToken
            var serverRetryDelay = initialServerRetryDelay
            var attempt = 0
            var needsDelay = initialServerRetryDelay != null

            @Suppress("LoopWithTooManyJumpStatements")
            while (isActive) {
                // Delay before (re)connection: skip only for first fresh SSE connection
                if (needsDelay) {
                    delay(getNextReconnectionDelay(attempt, serverRetryDelay))
                }
                needsDelay = true

                // Connect
                val session = when (val cr = connectSse(lastEventId)) {
                    is ConnectResult.Success -> {
                        attempt = 0
                        cr.session
                    }

                    ConnectResult.NonRetryable -> return@launch

                    ConnectResult.Failed -> {
                        // Give up after maxRetries consecutive failed connection attempts
                        if (++attempt >= reconnectionOptions.maxRetries) {
                            handleError(StreamableHttpError(null, "Maximum reconnection attempts exceeded"))
                            return@launch
                        }
                        continue
                    }
                }

                // Collect
                val result = collectSse(session, replayMessageId, onResumptionToken)
                lastEventId = result.lastEventId ?: lastEventId
                serverRetryDelay = result.serverRetryDelay ?: serverRetryDelay
                if (result.receivedResponse) break
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun connectSse(lastEventId: String?): ConnectResult {
        logger.debug { "Client attempting to start SSE session at url: $url" }
        return try {
            val session = client.sseSession(urlString = url, showRetryEvents = true) {
                method = HttpMethod.Get
                applyCommonHeaders(this)
                accept(ContentType.Application.Json)
                lastEventId?.let { headers.append(MCP_RESUMPTION_TOKEN_HEADER, it) }
                requestBuilder()
            }
            logger.debug { "Client SSE session started successfully." }
            ConnectResult.Success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SSEClientException) {
            if (isNonRetryableSseError(e)) {
                ConnectResult.NonRetryable
            } else {
                logger.debug { "SSE connection failed: ${e.message}" }
                ConnectResult.Failed
            }
        } catch (e: Exception) {
            logger.debug { "SSE connection failed: ${e.message}" }
            ConnectResult.Failed
        }
    }

    private fun getNextReconnectionDelay(attempt: Int, serverRetryDelay: Duration?): Duration {
        // Per SSE specification, the server-sent `retry` field sets the reconnection time
        // for all subsequent attempts, taking priority over exponential backoff.
        serverRetryDelay?.let { return it }
        val delay = reconnectionOptions.initialReconnectionDelay *
            reconnectionOptions.reconnectionDelayMultiplier.pow(attempt)
        return delay.coerceAtMost(reconnectionOptions.maxReconnectionDelay)
    }

    /**
     * Checks if an SSE session error is non-retryable (404, 405, JSON-only).
     * Returns `true` if non-retryable (should stop trying), `false` otherwise.
     */
    private fun isNonRetryableSseError(e: SSEClientException): Boolean {
        val responseStatus = e.response?.status
        val responseContentType = e.response?.contentType()

        return when {
            responseStatus == HttpStatusCode.NotFound || responseStatus == HttpStatusCode.MethodNotAllowed -> {
                logger.info { "Server returned ${responseStatus.value} for GET/SSE, stream disabled." }
                true
            }

            responseContentType?.match(ContentType.Application.Json) == true -> {
                logger.info { "Server returned application/json for GET/SSE, using JSON-only mode." }
                true
            }

            else -> false
        }
    }

    private fun applyCommonHeaders(builder: HttpRequestBuilder) {
        builder.headers {
            sessionId?.let { append(MCP_SESSION_ID_HEADER, it) }
            protocolVersion?.let { append(MCP_PROTOCOL_VERSION_HEADER, it) }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun collectSse(
        session: ClientSSESession,
        replayMessageId: RequestId?,
        onResumptionToken: ((String) -> Unit)?,
    ): SseStreamResult {
        var hasPrimingEvent = false
        var receivedResponse = false
        var localLastEventId: String? = null
        var localServerRetryDelay: Duration? = null
        try {
            session.incoming.collect { event ->
                event.retry?.let { localServerRetryDelay = it.milliseconds }
                event.id?.let {
                    localLastEventId = it
                    hasPrimingEvent = true
                    onResumptionToken?.invoke(it)
                }
                logger.trace { "Client received SSE event: event=${event.event}, data=${event.data}, id=${event.id}" }
                when (event.event) {
                    null, "message" ->
                        event.data?.takeIf { it.isNotEmpty() }?.let { json ->
                            runCatching { McpJson.decodeFromString<JSONRPCMessage>(json) }
                                .onSuccess { msg ->
                                    if (msg is JSONRPCResponse) receivedResponse = true
                                    if (replayMessageId != null && msg is JSONRPCResponse) {
                                        handleMessage(msg.copy(id = replayMessageId))
                                    } else {
                                        handleMessage(msg)
                                    }
                                }
                                .onFailure(::handleError)
                        }

                    "error" -> handleError(StreamableHttpError(null, event.data))
                }
            }
        } catch (_: CancellationException) {
            // ignore
        } catch (t: Throwable) {
            handleError(t)
        }
        return SseStreamResult(hasPrimingEvent, receivedResponse, localLastEventId, localServerRetryDelay)
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun handleInlineSse(
        response: HttpResponse,
        replayMessageId: RequestId?,
        onResumptionToken: ((String) -> Unit)?,
    ): SseStreamResult {
        logger.trace { "Handling inline SSE from POST response" }
        val channel = response.bodyAsChannel()

        var hasPrimingEvent = false
        var receivedResponse = false
        var localLastEventId: String? = null
        var localServerRetryDelay: Duration? = null
        val sb = StringBuilder()
        var id: String? = null
        var eventName: String? = null

        fun dispatch(id: String?, eventName: String?, data: String) {
            id?.let {
                localLastEventId = it
                hasPrimingEvent = true
                onResumptionToken?.invoke(it)
            }
            if (data.isBlank()) {
                return
            }
            if (eventName == null || eventName == "message") {
                runCatching { McpJson.decodeFromString<JSONRPCMessage>(data) }
                    .onSuccess { msg ->
                        if (msg is JSONRPCResponse) receivedResponse = true
                        if (replayMessageId != null && msg is JSONRPCResponse) {
                            handleMessage(msg.copy(id = replayMessageId))
                        } else {
                            handleMessage(msg)
                        }
                    }
                    .onFailure {
                        handleError(it)
                        throw it
                    }
            }
            if (eventName == "error") {
                handleError(StreamableHttpError(null, data))
                return
            }
        }

        @Suppress("LoopWithTooManyJumpStatements")
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.isEmpty()) {
                dispatch(id = id, eventName = eventName, data = sb.toString())
                // reset
                id = null
                eventName = null
                sb.clear()
                continue
            }
            when {
                line.startsWith("id:") -> id = line.substringAfter("id:").trim()

                line.startsWith("event:") -> eventName = line.substringAfter("event:").trim()

                line.startsWith("data:") -> {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(line.substringAfter("data:").removePrefix(" "))
                }

                line.startsWith("retry:") -> line.substringAfter("retry:").trim().toLongOrNull()?.let {
                    localServerRetryDelay = it.milliseconds
                }
            }
        }
        dispatch(id = id, eventName = eventName, data = sb.toString())
        return SseStreamResult(hasPrimingEvent, receivedResponse, localLastEventId, localServerRetryDelay)
    }
}
