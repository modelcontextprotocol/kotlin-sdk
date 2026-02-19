@file:Suppress("TooManyFunctions")

package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.KtorDsl
import io.modelcontextprotocol.kotlin.sdk.LIB_VERSION
import io.modelcontextprotocol.kotlin.sdk.shared.IMPLEMENTATION_NAME
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.awaitCancellation

private val logger = KotlinLogging.logger {}

/**
 * Configures the Ktor Application to handle Model Context Protocol (MCP) over WebSocket.
 */
@KtorDsl
public fun Route.mcpWebSocket(block: () -> Server) {
    webSocket {
        mcpWebSocketEndpoint(block)
    }
}

/**
 * Configures the Ktor Application to handle Model Context Protocol (MCP) over WebSocket.
 */
@KtorDsl
public fun Route.mcpWebSocket(path: String, block: () -> Server) {
    webSocket(path) {
        mcpWebSocketEndpoint(block)
    }
}

/**
 * Configures the Ktor Application to handle Model Context Protocol (MCP) over WebSocket.
 */
@KtorDsl
public fun Application.mcpWebSocket(block: () -> Server) {
    install(WebSockets)

    routing {
        mcpWebSocket(block)
    }
}

/**
 * Configures the Ktor Application to handle Model Context Protocol (MCP) over WebSocket at the specified path.
 */
@KtorDsl
public fun Application.mcpWebSocket(path: String, block: () -> Server) {
    install(WebSockets)

    routing {
        mcpWebSocket(path, block)
    }
}

internal suspend fun WebSocketServerSession.mcpWebSocketEndpoint(block: () -> Server) {
    logger.info { "Ktor Server establishing new connection" }
    val transport = createMcpTransport(this)
    val server = block()
    var session: ServerSession? = null
    try {
        session = server.createSession(transport)
        awaitCancellation()
    } catch (_: CancellationException) {
        session?.close()
    }
}

private fun createMcpTransport(webSocketSession: WebSocketServerSession): WebSocketMcpServerTransport =
    WebSocketMcpServerTransport(webSocketSession)

/**
 * Registers a WebSocket route that establishes an MCP (Model Context Protocol) server session.
 *
 * @param options Optional server configuration settings for the MCP server.
 * @param handler A suspend function that defines the server's behavior.
 */
@Deprecated(
    "Use mcpWebSocket with a lambda that returns a Server instance instead",
    ReplaceWith(""),
    DeprecationLevel.WARNING,
)
public fun Route.mcpWebSocket(options: ServerOptions? = null, handler: suspend Server.() -> Unit = {}) {
    webSocket {
        createMcpServer(this, options, handler)
    }
}

/**
 * Registers a WebSocket route at the specified [path] that establishes an MCP server session.
 *
 * @param path The URL path at which to register the WebSocket route.
 * @param options Optional server configuration settings for the MCP server.
 * @param handler A suspend function that defines the server's behavior.
 */
@Deprecated(
    "Use mcpWebSocket with a path and a lambda that returns a Server instance instead",
    ReplaceWith(""),
    DeprecationLevel.WARNING,
)
public fun Route.mcpWebSocket(path: String, options: ServerOptions? = null, handler: suspend Server.() -> Unit = {}) {
    webSocket(path) {
        createMcpServer(this, options, handler)
    }
}

/**
 * Registers a WebSocket route that creates an MCP server transport layer.
 *
 * @param handler A suspend function that defines the behavior of the transport layer.
 */
@Deprecated(
    "Use mcpWebSocket with a lambda that returns a Server instance instead",
    ReplaceWith(""),
    DeprecationLevel.WARNING,
)
public fun Route.mcpWebSocketTransport(handler: suspend WebSocketMcpServerTransport.() -> Unit = {}) {
    webSocket {
        val transport = createMcpTransport(this)
        transport.start()
        handler(transport)
        transport.close()
    }
}

/**
 * Registers a WebSocket route at the specified [path] that creates an MCP server transport layer.
 *
 * @param path The URL path at which to register the WebSocket route.
 * @param handler A suspend function that defines the behavior of the transport layer.
 */
@Deprecated(
    "Use mcpWebSocket with a path and a lambda that returns a Server instance instead",
    ReplaceWith("Routing.mcpWebSocket"),
    DeprecationLevel.WARNING,
)
public fun Route.mcpWebSocketTransport(path: String, handler: suspend WebSocketMcpServerTransport.() -> Unit = {}) {
    webSocket(path) {
        val transport = createMcpTransport(this)
        transport.start()
        handler(transport)
        transport.close()
    }
}

private suspend fun createMcpServer(
    session: WebSocketServerSession,
    options: ServerOptions?,
    handler: suspend Server.() -> Unit,
) {
    val transport = createMcpTransport(session)

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
            ),
        ),
    )

    server.createSession(transport)
    handler(server)
    server.close()
}
