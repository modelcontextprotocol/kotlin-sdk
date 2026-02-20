@file:Suppress("TooManyFunctions")

package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.MissingApplicationPluginException
import io.ktor.server.application.install
import io.ktor.server.application.plugin
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.application
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.sse
import io.ktor.utils.io.KtorDsl
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import kotlinx.coroutines.awaitCancellation

private val logger = KotlinLogging.logger {}

/**
 * Configuration for Streamable HTTP MCP endpoints.
 *
 * @property enableDnsRebindingProtection Whether to enable DNS rebinding protection by
 *   validating the `Host` header against [allowedHosts].
 * @property allowedHosts Allowed hosts for DNS rebinding validation; only consulted when
 *   [enableDnsRebindingProtection] is `true`.
 * @property allowedOrigins Allowed origins for cross-origin request validation.
 * @property eventStore An optional [EventStore] for persistent, resumable sessions.
 */
@KtorDsl
public class McpStreamableHttpConfig {
    public var enableDnsRebindingProtection: Boolean = false
    public var allowedHosts: List<String>? = null
    public var allowedOrigins: List<String>? = null
    public var eventStore: EventStore? = null
}

/**
 * Registers a server-sent events (SSE) route at the specified path.
 *
 * @param path the URL path to register the route for SSE.
 * @param block the block of code that defines the server's behavior for the SSE session.
 */
@KtorDsl
public fun Route.mcp(path: String, block: ServerSSESession.() -> Server) {
    route(path) {
        mcp(block)
    }
}

/**
 * Configures the Ktor Application to handle Model Context Protocol (MCP) over Server-Sent Events (SSE).
 *
 * **Precondition:** the [SSE] plugin must be installed on the application before calling this function.
 * Use [Application.mcp] if you want SSE to be installed automatically.
 *
 * @throws IllegalStateException if the [SSE] plugin is not installed.
 */
@KtorDsl
public fun Route.mcp(block: ServerSSESession.() -> Server) {
    try {
        plugin(SSE)
    } catch (e: MissingApplicationPluginException) {
        throw IllegalStateException(
            "The SSE plugin must be installed before registering MCP routes. " +
                "Add `install(SSE)` to your application configuration, " +
                "or use Application.mcp() which installs it automatically.",
            e,
        )
    }

    val transportManager = TransportManager<SseServerTransport>()

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

/**
 * Registers Streamable HTTP MCP endpoints at the specified [path] as a [Route] extension.
 *
 * This allows placing the endpoints inside an [Route.authenticate] block.
 *
 * **Precondition:** the [SSE] plugin must be installed on the application before calling this function.
 * Use [Application.mcpStreamableHttp] if you want SSE to be installed automatically.
 *
 * @param path the URL path to register the routes.
 * @param config optional configuration for DNS rebinding protection, CORS, and event store.
 * @throws IllegalStateException if the [SSE] plugin is not installed.
 */
@KtorDsl
public fun Route.mcpStreamableHttp(
    path: String,
    config: McpStreamableHttpConfig.() -> Unit = {},
    serverFactory: RoutingContext.() -> Server,
) {
    route(path) {
        mcpStreamableHttp(
            config = config,
            serverFactory = serverFactory,
        )
    }
}

/**
 * Registers Streamable HTTP MCP endpoints on the current route.
 *
 * This allows placing the endpoints inside an [Route.authenticate] block.
 * Each call creates its own session namespace; registering this endpoint twice on the same
 * route tree produces two independent session spaces.
 *
 * **Precondition:** the [SSE] plugin must be installed on the application before calling this function.
 * Use [Application.mcpStreamableHttp] if you want SSE to be installed automatically.
 *
 * @param config optional configuration for DNS rebinding protection, CORS, and event store.
 * @throws IllegalStateException if the [SSE] plugin is not installed.
 */
@KtorDsl
public fun Route.mcpStreamableHttp(
    config: McpStreamableHttpConfig.() -> Unit = {},
    serverFactory: RoutingContext.() -> Server,
) {
    try {
        application.plugin(SSE)
    } catch (e: MissingApplicationPluginException) {
        throw IllegalStateException(
            "The SSE plugin must be installed before registering MCP routes. " +
                "Add `install(SSE)` to your application configuration, " +
                "or use Application.mcpStreamableHttp() which installs it automatically.",
            e,
        )
    }

    val mcpConfig = McpStreamableHttpConfig().apply(config)
    val transportManager = TransportManager<StreamableHttpServerTransport>()

    sse {
        val transport = call.resolveStreamableTransport(transportManager) ?: return@sse
        transport.handleRequest(this, call)
    }

    post {
        val transport = streamableTransport(
            transportManager = transportManager,
            config = mcpConfig,
            serverFactory = serverFactory,
        )
            ?: return@post

        transport.handleRequest(null, call)
    }

    delete {
        val transport = call.resolveStreamableTransport(transportManager) ?: return@delete
        transport.handleRequest(null, call)
    }
}

@KtorDsl
public fun Application.mcpStreamableHttp(
    path: String = "/mcp",
    config: McpStreamableHttpConfig.() -> Unit = {},
    serverFactory: RoutingContext.() -> Server,
) {
    install(SSE)

    routing {
        mcpStreamableHttp(
            path = path,
            config = config,
            serverFactory = serverFactory,
        )
    }
}

/**
 * Registers stateless Streamable HTTP MCP endpoints at the specified [path] as a [Route] extension.
 *
 * This allows placing the endpoints inside an [Route.authenticate] block.
 * Unlike [mcpStreamableHttp], each request creates a fresh server instance with no session
 * persistence between calls.
 *
 * **Precondition:** the [SSE] plugin must be installed on the application before calling this function.
 * Use [Application.mcpStatelessStreamableHttp] if you want SSE to be installed automatically.
 *
 * @param path the URL path to register the routes.
 * @param config optional configuration for DNS rebinding protection, CORS, and event store.
 * @throws IllegalStateException if the [SSE] plugin is not installed.
 */
@KtorDsl
public fun Route.mcpStatelessStreamableHttp(
    path: String,
    config: McpStreamableHttpConfig.() -> Unit = {},
    serverFactory: RoutingContext.() -> Server,
) {
    route(path) {
        mcpStatelessStreamableHttp(
            config = config,
            serverFactory = serverFactory,
        )
    }
}

/**
 * Registers stateless Streamable HTTP MCP endpoints on the current route.
 *
 * This allows placing the endpoints inside an [Route.authenticate] block.
 * Unlike [mcpStreamableHttp], each request creates a fresh server instance with no session
 * persistence between calls.
 *
 * **Precondition:** the [SSE] plugin must be installed on the application before calling this function.
 * Use [Application.mcpStatelessStreamableHttp] if you want SSE to be installed automatically.
 *
 * @param config optional configuration for DNS rebinding protection, CORS, and event store.
 * @throws IllegalStateException if the [SSE] plugin is not installed.
 */
@KtorDsl
public fun Route.mcpStatelessStreamableHttp(
    config: McpStreamableHttpConfig.() -> Unit = {},
    serverFactory: RoutingContext.() -> Server,
) {
    try {
        application.plugin(SSE)
    } catch (e: MissingApplicationPluginException) {
        throw IllegalStateException(
            "The SSE plugin must be installed before registering MCP routes. " +
                "Add `install(SSE)` to your application configuration, " +
                "or use Application.mcpStatelessStreamableHttp() which installs it automatically.",
            e,
        )
    }

    val mcpConfig = McpStreamableHttpConfig().apply(config)

    post {
        mcpStatelessStreamableHttpEndpoint(
            enableDnsRebindingProtection = mcpConfig.enableDnsRebindingProtection,
            allowedHosts = mcpConfig.allowedHosts,
            allowedOrigins = mcpConfig.allowedOrigins,
            eventStore = mcpConfig.eventStore,
            serverFactory = serverFactory,
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

@KtorDsl
public fun Application.mcpStatelessStreamableHttp(
    path: String = "/mcp",
    config: McpStreamableHttpConfig.() -> Unit = {},
    serverFactory: RoutingContext.() -> Server,
) {
    install(SSE)

    routing {
        mcpStatelessStreamableHttp(
            path = path,
            config = config,
            serverFactory = serverFactory,
        )
    }
}

private suspend fun ServerSSESession.mcpSseEndpoint(
    postEndpoint: String,
    transportManager: TransportManager<SseServerTransport>,
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

private fun ServerSSESession.mcpSseTransport(
    postEndpoint: String,
    transportManager: TransportManager<SseServerTransport>,
): SseServerTransport {
    val transport = SseServerTransport(postEndpoint, this)
    transportManager.addTransport(transport.sessionId, transport)
    logger.info { "New SSE connection established and stored with sessionId: ${transport.sessionId}" }

    return transport
}

private suspend fun RoutingContext.mcpStatelessStreamableHttpEndpoint(
    enableDnsRebindingProtection: Boolean = false,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore? = null,
    serverFactory: RoutingContext.() -> Server,
) {
    val transport = StreamableHttpServerTransport(
        enableDnsRebindingProtection = enableDnsRebindingProtection,
        allowedHosts = allowedHosts,
        allowedOrigins = allowedOrigins,
        eventStore = eventStore,
        enableJsonResponse = true,
    ).also { it.setSessionIdGenerator(null) }

    logger.info { "New stateless StreamableHttp connection established without sessionId" }

    val server = serverFactory()
    server.onClose { logger.info { "Server connection closed without sessionId" } }
    server.createSession(transport)

    transport.handleRequest(null, this.call)
    logger.debug { "Server connected to transport without sessionId" }
}

private suspend fun RoutingContext.mcpPostEndpoint(transportManager: TransportManager<SseServerTransport>) {
    val sessionId: String = call.request.queryParameters["sessionId"] ?: run {
        call.respond(HttpStatusCode.BadRequest, "sessionId query parameter is not provided")
        return
    }

    logger.debug { "Received message for sessionId: $sessionId" }

    val transport = transportManager.getTransport(sessionId)
    if (transport == null) {
        logger.warn { "Session not found for sessionId: $sessionId" }
        call.respond(HttpStatusCode.NotFound, "Session not found")
        return
    }

    transport.handlePostMessage(call)
    logger.trace { "Message handled for sessionId: $sessionId" }
}

private fun ApplicationRequest.sessionId(): String? = header(MCP_SESSION_ID_HEADER)

private suspend fun ApplicationCall.resolveStreamableTransport(
    transportManager: TransportManager<StreamableHttpServerTransport>,
): StreamableHttpServerTransport? {
    val sessionId = request.sessionId()
    if (sessionId.isNullOrEmpty()) {
        reject(
            HttpStatusCode.BadRequest,
            RPCError.ErrorCode.CONNECTION_CLOSED,
            "Bad Request: No valid session ID provided",
        )
        return null
    }

    return transportManager.getTransport(sessionId) ?: run {
        reject(
            HttpStatusCode.NotFound,
            RPCError.ErrorCode.CONNECTION_CLOSED,
            "Session not found",
        )
        null
    }
}

private suspend fun RoutingContext.streamableTransport(
    transportManager: TransportManager<StreamableHttpServerTransport>,
    config: McpStreamableHttpConfig,
    serverFactory: RoutingContext.() -> Server,
): StreamableHttpServerTransport? {
    val sessionId = call.request.sessionId()
    if (sessionId != null) {
        val transport = transportManager.getTransport(sessionId)
        return transport ?: call.resolveStreamableTransport(transportManager)
    }

    val transport = StreamableHttpServerTransport(
        enableDnsRebindingProtection = config.enableDnsRebindingProtection,
        allowedHosts = config.allowedHosts,
        allowedOrigins = config.allowedOrigins,
        eventStore = config.eventStore,
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

    val server = serverFactory()
    server.onClose {
        transport.sessionId?.let { transportManager.removeTransport(it) }
        logger.info { "Server connection closed for sessionId: ${transport.sessionId}" }
    }
    server.createSession(transport)

    return transport
}
