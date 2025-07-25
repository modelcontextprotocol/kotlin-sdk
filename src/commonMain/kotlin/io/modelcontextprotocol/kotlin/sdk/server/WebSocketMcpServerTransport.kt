package io.modelcontextprotocol.kotlin.sdk.server

import io.ktor.http.HttpHeaders
import io.ktor.server.websocket.WebSocketServerSession
import io.modelcontextprotocol.kotlin.sdk.shared.MCP_SUBPROTOCOL
import io.modelcontextprotocol.kotlin.sdk.shared.WebSocketMcpTransport

/**
 * Server-side implementation of the MCP (Model Context Protocol) transport over WebSocket.
 *
 * @property session The WebSocket server session used for communication.
 */
public class WebSocketMcpServerTransport(override val session: WebSocketServerSession) : WebSocketMcpTransport() {
    override suspend fun initializeSession() {
        val subprotocol = session.call.request.headers[HttpHeaders.SecWebSocketProtocol]
        if (subprotocol != MCP_SUBPROTOCOL) {
            error("Invalid subprotocol: $subprotocol, expected $MCP_SUBPROTOCOL")
        }
    }
}
