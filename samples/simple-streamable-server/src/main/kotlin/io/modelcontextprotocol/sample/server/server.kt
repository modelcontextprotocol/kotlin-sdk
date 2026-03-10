package io.modelcontextprotocol.sample.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.util.collections.ConcurrentMap
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration.Companion.milliseconds

private const val MCP_SESSION_ID_HEADER = "mcp-session-id"

fun Application.configureServer(authToken: String? = null) {
    installCors(authEnabled = authToken != null)
    install(ContentNegotiation) {
        json(McpJson)
    }

    if (authToken == null) {
        mcpStreamableHttp {
            createMcpServer()
        }
    } else {
        configureAuthenticatedMcp(authToken)
    }
}

private fun Application.configureAuthenticatedMcp(authToken: String) {
    install(SSE)
    install(Authentication) {
        bearer("mcp-bearer") {
            authenticate { credential ->
                if (credential.token == authToken) {
                    io.ktor.server.auth.UserIdPrincipal("mcp-client")
                } else {
                    null
                }
            }
        }
    }

    val transports = ConcurrentMap<String, StreamableHttpServerTransport>()

    routing {
        authenticate("mcp-bearer") {
            route("/mcp") {
                sse {
                    val transport = findTransport(call, transports) ?: return@sse
                    transport.handleRequest(this, call)
                }

                post {
                    val transport = getOrCreateTransport(call, transports) ?: return@post
                    transport.handleRequest(null, call)
                }

                delete {
                    val transport = findTransport(call, transports) ?: return@delete
                    transport.handleRequest(null, call)
                }
            }
        }
    }
}

private suspend fun findTransport(
    call: ApplicationCall,
    transports: ConcurrentMap<String, StreamableHttpServerTransport>,
): StreamableHttpServerTransport? {
    val sessionId = call.request.header(MCP_SESSION_ID_HEADER)
    if (sessionId.isNullOrEmpty()) {
        call.respond(HttpStatusCode.BadRequest, "Bad Request: No valid session ID provided")
        return null
    }
    val transport = transports[sessionId]
    if (transport == null) {
        call.respond(HttpStatusCode.NotFound, "Session not found")
        return null
    }
    return transport
}

private suspend fun getOrCreateTransport(
    call: ApplicationCall,
    transports: ConcurrentMap<String, StreamableHttpServerTransport>,
): StreamableHttpServerTransport? {
    val sessionId = call.request.header(MCP_SESSION_ID_HEADER)
    if (sessionId != null) {
        val transport = transports[sessionId]
        if (transport == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
        }
        return transport
    }

    val configuration = StreamableHttpServerTransport.Configuration(
        enableJsonResponse = true,
    )
    val transport = StreamableHttpServerTransport(configuration)

    transport.setOnSessionInitialized { initializedSessionId ->
        transports[initializedSessionId] = transport
    }
    transport.setOnSessionClosed { closedSessionId ->
        transports.remove(closedSessionId)
    }

    val server = createMcpServer()
    server.onClose {
        transport.sessionId?.let { transports.remove(it) }
    }
    server.createSession(transport)

    return transport
}

private fun Application.installCors(authEnabled: Boolean = false) {
    install(CORS) {
        anyHost() // Don't do this in production if possible. Try to limit it.
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowNonSimpleContentTypes = true
        allowHeader("Mcp-Session-Id")
        allowHeader("Mcp-Protocol-Version")
        exposeHeader("Mcp-Session-Id")
        exposeHeader("Mcp-Protocol-Version")
        if (authEnabled) {
            allowHeader(HttpHeaders.Authorization)
        }
    }
}

private fun createMcpServer(): Server {
    val server = Server(
        Implementation(
            name = "simple-streamable-http-server",
            version = "1.0.0",
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = true),
                resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                tools = ServerCapabilities.Tools(listChanged = true),
                logging = ServerCapabilities.Logging,
            ),
        ),
    )

    // Tool: greet
    server.addTool(
        name = "greet",
        description = "A simple greeting tool",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "Name to greet")
                }
            },
            required = listOf("name"),
        ),
    ) { request ->
        val name = request.arguments?.get("name")?.jsonPrimitive?.content ?: "World"
        CallToolResult(content = listOf(TextContent("Hello, $name!")))
    }

    // Tool: multi-greet (demonstrates logging notifications)
    server.addTool(
        name = "multi-greet",
        description = "A tool that sends different greetings with delays between them",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "Name to greet")
                }
            },
            required = listOf("name"),
        ),
        toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
    ) { request ->
        val name = request.arguments?.get("name")?.jsonPrimitive?.content ?: "World"

        sendLoggingMessage(
            LoggingMessageNotification(
                LoggingMessageNotificationParams(
                    level = LoggingLevel.Debug,
                    data = JsonPrimitive("Starting multi-greet for $name")
                )
            )
        )
        delay(1000.milliseconds)

        sendLoggingMessage(
            LoggingMessageNotification(
                LoggingMessageNotificationParams(
                    level = LoggingLevel.Info,
                    data = JsonPrimitive("Sending first greeting to $name")
                )
            )
        )
        delay(1000.milliseconds)

        sendLoggingMessage(
            LoggingMessageNotification(
                LoggingMessageNotificationParams(
                    level = LoggingLevel.Info,
                    data = JsonPrimitive("Sending second greeting to $name")
                )
            )
        )

        CallToolResult(content = listOf(TextContent("Good morning, $name!")))
    }

    // Prompt: greeting-template
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
        val name = request.arguments?.get("name") ?: "World"
        GetPromptResult(
            messages = listOf(
                PromptMessage(
                    role = Role.User,
                    content = TextContent("Please greet $name in a friendly manner."),
                ),
            ),
        )
    }

    // Resource: greeting-resource
    server.addResource(
        uri = "https://example.com/greetings/default",
        name = "Default Greeting",
        description = "A simple greeting resource",
        mimeType = "text/plain",
    ) { request ->
        ReadResourceResult(
            contents = listOf(
                TextResourceContents("Hello, world!", request.uri, "text/plain"),
            ),
        )
    }

    return server
}
