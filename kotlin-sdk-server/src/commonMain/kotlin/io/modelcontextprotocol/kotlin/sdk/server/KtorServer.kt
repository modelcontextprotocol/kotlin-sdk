package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.sse
import io.ktor.utils.io.KtorDsl
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.awaitCancellation

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

@KtorDsl
public fun Application.mcpStreamableHttp(
    enableDnsRebindingProtection: Boolean = false,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore? = null,
    block: RoutingContext.() -> Server,
) {
    install(SSE)

    val transportManager = TransportManager()

    routing {
        route("/mcp") {
            sse {
                val transport = existingStreamableTransport(call, transportManager) ?: return@sse
                transport.handleRequest(this, call)
            }

            post {
                val transport = streamableTransport(
                    transportManager = transportManager,
                    enableDnsRebindingProtection = enableDnsRebindingProtection,
                    allowedHosts = allowedHosts,
                    allowedOrigins = allowedOrigins,
                    eventStore = eventStore,
                    block = block,
                )
                    ?: return@post

                transport.handleRequest(null, call)
            }

            delete {
                val transport = existingStreamableTransport(call, transportManager) ?: return@delete
                transport.handleRequest(null, call)
            }
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
    install(SSE)

    routing {
        route("/mcp") {
            post {
                mcpStatelessStreamableHttpEndpoint(
                    enableDnsRebindingProtection = enableDnsRebindingProtection,
                    allowedHosts = allowedHosts,
                    allowedOrigins = allowedOrigins,
                    eventStore = eventStore,
                    block = block,
                )
            }
            get {
                call.reject(
                    HttpStatusCode.MethodNotAllowed,
                    RPCError.ErrorCode.CONNECTION_CLOSED,
                    "Method not allowed.",
                )
            }
            delete {
                call.reject(
                    HttpStatusCode.MethodNotAllowed,
                    RPCError.ErrorCode.CONNECTION_CLOSED,
                    "Method not allowed.",
                )
            }
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
    ).also { it.setSessionIdGenerator(null) }

    logger.info { "New stateless StreamableHttp connection established without sessionId" }

    val server = block()
    server.onClose { logger.info { "Server connection closed without sessionId" } }
    server.createSession(transport)

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

private fun ApplicationRequest.sessionId(): String? = header(MCP_SESSION_ID_HEADER)

private suspend fun existingStreamableTransport(
    call: ApplicationCall,
    transportManager: TransportManager,
): StreamableHttpServerTransport? {
    val sessionId = call.request.sessionId()
    if (sessionId.isNullOrEmpty()) {
        call.reject(
            HttpStatusCode.BadRequest,
            RPCError.ErrorCode.CONNECTION_CLOSED,
            "Bad Request: No valid session ID provided",
        )
        return null
    }

    val transport = transportManager.getTransport(sessionId) as? StreamableHttpServerTransport
    if (transport == null) {
        call.reject(
            HttpStatusCode.NotFound,
            RPCError.ErrorCode.CONNECTION_CLOSED,
            "Session not found",
        )
        return null
    }

    return transport
}

private suspend fun RoutingContext.streamableTransport(
    transportManager: TransportManager,
    enableDnsRebindingProtection: Boolean,
    allowedHosts: List<String>?,
    allowedOrigins: List<String>?,
    eventStore: EventStore?,
    block: RoutingContext.() -> Server,
): StreamableHttpServerTransport? {
    val sessionId = call.request.sessionId()
    if (sessionId != null) {
        val transport = transportManager.getTransport(sessionId) as? StreamableHttpServerTransport
        return transport ?: existingStreamableTransport(call, transportManager)
    }

    val transport = StreamableHttpServerTransport(
        enableDnsRebindingProtection = enableDnsRebindingProtection,
        allowedHosts = allowedHosts,
        allowedOrigins = allowedOrigins,
        eventStore = eventStore,
        enableJsonResponse = true,
    )

    transport.setOnSessionInitialized { initializedSessionId ->
        transportManager.addTransport(initializedSessionId, transport)
        logger.info { "New StreamableHttp connection established and stored with sessionId: $initializedSessionId" }
    }

    transport.setOnSessionClosed { closedSession ->
        transportManager.removeTransport(closedSession)
        logger.info { "Closed StreamableHttp connection and removed sessionId: $closedSession" }
    }

    val server = block()
    server.onClose {
        transport.sessionId?.let { transportManager.removeTransport(it) }
        logger.info { "Server connection closed for sessionId: ${transport.sessionId}" }
    }
    server.createSession(transport)

    return transport
}
