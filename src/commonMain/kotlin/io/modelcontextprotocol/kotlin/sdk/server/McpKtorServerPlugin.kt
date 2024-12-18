package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*

private val logger = KotlinLogging.logger {}

/**
 * Configures the Ktor Application to handle Model Context Protocol (MCP) over Server-Sent Events (SSE).
 */
public fun Application.MCP(block: () -> Server) {
    val servers = ConcurrentMap<String, Server>()

    install(SSE)
    routing {
        sse("/sse") {
            val transport = SSEServerTransport("/message", this)
            logger.info { "New SSE connection established with sessionId: ${transport.sessionId}" }

            val server = block()

            servers[transport.sessionId] = server
            logger.debug { "Server instance created and stored for sessionId: ${transport.sessionId}" }

            server.onCloseCallback = {
                logger.info { "Server connection closed for sessionId: ${transport.sessionId}" }
                servers.remove(transport.sessionId)
            }

            server.connect(transport)
            logger.debug { "Server connected to transport for sessionId: ${transport.sessionId}" }
        }

        post("/message") {
            val sessionId: String = call.request.queryParameters["sessionId"]!!
            logger.debug { "Received message for sessionId: $sessionId" }

            val transport = servers[sessionId]?.transport as? SSEServerTransport
            if (transport == null) {
                logger.warn { "Session not found for sessionId: $sessionId" }
                call.respond(HttpStatusCode.NotFound, "Session not found")
                return@post
            }

            transport.handlePostMessage(call)
            logger.trace { "Message handled for sessionId: $sessionId" }
        }
    }
}
