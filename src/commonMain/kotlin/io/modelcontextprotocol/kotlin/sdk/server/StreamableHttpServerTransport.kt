package io.modelcontextprotocol.kotlin.sdk.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sse.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.MCP_SESSION_ID
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.collections.HashMap
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Server transport for StreamableHttp: this allows server to respond to GET, POST and DELETE requests. Server can optionally make use of Server-Sent Events (SSE) to stream multiple server messages.
 *
 * Creates a new StreamableHttp server transport.
 * 
 * @param isStateful If true, the server will generate and maintain session IDs for each client connection.
 *                   Session IDs are included in response headers and must be provided by clients in subsequent requests.
 * @param enableJSONResponse If true, the server will return JSON responses instead of starting an SSE stream.
 *                          This can be useful for simple request/response scenarios without streaming.
 */
@OptIn(ExperimentalAtomicApi::class)
public class StreamableHttpServerTransport(
    private val isStateful: Boolean = false,
    private val enableJSONResponse: Boolean = false,
): AbstractTransport() {
    private val standalone = "standalone"
    private val streamMapping: HashMap<String, ServerSSESession> = hashMapOf()
    private val requestToStreamMapping: HashMap<RequestId, String> = hashMapOf()
    private val requestResponseMapping: HashMap<RequestId, JSONRPCMessage> = hashMapOf()
    private val callMapping: HashMap<String, ApplicationCall> = hashMapOf()
    private val started: AtomicBoolean = AtomicBoolean(false)
    private val initialized: AtomicBoolean = AtomicBoolean(false)

    /**
     * The current session ID for this transport instance.
     * This is set during initialization when isStateful is true.
     * Clients must include this ID in the Mcp-Session-Id header for all subsequent requests.
     */
    public var sessionId: String? = null
        private set

    override suspend fun start() {
        if (!started.compareAndSet(false, true)) {
            error("StreamableHttpServerTransport already started! If using Server class, note that connect() calls start() automatically.")
        }
    }

    override suspend fun send(message: JSONRPCMessage) {
        var requestId: RequestId? = null

        if (message is JSONRPCResponse) {
            requestId = message.id
        }

        if (requestId == null) {
            val standaloneSSE = streamMapping[standalone] ?: return

            standaloneSSE.send(
                event = "message",
                data = McpJson.encodeToString(message),
            )
            return
        }

        val streamId = requestToStreamMapping[requestId] ?: error("No connection established for request id $requestId")
        val correspondingStream =
            streamMapping[streamId] ?: error("No connection established for request id $requestId")
        val correspondingCall = callMapping[streamId] ?: error("No connection established for request id $requestId")

        if (!enableJSONResponse) {
            correspondingStream.send(
                event = "message",
                data = McpJson.encodeToString(message),
            )
        }

        requestResponseMapping[requestId] = message
        val relatedIds =
            requestToStreamMapping.entries.filter { streamMapping[it.value] == correspondingStream }.map { it.key }
        val allResponsesReady = relatedIds.all { requestResponseMapping[it] != null }

        if (!allResponsesReady) return

        if (enableJSONResponse) {
            correspondingCall.response.headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            correspondingCall.response.status(HttpStatusCode.OK)
            if (sessionId != null) {
                correspondingCall.response.header(MCP_SESSION_ID, sessionId!!)
            }
            val responses = relatedIds.map { requestResponseMapping[it] }
            if (responses.size == 1) {
                correspondingCall.respond(responses[0]!!)
            } else {
                correspondingCall.respond(responses)
            }
            callMapping.remove(streamId)
        } else {
            correspondingStream.close()
            streamMapping.remove(streamId)
        }

        for (id in relatedIds) {
            requestToStreamMapping.remove(id)
            requestResponseMapping.remove(id)
        }

    }

    override suspend fun close() {
        streamMapping.values.forEach {
            it.close()
        }
        streamMapping.clear()
        requestToStreamMapping.clear()
        requestResponseMapping.clear()
        _onClose.invoke()
    }

    /**
     * Handles HTTP POST requests for the StreamableHttp transport.
     * This method processes JSON-RPC messages sent by clients and manages SSE sessions.
     * 
     * @param call The Ktor ApplicationCall representing the incoming HTTP request
     * @param session The ServerSSESession for streaming responses back to the client
     * 
     * The method performs the following:
     * - Validates required headers (Accept and Content-Type)
     * - Parses JSON-RPC messages from the request body
     * - Handles initialization requests and session management
     * - Sets up SSE streams for request/response communication
     * - Sends appropriate error responses for invalid requests
     */
    @OptIn(ExperimentalUuidApi::class)
    public suspend fun handlePostRequest(call: ApplicationCall, session: ServerSSESession) {
        try {
            if (!validateHeaders(call)) return

            val messages = parseBody(call)

            if (messages.isEmpty()) return

            val hasInitializationRequest = messages.any { it is JSONRPCRequest && it.method == Method.Defined.Initialize.value }
            if (hasInitializationRequest) {
                if (initialized.load() && sessionId != null) {
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
                    return
                }

                if (messages.size > 1) {
                    call.response.status(HttpStatusCode.BadRequest)
                    call.respond(
                        JSONRPCResponse(
                            id = null,
                            error = JSONRPCError(
                                code = ErrorCode.Defined.InvalidRequest,
                                message = "Invalid Request: Only one initialization request is allowed"
                            )
                        )
                    )
                    return
                }

                if (isStateful) {
                    sessionId = Uuid.random().toString()
                }
                initialized.store(true)
            }

            if (!validateSession(call)) {
                return
            }

            val hasRequests = messages.any { it is JSONRPCRequest }
            val streamId = Uuid.random().toString()

            if (!hasRequests) {
                call.respondNullable(HttpStatusCode.Accepted)
            } else {
                if (!enableJSONResponse) {
                    call.response.headers.append(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())

                    if (sessionId != null) {
                        call.response.header(MCP_SESSION_ID, sessionId!!)
                    }
                }

                for (message in messages) {
                    if (message is JSONRPCRequest) {
                        streamMapping[streamId] = session
                        callMapping[streamId] = call
                        requestToStreamMapping[message.id] = streamId
                    }
                }
            }
            for (message in messages) {
                _onMessage.invoke(message)
            }
        } catch (e: Exception) {
            call.response.status(HttpStatusCode.BadRequest)
            call.respond(
                JSONRPCResponse(
                    id = null,
                    error = JSONRPCError(
                        code = ErrorCode.Unknown(-32000),
                        message = e.message ?: "Parse error"
                    )
                )
            )
            _onError.invoke(e)
        }
    }

    /**
     * Handles HTTP GET requests for establishing standalone SSE streams.
     * This method sets up a persistent SSE connection for server-initiated messages.
     * 
     * @param call The Ktor ApplicationCall representing the incoming HTTP request
     * @param session The ServerSSESession for streaming messages to the client
     * 
     * The method:
     * - Validates the Accept header includes text/event-stream
     * - Validates session if stateful mode is enabled
     * - Ensures only one standalone SSE stream per session
     * - Sets up the stream for server-initiated notifications
     */
    public suspend fun handleGetRequest(call: ApplicationCall, session: ServerSSESession) {
        val acceptHeader = call.request.headers["Accept"]?.split(",")?.map { it.trim() } ?: listOf()
        val acceptsEventStream = acceptHeader.any { it == "text/event-stream" || it.startsWith("text/event-stream;") }
        
        if (!acceptsEventStream) {
            call.response.status(HttpStatusCode.NotAcceptable)
            call.respond(
                JSONRPCResponse(
                    id = null,
                    error = JSONRPCError(
                        code = ErrorCode.Unknown(-32000),
                        message = "Not Acceptable: Client must accept text/event-stream"
                    )
                )
            )
            return
        }

        if (!validateSession(call)) {
            return
        }

        if (sessionId != null) {
            call.response.header(MCP_SESSION_ID, sessionId!!)
        }

        if (streamMapping[standalone] != null) {
            call.response.status(HttpStatusCode.Conflict)
            call.respond(
                JSONRPCResponse(
                    id = null,
                    error = JSONRPCError(
                        code = ErrorCode.Unknown(-32000),
                        message = "Conflict: Only one SSE stream is allowed per session"
                    )
                )
            )
            session.close()
            return
        }

        // TODO: Equivalent of typescript res.writeHead(200, headers).flushHeaders();
        streamMapping[standalone] = session
    }

    /**
     * Handles HTTP DELETE requests to terminate the transport session.
     * This method allows clients to gracefully close their connection and clean up server resources.
     * 
     * @param call The Ktor ApplicationCall representing the incoming HTTP request
     * 
     * The method:
     * - Validates the session if stateful mode is enabled
     * - Closes all active SSE streams
     * - Clears all internal mappings and resources
     * - Responds with 200 OK on successful termination
     */
    public suspend fun handleDeleteRequest(call: ApplicationCall) {
        if (!validateSession(call)) {
            return
        }
        close()
        call.respondNullable(HttpStatusCode.OK)
    }

    private suspend fun validateSession(call: ApplicationCall): Boolean {
        if (sessionId == null) {
            return true
        }

        if (!initialized.load()) {
            call.response.status(HttpStatusCode.BadRequest)
            call.respond(
                JSONRPCResponse(
                    id = null,
                    error = JSONRPCError(
                        code = ErrorCode.Unknown(-32000),
                        message = "Bad Request: Server not initialized"
                    )
                )
            )
            return false
        }
        return true
    }

    private suspend fun validateHeaders(call: ApplicationCall): Boolean {
        val acceptHeader = call.request.headers["Accept"]?.split(",")?.map { it.trim() } ?: listOf()

        val acceptsEventStream = acceptHeader.any { it == "text/event-stream" || it.startsWith("text/event-stream;") }
        val acceptsJson = acceptHeader.any { it == "application/json" || it.startsWith("application/json;") }
        
        if (!acceptsEventStream || !acceptsJson) {
            call.response.status(HttpStatusCode.NotAcceptable)
            call.respond(
                JSONRPCResponse(
                    id = null,
                    error = JSONRPCError(
                        code = ErrorCode.Unknown(-32000),
                        message = "Not Acceptable: Client must accept both application/json and text/event-stream"
                    )
                )
            )
            return false
        }

        val contentType = call.request.contentType()
        if (contentType != ContentType.Application.Json) {
            call.response.status(HttpStatusCode.UnsupportedMediaType)
            call.respond(
                JSONRPCResponse(
                    id = null,
                    error = JSONRPCError(
                        code = ErrorCode.Unknown(-32000),
                        message = "Unsupported Media Type: Content-Type must be application/json"
                    )
                )
            )
            return false
        }

        return true
    }

    private suspend fun parseBody(
        call: ApplicationCall,
    ): List<JSONRPCMessage> {
        val messages = mutableListOf<JSONRPCMessage>()
        when (val body = call.receive<JsonElement>()) {
            is JsonObject -> messages.add(McpJson.decodeFromJsonElement(body))
            is JsonArray -> messages.addAll(McpJson.decodeFromJsonElement<List<JSONRPCMessage>>(body))
            else -> {
                call.response.status(HttpStatusCode.BadRequest)
                call.respond(
                    JSONRPCResponse(
                        id = null,
                        error = JSONRPCError(
                            code = ErrorCode.Defined.InvalidRequest,
                            message = "Invalid Request: Body must be a JSON object or array"
                        )
                    )
                )
                return listOf()
            }
        }
        return messages
    }


}
