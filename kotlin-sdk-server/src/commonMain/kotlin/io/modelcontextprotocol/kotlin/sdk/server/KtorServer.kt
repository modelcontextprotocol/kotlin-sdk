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
import io.ktor.utils.io.KtorDsl
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.awaitCancellation
import io.modelcontextprotocol.kotlin.sdk.ErrorCode
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport

private val logger = KotlinLogging.logger {}

internal class TransportManager(transports: Map<String, AbstractTransport> = emptyMap()) {
    private val transports: AtomicRef<PersistentMap<String, AbstractTransport>> = atomic(transports.toPersistentMap())

    fun hasTransport(sessionId: String): Boolean = transports.value.containsKey(sessionId)

    fun getTransport(sessionId: String): AbstractTransport? = transports.value[sessionId]

    fun addTransport(sessionId: String, transport: AbstractTransport) {
        transports.update { it.put(sessionId, transport) }
    }

    fun removeTransport(sessionId: String) {
        transports.update { it.remove(sessionId) }
    }
}

@KtorDsl
public fun Routing.mcp(path: String, block: ServerSSESession.() -> Server) {
    route(path) {
        mcp(block)
    }
}

/*
* Configures the Ktor Application to handle Model Context Protocol (MCP) over Server-Sent Events (SSE).
*/
@KtorDsl
public fun Routing.mcp(block: ServerSSESession.() -> Server) {
    val transportManager = TransportManager()

    sse {
        mcpSseEndpoint("", transportManager, block)
    }

    post {
        mcpPostEndpoint(transportManager)
    }
}

@KtorDsl
public fun Application.mcp(block: ServerSSESession.() -> Server) {
    install(SSE)

    routing {
        mcp(block)
    }
}

/*
* Configures the Ktor Application to handle Model Context Protocol (MCP) over Streamable Http.
* It currently only works with JSON response.
*/
@KtorDsl
public fun Application.mcpStreamableHttp(
    enableDnsRebindingProtection: Boolean = false,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore? = null,
    block: RoutingContext.() -> Server,
) {
    val transportManager = TransportManager()

    routing {
        post("/mcp") {
            mcpStreamableHttpEndpoint(
                transportManager,
                enableDnsRebindingProtection,
                allowedHosts,
                allowedOrigins,
                eventStore,
                block,
            )
        }
    }
}

/*
* Configures the Ktor Application to handle Model Context Protocol (MCP) over stateless Streamable Http.
* It currently only works with JSON response.
*/
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

internal suspend fun ServerSSESession.mcpSseEndpoint(
    postEndpoint: String,
    transportManager: TransportManager,
    block: ServerSSESession.() -> Server,
) {
    val transport = mcpSseTransport(postEndpoint, transportManager)

    val server = block()

    server.onClose {
        logger.info { "Server connection closed for sessionId: ${transport.sessionId}" }
        transportManager.removeTransport(transport.sessionId)
    }

    server.createSession(transport)

    logger.debug { "Server connected to transport for sessionId: ${transport.sessionId}" }

    awaitCancellation()
}

internal fun ServerSSESession.mcpSseTransport(
    postEndpoint: String,
    transportManager: TransportManager,
): SseServerTransport {
    val transport = SseServerTransport(postEndpoint, this)
    transportManager.addTransport(transport.sessionId, transport)
    logger.info { "New SSE connection established and stored with sessionId: ${transport.sessionId}" }

    return transport
}

internal suspend fun RoutingContext.mcpStreamableHttpEndpoint(
    transportManager: TransportManager,
    enableDnsRebindingProtection: Boolean = false,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore? = null,
    block: RoutingContext.() -> Server,
) {
    val sessionId = this.call.request.header(MCP_SESSION_ID_HEADER)
    val transport = if (sessionId != null && transportManager.hasTransport(sessionId)) {
        transportManager.getTransport(sessionId)
    } else if (sessionId == null) {
        val transport = StreamableHttpServerTransport(
            enableDnsRebindingProtection = enableDnsRebindingProtection,
            allowedHosts = allowedHosts,
            allowedOrigins = allowedOrigins,
            eventStore = eventStore,
            enableJsonResponse = true,
        )

        transport.setOnSessionInitialized { sessionId ->
            transportManager.addTransport(sessionId, transport)

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

    (transport as StreamableHttpServerTransport).handleRequest(null, this.call)
    logger.debug { "Server connected to transport for sessionId: ${transport.sessionId}" }
}

internal suspend fun RoutingContext.mcpStatelessStreamableHttpEndpoint(
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

internal suspend fun RoutingContext.mcpPostEndpoint(transportManager: TransportManager) {
    val sessionId: String = call.request.queryParameters["sessionId"] ?: run {
        call.respond(HttpStatusCode.BadRequest, "sessionId query parameter is not provided")
        return
    }

    logger.debug { "Received message for sessionId: $sessionId" }

    val transport = transportManager.getTransport(sessionId) as SseServerTransport?
    if (transport == null) {
        logger.warn { "Session not found for sessionId: $sessionId" }
        call.respond(HttpStatusCode.NotFound, "Session not found")
        return
    }

    transport.handlePostMessage(call)
    logger.trace { "Message handled for sessionId: $sessionId" }
}
