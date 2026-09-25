package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.http.HttpRequestCloseHandlerKey
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.httpVersion
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.writeString
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * How long a silent handler may run before the response commits to `text/event-stream`.
 *
 * Long enough that an ordinary request answers as JSON, short enough that a slow one starts its
 * keepalive before a proxy's idle-read timeout closes the connection under it.
 */
private val SSE_PING_INTERVAL: Duration = 15.seconds

/** Serves one request-scoped POST end to end, and answers with JSON or SSE as the handler dictates. */
internal suspend fun ApplicationCall.serveModernRequest(request: JSONRPCRequest, server: Server) {
    val transport = SingleExchangeTransport(request.id)
    // A request-scoped session serves one exchange and emits nothing that is not its own answer, so
    // it stays off the feature-notification bus (§10: notifications on a response stream must relate
    // to its originating request).
    val session = server.createSession(transport, subscribeToFeatureNotifications = false)
    val clientGone = DisconnectSignal()
    try {
        coroutineScope {
            val exchange = this
            // Closing the response stream is this wire's only cancellation channel, and nothing is
            // written to the socket during the deferral window — the engine's close signal is the
            // one place a disconnect shows up before a write fails.
            onClientDisconnect {
                clientGone.fired = true
                exchange.cancel("The client closed the response stream")
            }
            // Delivery blocks until the handler answers, so it gets its own coroutine: the deferral
            // below has to watch the handler rather than wait behind it. Closing the response
            // cancels this scope, which cancels the handler — on this wire that *is* cancellation.
            val delivery = launch { transport.deliver(request) }
            respondModern(transport, delivery)
        }
    } catch (e: CancellationException) {
        // Only the disconnect watch's own cancellation ends here; server shutdown and any outer
        // cancellation must keep unwinding.
        if (!clientGone.fired) throw e
        logger.debug { "Abandoned exchange ${request.id}: the client closed the response stream" }
    } finally {
        // One request, one session: nothing here outlives the exchange, and the revision forbids
        // treating anything about it as continuity for the next one. NonCancellable because this
        // cleanup is what un-registers the session, disconnect or not.
        withContext(NonCancellable) { session.close() }
    }
}

/**
 * Runs [block] when the engine learns the client closed this call's connection mid-request.
 *
 * The signal rides the per-call callback Ktor's `HttpRequestLifecycle` plugin consumes. CIO also
 * fires it — falsely, for this purpose — the moment it knows a request is the last one on its
 * connection, so an `HTTP/1.0` or `Connection: close` request would read as an instant disconnect;
 * those requests keep write-failure detection instead, which is what every request got before this
 * watch existed. So do engines that never fire the callback (Jetty, the test host).
 */
@OptIn(InternalAPI::class)
private fun ApplicationCall.onClientDisconnect(block: () -> Unit) {
    if (request.closesConnection()) return
    val previous = attributes.getOrNull(HttpRequestCloseHandlerKey)
    attributes.put(HttpRequestCloseHandlerKey) {
        previous?.invoke()
        block()
    }
}

/** Set from the engine's close callback, read after cancellation — visibility, not contention. */
private class DisconnectSignal {
    @Volatile
    var fired: Boolean = false
}

/** Whether this request itself asks the server to close the connection after answering. */
private fun ApplicationRequest.closesConnection(): Boolean {
    val options = headers.getAll(HttpHeaders.Connection).orEmpty()
        .flatMap { it.split(',') }
        .map { it.trim().lowercase() }
    return when {
        "keep-alive" in options -> false
        "close" in options -> true
        else -> httpVersion != "HTTP/1.1"
    }
}

/** Acknowledges a request-scoped notification POST, which this wire defines none of. */
internal suspend fun ApplicationCall.acknowledgeModernNotification(method: String) {
    // Cancellation is closing the response stream, and a per-request entry holds no cross-request
    // state for a notification to act on — but clients in the field still post them, and a
    // fire-and-forget message is better acknowledged than answered with an error nobody reads.
    logger.debug { "Acknowledged and dropped request-scoped client notification $method" }
    respond(HttpStatusCode.Accepted)
}

/** Answers [error] on [status], as a JSON-RPC error body correlated to [id] where there is one. */
internal suspend fun ApplicationCall.respondJsonRpcError(error: RPCError, status: HttpStatusCode, id: RequestId?) {
    respondText(
        text = McpJson.encodeToString(JSONRPCError(id = id, error = error)),
        contentType = ContentType.Application.Json,
        status = status,
    )
}

/**
 * Waits out the deferral window and answers however it ends.
 *
 * A handler that answers — or fails — before emitting anything gets a plain JSON response, which is
 * what keeps the revision's `404` and `400` requirements reachable for dispatch-time errors: once
 * `text/event-stream` headers are written the status is locked to `200` and those become
 * unsatisfiable. A handler that emits, or that runs silent past the window, commits to SSE.
 */
private suspend fun ApplicationCall.respondModern(transport: SingleExchangeTransport, delivery: Job) {
    val first = withTimeoutOrNull(SSE_PING_INTERVAL) {
        select<FirstEvent> {
            // Notifications are offered first: a handler that emits and then answers in one breath
            // leaves both ready at once, and select takes the earliest ready clause. Answering from
            // the response would silently drop everything the handler emitted.
            transport.notifications.onReceive { FirstEvent.Emitted(it) }
            transport.outcome.onAwait { FirstEvent.Answered(it) }
            // Delivery ending is only news when it ended without answering; the answer is written
            // before delivery returns, so reading it here settles the tie rather than racing it.
            delivery.onJoin { transport.answered()?.let(FirstEvent::Answered) ?: FirstEvent.Abandoned }
        }
    }

    if (first is FirstEvent.Abandoned) {
        respondText(
            text = McpJson.encodeToString(
                JSONRPCError(
                    id = transport.requestId,
                    error = RPCError(
                        code = RPCError.ErrorCode.INTERNAL_ERROR,
                        message = "The request handler returned without producing a response",
                    ),
                ),
            ),
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.OK,
        )
        return
    }

    if (first is FirstEvent.Answered) {
        // Nothing raced the response into the channel, so this exchange never needed a stream.
        val buffered = transport.notifications.tryReceive().getOrNull()
        if (buffered == null) {
            respondText(
                text = McpJson.encodeToString(first.message),
                contentType = ContentType.Application.Json,
                status = statusOf(first.message),
            )
            return
        }
        commitSse(transport, pending = buffered)
        return
    }

    commitSse(transport, pending = (first as? FirstEvent.Emitted)?.message)
}

/** Streams notifications, keepalives and finally the response over `text/event-stream`. */
private suspend fun ApplicationCall.commitSse(transport: SingleExchangeTransport, pending: JSONRPCMessage?) {
    response.header(HttpHeaders.CacheControl, "no-cache, no-transform")
    // Tells reverse proxies not to buffer, which would hold every event until the response ends.
    response.header("X-Accel-Buffering", "no")
    respondBytesWriter(contentType = ContentType.Text.EventStream) {
        pending?.let { writeString(sseEvent(it)) }
        flush()
        while (true) {
            val next = withTimeoutOrNull(SSE_PING_INTERVAL) {
                select<JSONRPCMessage?> {
                    // Same bias as above: drain what the handler emitted before ending the stream.
                    transport.notifications.onReceive { it }
                    transport.outcome.onAwait { it }
                }
            }
            when {
                next == null -> writeString(": ping\r\n\r\n")

                next.isFinal(transport.requestId) -> {
                    writeString(sseEvent(next))
                    flush()
                    return@respondBytesWriter
                }

                else -> writeString(sseEvent(next))
            }
            flush()
        }
    }
}

/** Whether [this] is the response that ends the exchange rather than a notification within it. */
private fun JSONRPCMessage.isFinal(requestId: RequestId): Boolean = when (this) {
    is JSONRPCResponse -> id == requestId
    is JSONRPCError -> id == requestId
    else -> false
}

/**
 * The HTTP status [message] travels on.
 *
 * `200`, unless it is an error whose code has an entry in [HTTP_STATUS_BY_ERROR_CODE].
 */
private fun statusOf(message: JSONRPCMessage): HttpStatusCode = when (message) {
    is JSONRPCError -> HTTP_STATUS_BY_ERROR_CODE[message.error.code] ?: HttpStatusCode.OK
    else -> HttpStatusCode.OK
}

private fun sseEvent(message: JSONRPCMessage): String = "data: ${McpJson.encodeToString(message)}\n\n"

/** What ended the deferral window. */
private sealed interface FirstEvent {
    /** The handler answered. */
    data class Answered(val message: JSONRPCMessage) : FirstEvent

    /** The handler emitted a notification before answering. */
    data class Emitted(val message: JSONRPCMessage) : FirstEvent

    /** Delivery ended without an answer — a handler that neither returned nor failed. */
    data object Abandoned : FirstEvent
}

/**
 * The transport behind one request-scoped exchange.
 *
 * There is no session, no stream registry and no resumability to keep: one request goes in, its
 * notifications and then its response come out. A message that is neither is dropped, because the
 * revision requires every notification on a response stream to relate to its originating request and
 * there is no other stream to put one on.
 */
private class SingleExchangeTransport(val requestId: RequestId) : AbstractTransport() {

    val outcome: CompletableDeferred<JSONRPCMessage> = CompletableDeferred()

    val notifications: Channel<JSONRPCMessage> = Channel(Channel.BUFFERED)

    override suspend fun start() = Unit

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        if (message.isFinal(requestId)) {
            outcome.complete(message)
            return
        }
        try {
            // Suspends rather than drops when the buffer fills: on this wire a notification the
            // handler emitted is part of the response, so backpressure into the handler is correct
            // where silent loss is not. Only a genuinely closed stream drops, and says so honestly.
            notifications.send(message)
        } catch (_: ClosedSendChannelException) {
            logger.debug { "Dropped a notification: the response stream is closed" }
        }
    }

    override suspend fun close() {
        notifications.close()
        outcome.cancel()
        invokeOnCloseCallback()
    }

    /** The answer to this exchange, or `null` while there is none yet. */
    fun answered(): JSONRPCMessage? = if (outcome.isCompleted && !outcome.isCancelled) outcome.getCompleted() else null

    /** Hands [message] to the session and returns once it has been answered. */
    suspend fun deliver(message: JSONRPCMessage) {
        _onMessage(message)
    }
}
