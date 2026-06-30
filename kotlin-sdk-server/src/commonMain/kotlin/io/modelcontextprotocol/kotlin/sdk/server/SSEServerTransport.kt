package io.modelcontextprotocol.kotlin.sdk.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPath
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.response.respondText
import io.ktor.server.sse.ServerSSESession
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.job
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal const val SESSION_ID_PARAM = "sessionId"

/**
 * Server transport for SSE: this will send messages over an SSE connection and receive messages from HTTP POST requests.
 *
 * Creates a new SSE server transport, which will direct the client to POST messages to the relative or absolute URL identified by `_endpoint`.
 *
 * @param endpoint relative or absolute URL the client will POST messages to
 * @param session active SSE session used to deliver server-to-client events
 * @param maxRequestBodySize maximum allowed size, in bytes, of an incoming POST body; larger requests are
 *      rejected with `413 Payload Too Large` without being buffered in full. Defaults to 4 MiB.
 */
@OptIn(ExperimentalAtomicApi::class)
public class SseServerTransport(
    private val endpoint: String,
    private val session: ServerSSESession,
    private val maxRequestBodySize: Long = DEFAULT_MAX_REQUEST_BODY_SIZE,
) : AbstractTransport() {
    init {
        require(maxRequestBodySize > 0) { "maxRequestBodySize must be greater than 0" }
    }

    private val initialized: AtomicBoolean = AtomicBoolean(false)

    /** Unique identifier for this transport session, generated randomly on creation. */
    @OptIn(ExperimentalUuidApi::class)
    public val sessionId: String = Uuid.random().toString()

    /**
     * Handles the initial SSE connection request.
     *
     * This should be called when a GET request is made to establish the SSE stream.
     */
    override suspend fun start() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
            error(
                "SSEServerTransport already started! If using Server class, note that connect() calls start() automatically.",
            )
        }

        // Send the endpoint event
        session.send(
            event = "endpoint",
            data = "${endpoint.encodeURLPath()}?$SESSION_ID_PARAM=$sessionId",
        )

        @OptIn(InternalCoroutinesApi::class)
        session.coroutineContext.job.invokeOnCompletion {
            if (it != null && it !is CancellationException) {
                _onError.invoke(it)
            } else {
                invokeOnCloseCallback()
            }
        }
    }

    /**
     * Handles incoming POST messages.
     *
     * This should be called when a POST request is made to send a message to the server.
     */
    public suspend fun handlePostMessage(call: ApplicationCall) {
        if (!initialized.load()) {
            val message = "SSE connection not established"
            call.respondText(message, status = HttpStatusCode.InternalServerError)
            _onError.invoke(IllegalStateException(message))
            return
        }

        val body = try {
            val ct = call.request.contentType()
            if (!ct.match(ContentType.Application.Json)) {
                error("Unsupported content-type: $ct")
            }

            call.receiveTextWithLimit(maxRequestBodySize)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RequestBodyTooLargeException) {
            call.respondText(e.message ?: "Request body too large", status = HttpStatusCode.PayloadTooLarge)
            _onError.invoke(e)
            return
        } catch (e: Exception) {
            call.respondText("Invalid message: ${e.message}", status = HttpStatusCode.BadRequest)
            _onError.invoke(e)
            return
        }

        try {
            handleMessage(body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            call.respondText("Error handling message $body: ${e.message}", status = HttpStatusCode.BadRequest)
            return
        }

        call.respondText("Accepted", status = HttpStatusCode.Accepted)
    }

    /**
     * Handle a client message, regardless of how it arrived.
     * This can be used to inform the server of messages that arrive via a means different from HTTP POST.
     */
    public suspend fun handleMessage(message: String) {
        try {
            val parsedMessage = McpJson.decodeFromString<JSONRPCMessage>(message)
            _onMessage.invoke(parsedMessage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _onError.invoke(e)
            throw e
        }
    }

    override suspend fun close() {
        session.close()
        invokeOnCloseCallback()
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        if (!initialized.load()) {
            error("Not connected")
        }

        session.send(
            event = "message",
            data = McpJson.encodeToString(message),
        )
    }
}
