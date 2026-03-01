@file:Suppress("TooManyFunctions")

package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.MissingApplicationPluginException
import io.ktor.server.application.install
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
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
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import kotlinx.coroutines.awaitCancellation

private val logger = KotlinLogging.logger {}

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

/**
 * Configures the server to use Server-Sent Events (SSE) and sets up routing with the provided configuration block.
 *
 * @param block A lambda with receiver of type [ServerSSESession] that configures
 * SSE session and returns a [Server] instance.
 */
@KtorDsl
public fun Application.mcp(block: ServerSSESession.() -> Server) {
    install(SSE)

    routing {
        mcp(block)
    }
}

/**
 * Configures the application to handle HTTP-based streamable communications using the MCP protocol.
 *
 * This method sets up server-sent events (SSE) along with HTTP POST and DELETE endpoints
 * for managing server-side streaming transports. It provides routes for creating, using,
 * and removing transports using the specified path, configuration, and handler block.
 *
 * @param path The base route path for the MCP protocol. Defaults to "/mcp".
 * @param configuration The transport configuration for the streamable HTTP server.
 * @param block A handler block that provides the routing context to initialize the server.
 */
private fun Application.mcpStreamableHttp(
    path: String = "/mcp",
    configuration: StreamableHttpServerTransport.Configuration,
    block: RoutingContext.() -> Server,
) {
    install(SSE)

    val transportManager = TransportManager<StreamableHttpServerTransport>()

    routing {
        route(path) {
            sse {
                val transport = existingStreamableTransport(call, transportManager) ?: return@sse
                transport.handleRequest(this, call)
            }

            post {
                val transport = streamableTransport(
                    transportManager = transportManager,
                    configuration = configuration,
                    block = block,
                ) ?: return@post

                transport.handleRequest(null, call)
            }

            delete {
                val transport = existingStreamableTransport(call, transportManager) ?: return@delete
                transport.handleRequest(null, call)
            }
        }
    }
}

/**
 * Configures an HTTP endpoint to support a streamable Message Channel Protocol (MCP) interaction.
 * This function is used to set up routing for an incoming streamable connection and optionally
 * configure settings such as DNS rebinding protection, allowed hosts, allowed origins, and event storage.
 *
 * @param path The base path for the MCP endpoint. Defaults to "/mcp".
 * @param enableDnsRebindingProtection Enables DNS rebinding attack protection for the endpoint. Defaults to false.
 * @param allowedHosts A list of hostnames allowed to access the endpoint. If null, no restrictions are applied.
 * @param allowedOrigins A list of origins allowed to perform cross-origin requests (CORS).
 *          If null, no restriction is applied.
 * @param eventStore An optional [EventStore] instance to enable resumable event stream functionality.
 *          Allows storing and replaying events.
 * @param block Lambda to define the routing logic using a [RoutingContext] to configure the behavior of the server.
 */
@KtorDsl
@Suppress("LongParameterList")
public fun Application.mcpStreamableHttp(
    path: String = "/mcp",
    enableDnsRebindingProtection: Boolean = false,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore? = null,
    block: RoutingContext.() -> Server,
) {
    mcpStreamableHttp(
        path = path,
        configuration = StreamableHttpServerTransport.Configuration(
            enableDnsRebindingProtection = enableDnsRebindingProtection,
            allowedHosts = allowedHosts,
            allowedOrigins = allowedOrigins,
            eventStore = eventStore,
            enableJsonResponse = true,
        ),
        block = block,
    )
}

/**
 * Configures a route to handle stateless, streamable HTTP requests for the MCP (Message Communication Protocol).
 *
 * This method sets up a predefined HTTP path and specifies POST as the allowed HTTP method.
 * Any other methods, such as GET or DELETE, will be rejected with an appropriate error response.
 * The configuration and server behavior are customized using the provided parameters.
 *
 * @param path The URL path where the MCP streamable HTTP endpoint will be available. Default is "/mcp".
 * @param configuration Configuration for the streamable HTTP server transport, including behavior and settings.
 * @param block A lambda expression that defines the server behavior in the context of the routing configuration.
 */
private fun Application.mcpStatelessStreamableHttp(
    path: String = "/mcp",
    configuration: StreamableHttpServerTransport.Configuration,
    block: RoutingContext.() -> Server,
) {
    install(SSE)

    routing {
        route(path) {
            post {
                mcpStatelessStreamableHttpEndpoint(
                    configuration = configuration,
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

/**
 * Configures an HTTP endpoint for a stateless, streamable JSON-RPC server to handle requests at the specified [path].
 * This method is a convenience wrapper around the `StreamableHttpServerTransport` configuration.
 *
 * @param path The URL path where the server listens for incoming JSON-RPC requests. Defaults to "/mcp".
 * @param enableDnsRebindingProtection Determines whether DNS rebinding protection is enabled. Defaults to `false`.
 * @param allowedHosts A list of allowed hostnames. If null, host filtering is disabled.
 * @param allowedOrigins A list of allowed origins for CORS. If null, origin filtering is disabled.
 * @param eventStore An optional `EventStore` implementation to provide resumability and event replay support.
 * @param block A callback function defining the server logic using the provided `RoutingContext`.
 */
@KtorDsl
@Suppress("LongParameterList")
public fun Application.mcpStatelessStreamableHttp(
    path: String = "/mcp",
    enableDnsRebindingProtection: Boolean = false,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore? = null,
    block: RoutingContext.() -> Server,
) {
    mcpStatelessStreamableHttp(
        path = path,
        configuration = StreamableHttpServerTransport.Configuration(
            enableDnsRebindingProtection = enableDnsRebindingProtection,
            allowedHosts = allowedHosts,
            allowedOrigins = allowedOrigins,
            eventStore = eventStore,
            enableJsonResponse = true,
        ),
        block = block,
    )
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
    configuration: StreamableHttpServerTransport.Configuration,
    block: RoutingContext.() -> Server,
) {
    val transport = StreamableHttpServerTransport(
        configuration,
    ).also { it.setSessionIdGenerator(null) }

    logger.info { "New stateless StreamableHttp connection established without sessionId" }

    val server = block()
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

private suspend fun existingStreamableTransport(
    call: ApplicationCall,
    transportManager: TransportManager<StreamableHttpServerTransport>,
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

    val transport = transportManager.getTransport(sessionId)
    return if (transport == null) {
        call.reject(
            HttpStatusCode.NotFound,
            RPCError.ErrorCode.CONNECTION_CLOSED,
            "Session not found",
        )
        null
    } else {
        transport
    }
}

private suspend fun RoutingContext.streamableTransport(
    transportManager: TransportManager<StreamableHttpServerTransport>,
    configuration: StreamableHttpServerTransport.Configuration,
    block: RoutingContext.() -> Server,
): StreamableHttpServerTransport? {
    val sessionId = call.request.sessionId()
    if (sessionId != null) {
        val transport = transportManager.getTransport(sessionId)
        return transport ?: existingStreamableTransport(call, transportManager)
    }

    val transport = StreamableHttpServerTransport(configuration)

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
