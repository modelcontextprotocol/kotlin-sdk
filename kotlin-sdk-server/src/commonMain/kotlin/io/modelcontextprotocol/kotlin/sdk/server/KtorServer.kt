package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.sse
import io.ktor.utils.io.KtorDsl
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

private val logger = KotlinLogging.logger {}

@KtorDsl
public fun Routing.mcp(path: String, block: ServerSSESession.() -> Server) {
    route(path) {
        mcp(block)
    }
}

/**
 * Configures the Ktor Application to handle Model Context Protocol (MCP) over Server-Sent Events (SSE).
 */
@KtorDsl
public fun Routing.mcp(block: ServerSSESession.() -> Server) {
    val transports = atomic(persistentMapOf<String, SseServerTransport>())

    sse {
        mcpSseEndpoint("", transports, block)
    }

    post {
        mcpPostEndpoint(transports)
    }
}

@Suppress("FunctionName")
@Deprecated("Use mcp() instead", ReplaceWith("mcp(block)"), DeprecationLevel.WARNING)
public fun Application.MCP(block: ServerSSESession.() -> Server) {
    mcp(block)
}

@KtorDsl
public fun Application.mcp(block: ServerSSESession.() -> Server) {
    install(SSE)

    routing {
        mcp(block)
    }
}

internal suspend fun ServerSSESession.mcpSseEndpoint(
    postEndpoint: String,
    transports: AtomicRef<PersistentMap<String, SseServerTransport>>,
    block: ServerSSESession.() -> Server,
) {
    val transport = mcpSseTransport(postEndpoint, transports)

    val server = block()

    server.onClose {
        logger.info { "Server connection closed for sessionId: ${transport.sessionId}" }
        transports.update { it.remove(transport.sessionId) }
    }

    server.connectSession(transport)

    logger.debug { "Server connected to transport for sessionId: ${transport.sessionId}" }
}

internal fun ServerSSESession.mcpSseTransport(
    postEndpoint: String,
    transports: AtomicRef<PersistentMap<String, SseServerTransport>>,
): SseServerTransport {
    val transport = SseServerTransport(postEndpoint, this)
    transports.update { it.put(transport.sessionId, transport) }
    logger.info { "New SSE connection established and stored with sessionId: ${transport.sessionId}" }

    return transport
}

internal suspend fun RoutingContext.mcpPostEndpoint(
    transports: AtomicRef<PersistentMap<String, SseServerTransport>>,
) {
    val sessionId: String = call.request.queryParameters["sessionId"]
        ?: run {
            call.respond(HttpStatusCode.BadRequest, "sessionId query parameter is not provided")
            return
        }

    logger.debug { "Received message for sessionId: $sessionId" }

    val transport = transports.value[sessionId]
    if (transport == null) {
        logger.warn { "Session not found for sessionId: $sessionId" }
        call.respond(HttpStatusCode.NotFound, "Session not found")
        return
    }

    transport.handlePostMessage(call)
    logger.trace { "Message handled for sessionId: $sessionId" }
}
