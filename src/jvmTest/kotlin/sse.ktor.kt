import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.sse
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import io.modelcontextprotocol.kotlin.sdk.server.SSEServerTransport
import kotlinx.coroutines.CompletableDeferred
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.LIB_VERSION
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.SESSION_ID_PARAM
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.shared.IMPLEMENTATION_NAME

typealias IncomingHandler = suspend RoutingContext.(forward: suspend () -> Unit) -> Unit

fun Route.mcpSse(
    options: ServerOptions? = null,
    incomingPath: String = "",
    incomingHandler: (IncomingHandler)? = null,
    handler: suspend Server.() -> Unit = {},
) {
    sse {
        createMcpServer(this, incomingPath, options, handler)
    }

    setupPostRoute(incomingPath, incomingHandler)
}

fun Route.mcpSse(
    path: String,
    incomingPath: String = path,
    options: ServerOptions? = null,
    incomingHandler: (IncomingHandler)? = null,
    handler: suspend Server.() -> Unit = {},
) {
    sse(path) {
        createMcpServer(this, incomingPath, options, handler)
    }

    setupPostRoute(incomingPath, incomingHandler)
}

fun Route.mcpSseTransport(
    incomingPath: String = "",
    incomingHandler: (IncomingHandler)? = null,
    handler: suspend SSEServerTransport.() -> Unit = {},
) {
    sse {
        val transport = createMcpTransport(this, incomingPath)
        handler(transport)
        transport.start()
        transport.close()
    }

    setupPostRoute(incomingPath, incomingHandler)
}

fun Route.mcpSseTransport(
    path: String,
    incomingPath: String = path,
    incomingHandler: (IncomingHandler)? = null,
    handler: suspend SSEServerTransport.() -> Unit = {},
) {
    sse(path) {
        val transport = createMcpTransport(this, incomingPath)
        transport.start()
        handler(transport)
        transport.close()
    }

    setupPostRoute(incomingPath, incomingHandler)
}

internal val McpServersKey = AttributeKey<Attributes>("mcp-servers")

private fun String.asAttributeKey() = AttributeKey<SSEServerTransport>(this)

private suspend fun Route.forwardMcpMessage(call: ApplicationCall) {
    val sessionId = call.request.queryParameters[SESSION_ID_PARAM]
        ?.asAttributeKey()
        ?: run {
            call.sessionNotFound()
            return
        }

    application.attributes.getOrNull(McpServersKey)
        ?.get(sessionId)
        ?.handlePostMessage(call)
        ?: call.sessionNotFound()
}

private suspend fun ApplicationCall.sessionNotFound() {
    respondText("Session not found", status = HttpStatusCode.NotFound)
}

private fun Route.setupPostRoute(incomingPath: String, incomingHandler: IncomingHandler?) {
    post(incomingPath) {
        if (incomingHandler != null) {
            incomingHandler {
                forwardMcpMessage(call)
            }
        } else {
            forwardMcpMessage(call)
        }
    }
}

private suspend fun Route.createMcpServer(
    session: ServerSSESession,
    incomingPath: String,
    options: ServerOptions?,
    handler: suspend Server.() -> Unit = {},
) {
    val transport = createMcpTransport(session, incomingPath)

    val closed = CompletableDeferred<Unit>()

    val server = Server(
        serverInfo = Implementation(
            name = IMPLEMENTATION_NAME,
            version = LIB_VERSION,
        ),
        options = options ?: ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = null),
                resources = ServerCapabilities.Resources(subscribe = null, listChanged = null),
                tools = ServerCapabilities.Tools(listChanged = null),
            )
        ),
        onCloseCallback = {
            closed.complete(Unit)
        },
    )

    server.connect(transport)
    handler(server)
    server.close()
}

private fun Route.createMcpTransport(
    session: ServerSSESession,
    incomingPath: String,
): SSEServerTransport {
    val transport = SSEServerTransport(
        endpoint = incomingPath,
        session = session,
    )

    application.attributes
        .computeIfAbsent(McpServersKey) { Attributes(concurrent = true) }
        .put(transport.sessionId.asAttributeKey(), transport)

    return transport
}
