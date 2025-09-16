package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.sse
import io.ktor.util.collections.ConcurrentMap
import io.ktor.utils.io.KtorDsl
import io.modelcontextprotocol.kotlin.sdk.ErrorCode

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
    val transports = ConcurrentMap<String, SseServerTransport>()

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
    val transports = ConcurrentMap<String, SseServerTransport>()

    install(SSE)

    routing {
        sse("/sse") {
            mcpSseEndpoint("/message", transports, block)
        }

        post("/message") {
            mcpPostEndpoint(transports)
        }
    }
}

@KtorDsl
public fun Application.mcpStreamableHttp(
    enableDnsRebindingProtection: Boolean = false,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore? = null,
    block: RoutingContext.() -> Server,
) {
    val transports = ConcurrentMap<String, StreamableHttpServerTransport>()

    routing {
        post("/mcp") {
            mcpStreamableHttpEndpoint(
                transports,
                enableDnsRebindingProtection,
                allowedHosts,
                allowedOrigins,
                eventStore,
                block,
            )
        }
    }
}

@KtorDsl
public fun Application.mcpStatelessStreamableHttp(
    enableDnsRebindingProtection: Boolean = false,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore? = null,
    block: RoutingContext.() -> Server,
) {
    routing {
        post("/mcp") {
            mcpStatelessStreamableHttpEndpoint(
                enableDnsRebindingProtection,
                allowedHosts,
                allowedOrigins,
                eventStore,
                block,
            )
        }
    }
}

private suspend fun ServerSSESession.mcpSseEndpoint(
    postEndpoint: String,
    transports: ConcurrentMap<String, SseServerTransport>,
    block: ServerSSESession.() -> Server,
) {
    val transport = mcpSseTransport(postEndpoint, transports)

    val server = block()

    server.onClose {
        logger.info { "Server connection closed for sessionId: ${transport.sessionId}" }
        transports.remove(transport.sessionId)
    }

    server.connect(transport)
    logger.debug { "Server connected to transport for sessionId: ${transport.sessionId}" }
}

internal fun ServerSSESession.mcpSseTransport(
    postEndpoint: String,
    transports: ConcurrentMap<String, SseServerTransport>,
): SseServerTransport {
    val transport = SseServerTransport(postEndpoint, this)
    transports[transport.sessionId] = transport

    logger.info { "New SSE connection established and stored with sessionId: ${transport.sessionId}" }

    return transport
}

private suspend fun RoutingContext.mcpStreamableHttpEndpoint(
    transports: ConcurrentMap<String, StreamableHttpServerTransport>,
    enableDnsRebindingProtection: Boolean = false,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore? = null,
    block: RoutingContext.() -> Server,
) {
    val sessionId = this.call.request.header(MCP_SESSION_ID_HEADER)
    val transport = if (sessionId != null && transports.containsKey(sessionId)) {
        transports[sessionId]!!
    } else if (sessionId == null) {
        val transport = StreamableHttpServerTransport(
            enableDnsRebindingProtection = enableDnsRebindingProtection,
            allowedHosts = allowedHosts,
            allowedOrigins = allowedOrigins,
            eventStore = eventStore,
            enableJsonResponse = true,
        )

        transport.setOnSessionInitialized { sessionId ->
            transports[sessionId] = transport

            logger.info { "New StreamableHttp connection established and stored with sessionId: $sessionId" }
        }

        val server = block()
        server.onClose {
            logger.info { "Server connection closed for sessionId: ${transport.sessionId}" }
        }

        server.connect(transport)

        transport
    } else {
        null
    }

    if (transport == null) {
        this.call.reject(
            HttpStatusCode.BadRequest,
            ErrorCode.Unknown(-32000),
            "Bad Request: No valid session ID provided",
        )
        return
    }

    transport.handleRequest(null, this.call)
    logger.debug { "Server connected to transport for sessionId: ${transport.sessionId}" }
}

private suspend fun RoutingContext.mcpStatelessStreamableHttpEndpoint(
    enableDnsRebindingProtection: Boolean = false,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore? = null,
    block: RoutingContext.() -> Server,
) {
    val transport = StreamableHttpServerTransport(
        enableDnsRebindingProtection = enableDnsRebindingProtection,
        allowedHosts = allowedHosts,
        allowedOrigins = allowedOrigins,
        eventStore = eventStore,
        enableJsonResponse = true,
    )
    transport.setSessionIdGenerator(null)

    logger.info { "New stateless StreamableHttp connection established without sessionId" }

    val server = block()

    server.onClose {
        logger.info { "Server connection closed without sessionId" }
    }

    server.connect(transport)

    transport.handleRequest(null, this.call)

    logger.debug { "Server connected to transport without sessionId" }
}

internal suspend fun RoutingContext.mcpPostEndpoint(transports: ConcurrentMap<String, SseServerTransport>) {
    val sessionId: String = call.request.queryParameters["sessionId"]
        ?: run {
            call.respond(HttpStatusCode.BadRequest, "sessionId query parameter is not provided")
            return
        }

    logger.debug { "Received message for sessionId: $sessionId" }

    val transport = transports[sessionId]
    if (transport == null) {
        logger.warn { "Session not found for sessionId: $sessionId" }
        call.respond(HttpStatusCode.NotFound, "Session not found")
        return
    }

    transport.handlePostMessage(call)
    logger.trace { "Message handled for sessionId: $sessionId" }
}
