package io.modelcontextprotocol.kotlin.sdk.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sse.*
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.job
import kotlinx.serialization.encodeToString
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal const val SESSION_ID_PARAM = "sessionId"

@Deprecated("Use SseServerTransport instead", ReplaceWith("SseServerTransport"), DeprecationLevel.WARNING)
public typealias SSEServerTransport = SseServerTransport

/**
 * Server transport for SSE: this will send messages over an SSE connection and receive messages from HTTP POST requests.
 *
 * Creates a new SSE server transport, which will direct the client to POST messages to the relative or absolute URL identified by `_endpoint`.
 */
public class SseServerTransport(
    private val endpoint: String,
    private val session: ServerSSESession,
) : AbstractTransport() {
    private val initialized: AtomicBoolean = atomic(false)

    @OptIn(ExperimentalUuidApi::class)
    public val sessionId: String = Uuid.random().toString()

    /**
     * Handles the initial SSE connection request.
     *
     * This should be called when a GET request is made to establish the SSE stream.
     */
    override suspend fun start() {
        if (!initialized.compareAndSet(false, true)) {
            throw error("SSEServerTransport already started! If using Server class, note that connect() calls start() automatically.")
        }

        // Send the endpoint event
        session.send(
            event = "endpoint",
            data = "${endpoint.encodeURLPath()}?$SESSION_ID_PARAM=${sessionId}",
        )

        try {
            session.coroutineContext.job.join()
        } finally {
            _onClose.invoke()
        }
    }

    /**
     * Handles incoming POST messages.
     *
     * This should be called when a POST request is made to send a message to the server.
     */
    public suspend fun handlePostMessage(call: ApplicationCall) {
        if (!initialized.value) {
            val message = "SSE connection not established"
            call.respondText(message, status = HttpStatusCode.InternalServerError)
            _onError.invoke(IllegalStateException(message))
        }

        val body = try {
            val ct = call.request.contentType()
            if (ct != ContentType.Application.Json) {
                error("Unsupported content-type: $ct")
            }

            call.receiveText()
        } catch (e: Exception) {
            call.respondText("Invalid message: ${e.message}", status = HttpStatusCode.BadRequest)
            _onError.invoke(e)
            return
        }

        try {
            handleMessage(body)
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
        } catch (e: Exception) {
            _onError.invoke(e)
            throw e
        }
    }

    override suspend fun close() {
        session.close()
        _onClose.invoke()
    }

    override suspend fun send(message: JSONRPCMessage) {
        if (!initialized.value) {
            throw error("Not connected")
        }

        session.send(
            event = "message",
            data = McpJson.encodeToString(message),
        )
    }
}
