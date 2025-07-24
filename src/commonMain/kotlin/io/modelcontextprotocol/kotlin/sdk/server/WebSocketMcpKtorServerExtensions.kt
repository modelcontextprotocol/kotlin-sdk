package io.modelcontextprotocol.kotlin.sdk.server

import io.ktor.server.routing.Route
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.LIB_VERSION
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.shared.IMPLEMENTATION_NAME
import kotlinx.coroutines.job

/**
 * Registers a WebSocket route that establishes an MCP (Model Context Protocol) server session.
 *
 * @param options Optional server configuration settings for the MCP server.
 * @param handler A suspend function that defines the server's behavior.
 */
public fun Route.mcpWebSocket(
    options: ServerOptions? = null,
    handler: suspend Server.() -> Unit = {},
) {
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
public fun Route.mcpWebSocket(
    path: String,
    options: ServerOptions? = null,
    handler: suspend Server.() -> Unit = {},
) {
    webSocket(path) {
        createMcpServer(this, options, handler)
    }
}

/**
 * Registers a WebSocket route that creates an MCP server transport layer.
 *
 * @param handler A suspend function that defines the behavior of the transport layer.
 */
public fun Route.mcpWebSocketTransport(
    handler: suspend WebSocketMcpServerTransport.() -> Unit = {},
) {
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
public fun Route.mcpWebSocketTransport(
    path: String,
    handler: suspend WebSocketMcpServerTransport.() -> Unit = {},
) {
    webSocket(path) {
        val transport = createMcpTransport(this)
        transport.start()
        handler(transport)
        transport.close()
    }
}


private suspend fun Route.createMcpServer(
    session: WebSocketServerSession,
    options: ServerOptions?,
    handler: suspend Server.() -> Unit,
) {
    val transport = createMcpTransport(session)

    val server = Server(
        serverInfo = Implementation(
            name = IMPLEMENTATION_NAME,
            version = LIB_VERSION
        ),
        options = options ?: ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = null),
                resources = ServerCapabilities.Resources(subscribe = null, listChanged = null),
                tools = ServerCapabilities.Tools(listChanged = null),
            )
        ),
    )

    server.connect(transport)
    handler(server)
    
    // Wait for the WebSocket session to complete instead of closing immediately
    // This keeps the connection alive while the transport handles messages
    try {
        // The transport's message handling loop will keep running until the client disconnects
        session.coroutineContext.job.join()
    } finally {
        server.close()
    }
}

private fun createMcpTransport(
    session: WebSocketServerSession,
): WebSocketMcpServerTransport {
    return WebSocketMcpServerTransport(session)
}
