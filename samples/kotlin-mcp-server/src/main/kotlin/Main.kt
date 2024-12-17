import org.jetbrains.kotlinx.mcp.CallToolResult
import org.jetbrains.kotlinx.mcp.GetPromptResult
import org.jetbrains.kotlinx.mcp.Implementation
import org.jetbrains.kotlinx.mcp.PromptArgument
import org.jetbrains.kotlinx.mcp.PromptMessage
import org.jetbrains.kotlinx.mcp.ReadResourceResult
import org.jetbrains.kotlinx.mcp.Role
import org.jetbrains.kotlinx.mcp.ServerCapabilities
import org.jetbrains.kotlinx.mcp.TextContent
import org.jetbrains.kotlinx.mcp.TextResourceContents
import org.jetbrains.kotlinx.mcp.Tool
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.mcp.server.MCP
import org.jetbrains.kotlinx.mcp.server.SSEServerTransport
import org.jetbrains.kotlinx.mcp.server.Server
import org.jetbrains.kotlinx.mcp.server.ServerOptions
import org.jetbrains.kotlinx.mcp.server.StdioServerTransport

/**
 * Start sse-server mcp on port 3001.
 *
 * @param args
 * - "--stdio": Runs an MCP server using standard input/output.
 * - "--sse-server-ktor <port>": Runs an SSE MCP server using Ktor plugin (default if no argument is provided).
 * - "--sse-server <port>": Runs an SSE MCP server with a plain configuration.
 */
fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: "--sse-server-ktor"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 3001
    when (command) {
        "--stdio" -> `run mcp server using stdio`()
        "--sse-server-ktor" -> `run sse mcp server using Ktor plugin`(port)
        "--sse-server" -> `run sse mcp server with plain configuration`(port)
        else -> {
            System.err.println("Unknown command: $command")
        }
    }
}

fun configureServer(): Server {
    val def = CompletableDeferred<Unit>()

    val server = Server(
        Implementation(
            name = "mcp-kotlin test server",
            version = "0.1.0"
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = true),
                resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                tools = ServerCapabilities.Tools(listChanged = true),
            )
        ),
        onCloseCallback = {
            def.complete(Unit)
        }
    )

    server.addPrompt(
        name = "Kotlin Developer",
        description = "Develop small kotlin applications",
        arguments = listOf(
            PromptArgument(
                name = "Project Name",
                description = "Project name for the new project",
                required = true
            )
        )
    ) { request ->
        GetPromptResult(
            "Description for ${request.name}",
            messages = listOf(
                PromptMessage(
                    role = Role.user,
                    content = TextContent("Develop a kotlin project named <name>${request.arguments?.get("Project Name")}</name>")
                )
            )
        )
    }

    // Add a tool
    server.addTool(
        name = "Test io.modelcontextprotocol.kotlin.sdk.Tool",
        description = "A test tool",
        inputSchema = Tool.Input()
    ) { request ->
        CallToolResult(
            content = listOf(TextContent("Hello, world!"))
        )
    }

    // Add a resource
    server.addResource(
        uri = "https://search.com/",
        name = "Web Search",
        description = "Web search engine",
        mimeType = "text/html"
    ) { request ->
        ReadResourceResult(
            contents = listOf(
                TextResourceContents("Placeholder content for ${request.uri}", request.uri, "text/html")
            )
        )
    }

    return server
}

fun `run mcp server using stdio`() {
    // Note: The server will handle listing prompts, tools, and resources automatically.
    // The handleListResourceTemplates will return empty as defined in the Server code.
    val server = configureServer()
    val transport = StdioServerTransport()

    runBlocking {
        server.connect(transport)
        println("Server running on stdio")
        val done = Job()
        server.onCloseCallback = {
            done.complete()
        }
        done.join()
        println("Server closed")
    }
}

fun `run sse mcp server with plain configuration`(port: Int): Unit = runBlocking {
    val servers = ConcurrentMap<String, Server>()
    println("Starting sse server on port $port. ")
    println("Use inspector to connect to the http://localhost:$port/sse")

    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(SSE)
        routing {
            sse("/sse") {
                val transport = SSEServerTransport("/message", this)
                val server = configureServer()

                // For SSE, you can also add prompts/tools/resources if needed:
                // server.addTool(...), server.addPrompt(...), server.addResource(...)

                servers[transport.sessionId] = server

                server.onCloseCallback = {
                    println("Server closed")
                    servers.remove(transport.sessionId)
                }

                server.connect(transport)
            }
            post("/message") {
                println("Received Message")
                val sessionId: String = call.request.queryParameters["sessionId"]!!
                val transport = servers[sessionId]?.transport as? SSEServerTransport
                if (transport == null) {
                    call.respond(HttpStatusCode.NotFound, "Session not found")
                    return@post
                }

                transport.handlePostMessage(call)
            }
        }
    }.start(wait = true)
}

/**
 * Starts an SSE (Server Sent Events) MCP server using the Ktor framework and the specified port.
 *
 * The url can be accessed in the MCP inspector at [http://localhost:$port]
 *
 * @param port The port number on which the SSE MCP server will listen for client connections.
 * @return Unit This method does not return a value.
 */
fun `run sse mcp server using Ktor plugin`(port: Int): Unit = runBlocking {
    println("Starting sse server on port $port")
    println("Use inspector to connect to the http://localhost:$port/sse")

    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        MCP {
            return@MCP configureServer()
        }
    }
}