package io.modelcontextprotocol.kotlin.sdk.integration.typescript.sse

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
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
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
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
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalMcpApi::class)
class KotlinServerForTsClient {
    private val serverTransports = ConcurrentHashMap<String, HttpServerTransport>()
    private val jsonFormat = Json { ignoreUnknownKeys = true }
    private var server: EmbeddedServer<*, *>? = null

    fun start(port: Int = 3000) {
        logger.info { "Starting HTTP server on port $port" }

        server = embeddedServer(CIO, port = port) {
            routing {
                get("/mcp") {
                    val sessionId = call.request.header("mcp-session-id")
                    if (sessionId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing mcp-session-id header")
                        return@get
                    }
                    val transport = serverTransports[sessionId]
                    if (transport == null) {
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
                                JsonObject(
                                    mapOf(
                                        "jsonrpc" to JsonPrimitive("2.0"),
                                        "error" to JsonObject(
                                            mapOf(
                                                "code" to JsonPrimitive(-32700),
                                                "message" to JsonPrimitive("Parse error: ${e.message}"),
                                            ),
                                        ),
                                        "id" to JsonNull,
                                    ),
                                ),
                            ),
                        )
                        return@post
                    }

                    if (sessionId != null && serverTransports.containsKey(sessionId)) {
                        logger.debug { "Using existing transport for session: $sessionId" }
                        val transport = serverTransports[sessionId]!!
                        transport.handleRequest(call, jsonElement)
                    } else {
                        if (isInitializeRequest(jsonElement)) {
                            val newSessionId = UUID.randomUUID().toString()
                            logger.info { "Creating new session with ID: $newSessionId" }

                            val transport = HttpServerTransport(newSessionId)

                            serverTransports[newSessionId] = transport

                            val mcpServer = createMcpServer()

                            call.response.header("mcp-session-id", newSessionId)

                            val serverThread = Thread {
                                runBlocking {
                                    mcpServer.createSession(transport)
                                }
                            }
                            serverThread.start()

                            Thread.sleep(500)

                            transport.handleRequest(call, jsonElement)
                        } else {
                            logger.warn { "Invalid request: no session ID or not an initialization request" }
                            call.respond(
                                HttpStatusCode.BadRequest,
                                jsonFormat.encodeToString(
                                    JsonObject.serializer(),
                                    JsonObject(
                                        mapOf(
                                            "jsonrpc" to JsonPrimitive("2.0"),
                                            "error" to JsonObject(
                                                mapOf(
                                                    "code" to JsonPrimitive(-32000),
                                                    "message" to
                                                        JsonPrimitive("Bad Request: No valid session ID provided"),
                                                ),
                                            ),
                                            "id" to JsonNull,
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                }

                delete("/mcp") {
                    val sessionId = call.request.header("mcp-session-id")
                    if (sessionId != null && serverTransports.containsKey(sessionId)) {
                        logger.info { "Terminating session: $sessionId" }
                        val transport = serverTransports[sessionId]!!
                        serverTransports.remove(sessionId)
                        transport.close()
                        call.respond(HttpStatusCode.OK)
                    } else {
                        logger.warn { "Invalid session termination request: $sessionId" }
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing session ID")
                    }
                }
            }
        }

        server?.start(wait = false)
    }

    fun stop() {
        logger.info { "Stopping HTTP server" }
        server?.stop(500, 1000)
        server = null
    }

    fun createMcpServer(): Server {
        val server = Server(
            Implementation(
                name = "kotlin-http-server",
                version = "1.0.0",
            ),
            ServerOptions(
                capabilities = ServerCapabilities(
                    prompts = ServerCapabilities.Prompts(listChanged = true),
                    resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                    tools = ServerCapabilities.Tools(listChanged = true),
                ),
            ),
        )

        server.addTool(
            name = "greet",
            description = "A simple greeting tool",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put(
                        "name",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Name to greet"))
                        },
                    )
                },
                required = listOf("name"),
            ),
        ) { request ->
            val name = (request.params.arguments?.get("name") as? JsonPrimitive)?.content ?: "World"
            CallToolResult {
                textContent("Hello, $name!")
                structuredContent {
                    put("greeting", JsonPrimitive("Hello, $name!"))
                }
            }
        }

        server.addTool(
            name = "multi-greet",
            description = "A greeting tool that sends multiple notifications",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put(
                        "name",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Name to greet"))
                        },
                    )
                },
                required = listOf("name"),
            ),
        ) { request ->
            val name = (request.params.arguments?.get("name") as? JsonPrimitive)?.content ?: "World"

            CallToolResult {
                textContent("Multiple greetings sent to $name!")
                structuredContent {
                    put("greeting", JsonPrimitive("Multiple greetings sent to $name!"))
                    put("notificationCount", JsonPrimitive(3))
                }
            }
        }

        server.addPrompt(
            name = "greeting-template",
            description = "A simple greeting prompt template",
            arguments = listOf(
                PromptArgument(
                    name = "name",
                    description = "Name to include in greeting",
                    required = true,
                ),
            ),
        ) { request ->
            GetPromptResult(
                messages = listOf(
                    PromptMessage(
                        role = Role.User,
                        content = TextContent(
                            "Please greet ${request.params.arguments?.get("name") ?: "someone"} in a friendly manner.",
                        ),
                    ),
                ),
                description = "Greeting for ${request.params.name}",
            )
        }

        server.addResource(
            uri = "https://example.com/greetings/default",
            name = "Default Greeting",
            description = "A simple greeting resource",
            mimeType = "text/plain",
        ) { request ->
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents("Hello, world!", request.params.uri, "text/plain"),
                ),
            )
        }

        return server
    }

    private fun isInitializeRequest(json: JsonElement): Boolean {
        if (json !is JsonObject) return false

        val method = json["method"]?.jsonPrimitive?.contentOrNull
        return method == "initialize"
    }
}

class HttpServerTransport(private val sessionId: String) : AbstractTransport() {
    private val logger = KotlinLogging.logger {}
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<JSONRPCMessage>>()
    private val messageQueue = Channel<JSONRPCMessage>(Channel.UNLIMITED)

    suspend fun stream(call: ApplicationCall) {
        logger.debug { "Starting SSE stream for session: $sessionId" }
        call.response.header("Cache-Control", "no-cache")
        call.response.header("Connection", "keep-alive")
        call.respondTextWriter(ContentType.Text.EventStream) {
            try {
                while (true) {
                    val result = messageQueue.receiveCatching()
                    val msg = result.getOrNull() ?: break
                    val json = McpJson.encodeToString(msg)
                    write("event: message\n")
                    write("data: ")
                    write(json)
                    write("\n\n")
                    flush()
                }
            } catch (e: Exception) {
                logger.warn(e) { "SSE stream terminated for session: $sessionId" }
            } finally {
                logger.debug { "SSE stream closed for session: $sessionId" }
            }
        }
    }

    suspend fun handleRequest(call: ApplicationCall, requestBody: JsonElement) {
        try {
            logger.info { "Handling request body: $requestBody" }
            val message = McpJson.decodeFromJsonElement<JSONRPCMessage>(requestBody)
            logger.info { "Decoded message: $message" }

            if (message is JSONRPCRequest) {
                val id = message.id.toString()
                logger.info { "Received request with ID: $id, method: ${message.method}" }
                val responseDeferred = CompletableDeferred<JSONRPCMessage>()
                pendingResponses[id] = responseDeferred
                logger.info { "Created deferred response for ID: $id" }

                logger.info { "Invoking onMessage handler" }
                _onMessage.invoke(message)
                logger.info { "onMessage handler completed" }

                try {
                    val response = withTimeoutOrNull(10000) {
                        responseDeferred.await()
                    }

                    if (response != null) {
                        val jsonResponse = McpJson.encodeToString(response)
                        call.respondText(jsonResponse, ContentType.Application.Json)
                    } else {
                        logger.warn { "Timeout waiting for response to request ID: $id" }
                        call.respondText(
                            McpJson.encodeToString(
                                JSONRPCError(
                                    id = message.id,
                                    error = RPCError(
                                        code = RPCError.ErrorCode.REQUEST_TIMEOUT,
                                        message = "Request timed out",
                                    ),
                                ),
                            ),
                            ContentType.Application.Json,
                        )
                    }
                } catch (_: CancellationException) {
                    logger.warn { "Request cancelled for ID: $id" }
                    pendingResponses.remove(id)
                    if (!call.response.isCommitted) {
                        call.respondText(
                            McpJson.encodeToString(
                                JSONRPCError(
                                    id = message.id,
                                    error = RPCError(
                                        code = RPCError.ErrorCode.CONNECTION_CLOSED,
                                        message = "Request cancelled",
                                    ),
                                ),
                            ),
                            ContentType.Application.Json,
                            HttpStatusCode.ServiceUnavailable,
                        )
                    }
                }
            } else {
                call.respondText("", ContentType.Application.Json, HttpStatusCode.Accepted)
            }
        } catch (e: Exception) {
            logger.error(e) { "Error handling request" }
            if (!call.response.isCommitted) {
                try {
                    val errorResponse = JSONRPCError(
                        id = RequestId(0),
                        error = RPCError(
                            code = RPCError.ErrorCode.INTERNAL_ERROR,
                            message = "Internal server error: ${e.message}",
                        ),
                    )

                    call.respondText(
                        McpJson.encodeToString(errorResponse),
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError,
                    )
                } catch (responseEx: Exception) {
                    logger.error(responseEx) { "Failed to send error response" }
                }
            }
        }
    }

    override suspend fun start() {
        logger.debug { "Starting HTTP server transport for session: $sessionId" }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        logger.info { "Sending message: $message" }

        if (message is JSONRPCResponse) {
            val id = message.id.toString()
            logger.info { "Sending response for request ID: $id" }
            val deferred = pendingResponses.remove(id)
            if (deferred != null) {
                logger.info { "Found pending response for ID: $id, completing deferred" }
                deferred.complete(message)
                return
            } else {
                logger.warn { "No pending response found for ID: $id" }
            }
        } else if (message is JSONRPCRequest) {
            logger.info { "Sending request with ID: ${message.id}" }
        } else if (message is JSONRPCNotification) {
            logger.info { "Sending notification: ${message.method}" }
        }

        logger.info { "Queueing message for next client request" }
        messageQueue.send(message)
    }

    override suspend fun close() {
        logger.debug { "Closing HTTP server transport for session: $sessionId" }
        messageQueue.close()
        invokeOnCloseCallback()
    }
}

fun main() {
    val server = KotlinServerForTsClient()
    server.start()
}
