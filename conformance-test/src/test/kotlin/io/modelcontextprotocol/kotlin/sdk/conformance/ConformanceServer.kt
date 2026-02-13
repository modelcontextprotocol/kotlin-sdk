package io.modelcontextprotocol.kotlin.sdk.conformance

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.WebSocketMcpServerTransport
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}
private val serverTransports = ConcurrentHashMap<String, HttpServerTransport>()
private val jsonFormat = Json { ignoreUnknownKeys = true }

private const val SESSION_CREATION_TIMEOUT_MS = 2000L
private const val REQUEST_TIMEOUT_MS = 10_000L
private const val MESSAGE_QUEUE_CAPACITY = 256

private fun isInitializeRequest(json: JsonElement): Boolean =
    json is JsonObject && json["method"]?.jsonPrimitive?.contentOrNull == "initialize"

@Suppress("CyclomaticComplexMethod", "LongMethod")
fun main(args: Array<String>) {
    val port = args.getOrNull(0)?.toIntOrNull() ?: 3000

    logger.info { "Starting MCP Conformance Server on port $port" }

    embeddedServer(CIO, port = port, host = "127.0.0.1") {
        install(WebSockets)

        routing {
            webSocket("/ws") {
                logger.info { "WebSocket connection established" }
                val transport = WebSocketMcpServerTransport(this)
                val server = createConformanceServer()

                try {
                    server.createSession(transport)
                } catch (e: Exception) {
                    logger.error(e) { "Error in WebSocket session" }
                    throw e
                }
            }

            get("/mcp") {
                val sessionId = call.request.header("mcp-session-id")
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, "Missing mcp-session-id header")
                        return@get
                    }
                val transport = serverTransports[sessionId]
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, "Invalid mcp-session-id")
                        return@get
                    }
                transport.stream(call)
            }

            post("/mcp") {
                val sessionId = call.request.header("mcp-session-id")
                val requestBody = call.receiveText()

                logger.debug { "Received request with sessionId: $sessionId" }
                logger.trace { "Request body: $requestBody" }

                val jsonElement = try {
                    jsonFormat.parseToJsonElement(requestBody)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to parse request body as JSON" }
                    call.respond(
                        HttpStatusCode.BadRequest,
                        jsonFormat.encodeToString(
                            JsonObject.serializer(),
                            buildJsonObject {
                                put("jsonrpc", "2.0")
                                put(
                                    "error",
                                    buildJsonObject {
                                        put("code", -32700)
                                        put("message", "Parse error: ${e.message}")
                                    },
                                )
                                put("id", JsonNull)
                            },
                        ),
                    )
                    return@post
                }

                val transport = sessionId?.let { serverTransports[it] }
                if (transport != null) {
                    logger.debug { "Using existing transport for session: $sessionId" }
                    transport.handleRequest(call, jsonElement)
                } else {
                    if (isInitializeRequest(jsonElement)) {
                        val newSessionId = UUID.randomUUID().toString()
                        logger.info { "Creating new session with ID: $newSessionId" }

                        val newTransport = HttpServerTransport(newSessionId)
                        serverTransports[newSessionId] = newTransport

                        val mcpServer = createConformanceServer()
                        call.response.header("mcp-session-id", newSessionId)

                        val sessionReady = CompletableDeferred<Unit>()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                mcpServer.createSession(newTransport)
                                sessionReady.complete(Unit)
                            } catch (e: Exception) {
                                logger.error(e) { "Failed to create session" }
                                serverTransports.remove(newSessionId)
                                newTransport.close()
                                sessionReady.completeExceptionally(e)
                            }
                        }

                        val sessionCreated = withTimeoutOrNull(SESSION_CREATION_TIMEOUT_MS) {
                            sessionReady.await()
                        }

                        if (sessionCreated == null) {
                            logger.error { "Session creation timed out" }
                            serverTransports.remove(newSessionId)
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                jsonFormat.encodeToString(
                                    JsonObject.serializer(),
                                    buildJsonObject {
                                        put("jsonrpc", "2.0")
                                        put(
                                            "error",
                                            buildJsonObject {
                                                put("code", -32000)
                                                put("message", "Session creation timed out")
                                            },
                                        )
                                        put("id", JsonNull)
                                    },
                                ),
                            )
                            return@post
                        }

                        newTransport.handleRequest(call, jsonElement)
                    } else {
                        logger.warn { "Invalid request: no session ID or not an initialization request" }
                        call.respond(
                            HttpStatusCode.BadRequest,
                            jsonFormat.encodeToString(
                                JsonObject.serializer(),
                                buildJsonObject {
                                    put("jsonrpc", "2.0")
                                    put(
                                        "error",
                                        buildJsonObject {
                                            put("code", -32000)
                                            put("message", "Bad Request: No valid session ID provided")
                                        },
                                    )
                                    put("id", JsonNull)
                                },
                            ),
                        )
                    }
                }
            }

            delete("/mcp") {
                val sessionId = call.request.header("mcp-session-id")
                val transport = sessionId?.let { serverTransports[it] }
                if (transport != null) {
                    logger.info { "Terminating session: $sessionId" }
                    serverTransports.remove(sessionId)
                    transport.close()
                    call.respond(HttpStatusCode.OK)
                } else {
                    logger.warn { "Invalid session termination request: $sessionId" }
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing session ID")
                }
            }
        }
    }.start(wait = true)
}

@Suppress("LongMethod")
private fun createConformanceServer(): Server {
    val server = Server(
        Implementation(
            name = "kotlin-conformance-server",
            version = "1.0.0",
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
                resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                prompts = ServerCapabilities.Prompts(listChanged = true),
            ),
        ),
    )

    server.addTool(
        name = "test-tool",
        description = "A test tool for conformance testing",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put(
                    "input",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Test input parameter")
                    },
                )
            },
            required = listOf("input"),
        ),
    ) { request ->
        val input = (request.params.arguments?.get("input") as? JsonPrimitive)?.content ?: "no input"
        CallToolResult(
            content = listOf(TextContent("Tool executed with input: $input")),
        )
    }

    server.addResource(
        uri = "test://test-resource",
        name = "Test Resource",
        description = "A test resource for conformance testing",
        mimeType = "text/plain",
    ) { request ->
        ReadResourceResult(
            contents = listOf(
                TextResourceContents("Test resource content", request.params.uri, "text/plain"),
            ),
        )
    }

    server.addPrompt(
        name = "test-prompt",
        description = "A test prompt for conformance testing",
        arguments = listOf(
            PromptArgument(
                name = "arg",
                description = "Test argument",
                required = false,
            ),
        ),
    ) {
        GetPromptResult(
            messages = listOf(
                PromptMessage(
                    role = Role.User,
                    content = TextContent("Test prompt content"),
                ),
            ),
            description = "Test prompt description",
        )
    }

    return server
}

private class HttpServerTransport(private val sessionId: String) : AbstractTransport() {
    private val logger = KotlinLogging.logger {}
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<JSONRPCMessage>>()
    private val messageQueue = Channel<JSONRPCMessage>(MESSAGE_QUEUE_CAPACITY)

    suspend fun stream(call: ApplicationCall) {
        logger.debug { "Starting SSE stream for session $sessionId" }
        call.response.apply {
            header("Cache-Control", "no-cache")
            header("Connection", "keep-alive")
        }
        call.respondTextWriter(ContentType.Text.EventStream) {
            try {
                while (true) {
                    val msg = messageQueue.receiveCatching().getOrNull() ?: break
                    write("event: message\ndata: ${McpJson.encodeToString(msg)}\n\n")
                    flush()
                }
            } catch (e: Exception) {
                logger.warn(e) { "SSE stream terminated for session $sessionId" }
            } finally {
                logger.debug { "SSE stream closed for session $sessionId" }
            }
        }
    }

    suspend fun handleRequest(call: ApplicationCall, requestBody: JsonElement) {
        try {
            val message = McpJson.decodeFromJsonElement<JSONRPCMessage>(requestBody)
            logger.debug { "Handling ${message::class.simpleName}: $requestBody" }

            when (message) {
                is JSONRPCRequest -> {
                    val idKey = when (val id = message.id) {
                        is RequestId.NumberId -> id.value.toString()
                        is RequestId.StringId -> id.value
                    }
                    val responseDeferred = CompletableDeferred<JSONRPCMessage>()
                    pendingResponses[idKey] = responseDeferred

                    _onMessage.invoke(message)

                    val response = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { responseDeferred.await() }
                    if (response != null) {
                        call.respondText(McpJson.encodeToString(response), ContentType.Application.Json)
                    } else {
                        pendingResponses.remove(idKey)
                        logger.warn { "Timeout for request $idKey" }
                        call.respondText(
                            McpJson.encodeToString(
                                JSONRPCError(
                                    message.id,
                                    RPCError(RPCError.ErrorCode.REQUEST_TIMEOUT, "Request timed out"),
                                ),
                            ),
                            ContentType.Application.Json,
                        )
                    }
                }

                else -> {
                    call.respond(HttpStatusCode.Accepted)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error handling request" }
            if (!call.response.isCommitted) {
                call.respondText(
                    McpJson.encodeToString(
                        JSONRPCError(
                            RequestId(0),
                            RPCError(RPCError.ErrorCode.INTERNAL_ERROR, "Internal error: ${e.message}"),
                        ),
                    ),
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError,
                )
            }
        }
    }

    override suspend fun start() {
        logger.debug { "Started transport for session $sessionId" }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        when (message) {
            is JSONRPCResponse -> {
                val idKey = when (val id = message.id) {
                    is RequestId.NumberId -> id.value.toString()
                    is RequestId.StringId -> id.value
                }
                pendingResponses.remove(idKey)?.complete(message) ?: run {
                    logger.warn { "No pending response for ID $idKey, queueing" }
                    messageQueue.send(message)
                }
            }

            else -> messageQueue.send(message)
        }
    }

    override suspend fun close() {
        logger.debug { "Closing transport for session $sessionId" }
        messageQueue.close()
        pendingResponses.clear()
        invokeOnCloseCallback()
    }
}
