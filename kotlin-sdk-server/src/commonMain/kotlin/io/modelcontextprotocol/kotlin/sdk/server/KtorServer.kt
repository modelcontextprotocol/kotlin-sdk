package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.MissingApplicationPluginException
import io.ktor.server.application.install
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.response.header
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
 * Registers MCP over [Server-Sent Events (SSE) Transport](https://modelcontextprotocol.io/specification/2024-11-05/basic/transports#http-with-sse)
 * at the specified [path] on this [Route].
 *
 * **Precondition:** the [SSE] plugin must be installed on the application before calling this function.
 * Use [Application.mcp] if you want SSE to be installed automatically.
 *
 * @param path the URL path to register the SSE endpoint.
 * @param enableDnsRebindingProtection whether to install [DnsRebindingProtection] on this route. Defaults to `true`.
 * @param allowedHosts hostnames allowed in the `Host` header. Defaults to `localhost`, `127.0.0.1`, `[::1]`.
 * @param allowedOrigins origins allowed in the `Origin` header, compared by hostname only
 *      (scheme and port are ignored). Requests without an `Origin` header are allowed.
 *      When `null` while the localhost host defaults are in effect (no custom `allowedHosts`),
 *      the `Origin` header is validated against `localhost`, `127.0.0.1`, `[::1]`.
 *      With custom `allowedHosts`, `null` skips origin validation.
 * @param maxRequestBodySize maximum allowed size, in bytes, of an incoming POST body; larger requests are
 *      rejected with `413 Payload Too Large`. Defaults to 4 MiB.
 * @param block factory block with access to the [ServerSSESession]
 *      that creates and returns the [Server] to handle the connection.
 * @throws IllegalStateException if the [SSE] plugin is not installed.
 */
@KtorDsl
@Suppress("LongParameterList")
public fun Route.mcp(
    path: String,
    enableDnsRebindingProtection: Boolean = true,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    maxRequestBodySize: Long = DEFAULT_MAX_REQUEST_BODY_SIZE,
    block: ServerSSESession.() -> Server,
) {
    route(path) {
        mcp(enableDnsRebindingProtection, allowedHosts, allowedOrigins, maxRequestBodySize, block)
    }
}

/**
 * Registers MCP over [Server-Sent Events (SSE) Transport](https://modelcontextprotocol.io/specification/2024-11-05/basic/transports#http-with-sse)
 * endpoints on this [Route].
 *
 * **Precondition:** the [SSE] plugin must be installed on the application before calling this function.
 * Use [Application.mcp] if you want SSE to be installed automatically.
 *
 * @param enableDnsRebindingProtection whether to install [DnsRebindingProtection] on this route. Defaults to `true`.
 * @param allowedHosts hostnames allowed in the `Host` header. Defaults to `localhost`, `127.0.0.1`, `[::1]`.
 * @param allowedOrigins origins allowed in the `Origin` header, compared by hostname only
 *      (scheme and port are ignored). Requests without an `Origin` header are allowed.
 *      When `null` while the localhost host defaults are in effect (no custom `allowedHosts`),
 *      the `Origin` header is validated against `localhost`, `127.0.0.1`, `[::1]`.
 *      With custom `allowedHosts`, `null` skips origin validation.
 * @param maxRequestBodySize maximum allowed size, in bytes, of an incoming POST body; larger requests are
 *      rejected with `413 Payload Too Large`. Defaults to 4 MiB.
 * @param block factory block with access to the [ServerSSESession]
 *      that creates and returns the [Server] to handle the connection.
 * @throws IllegalStateException if the [SSE] plugin is not installed.
 */
@KtorDsl
@Suppress("LongParameterList")
public fun Route.mcp(
    enableDnsRebindingProtection: Boolean = true,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    maxRequestBodySize: Long = DEFAULT_MAX_REQUEST_BODY_SIZE,
    block: ServerSSESession.() -> Server,
) {
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

    installDnsRebindingProtection(enableDnsRebindingProtection, allowedHosts, allowedOrigins)

    val transportManager = TransportManager<SseServerTransport>()

    sse {
        mcpSseEndpoint("", transportManager, maxRequestBodySize, block)
    }

    post {
        mcpPostEndpoint(transportManager)
    }
}

/**
 * Configures the Ktor Application to handle Model Context Protocol (MCP)
 * over [Server-Sent Events (SSE) Transport](https://modelcontextprotocol.io/specification/2024-11-05/basic/transports#http-with-sse)
 * and sets up routing with the provided configuration block.
 *
 * Automatically installs [ContentNegotiation][io.ktor.server.plugins.contentnegotiation.ContentNegotiation]
 * with [McpJson][io.modelcontextprotocol.kotlin.sdk.types.McpJson] and [SSE].
 *
 * @param enableDnsRebindingProtection whether to install [DnsRebindingProtection] on this route. Defaults to `true`.
 * @param allowedHosts hostnames allowed in the `Host` header. Defaults to `localhost`, `127.0.0.1`, `[::1]`.
 * @param allowedOrigins origins allowed in the `Origin` header, compared by hostname only
 *      (scheme and port are ignored). Requests without an `Origin` header are allowed.
 *      When `null` while the localhost host defaults are in effect (no custom `allowedHosts`),
 *      the `Origin` header is validated against `localhost`, `127.0.0.1`, `[::1]`.
 *      With custom `allowedHosts`, `null` skips origin validation.
 * @param maxRequestBodySize maximum allowed size, in bytes, of an incoming POST body; larger requests are
 *      rejected with `413 Payload Too Large`. Defaults to 4 MiB.
 * @param block factory block with access to the [ServerSSESession]
 *      that creates and returns the [Server] to handle the connection.
 */
@KtorDsl
@Suppress("LongParameterList")
public fun Application.mcp(
    enableDnsRebindingProtection: Boolean = true,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    maxRequestBodySize: Long = DEFAULT_MAX_REQUEST_BODY_SIZE,
    block: ServerSSESession.() -> Server,
) {
    installMcpContentNegotiation()
    install(SSE)

    routing {
        mcp(enableDnsRebindingProtection, allowedHosts, allowedOrigins, maxRequestBodySize, block)
    }
}

@Suppress("LongParameterList")
private fun Application.mcpStreamableHttp(
    path: String = "/mcp",
    enableDnsRebindingProtection: Boolean,
    allowedHosts: List<String>?,
    allowedOrigins: List<String>?,
    configuration: StreamableHttpServerTransport.Configuration,
    block: RoutingContext.() -> Server,
) {
    installMcpContentNegotiation()
    install(SSE)

    val transportManager = TransportManager<StreamableHttpServerTransport>()

    routing {
        route(path) {
            installDnsRebindingProtection(enableDnsRebindingProtection, allowedHosts, allowedOrigins)

            // Set Mcp-Session-Id on GET responses before Ktor's sse {} commits headers.
            intercept(ApplicationCallPipeline.Plugins) {
                if (context.request.httpMethod == HttpMethod.Get) {
                    val sessionId = context.request.header(MCP_SESSION_ID_HEADER)
                    if (sessionId != null && transportManager.getTransport(sessionId) != null) {
                        context.response.header(MCP_SESSION_ID_HEADER, sessionId)
                    }
                }
            }

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
 * Configures the Ktor Application to handle Model Context Protocol (MCP)
 * over [Streamable HTTP Transport](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#streamable-http)
 *
 * Sets up SSE, HTTP POST, and DELETE endpoints at the specified [path].
 * Simple request/response pairs are returned as JSON (not SSE streams).
 *
 * Automatically installs [ContentNegotiation][io.ktor.server.plugins.contentnegotiation.ContentNegotiation]
 * with [McpJson][io.modelcontextprotocol.kotlin.sdk.types.McpJson] and [SSE].
 *
 * @param path The base path for the MCP Streamable HTTP endpoint. Defaults to "/mcp".
 * @param enableDnsRebindingProtection Enables DNS rebinding attack protection for the endpoint. Defaults to `true`.
 * @param allowedHosts A list of hostnames allowed to access the endpoint.
 *          If `null` and DNS rebinding protection is enabled, defaults to `localhost`, `127.0.0.1`, `[::1]`.
 * @param allowedOrigins A list of allowed `Origin` header values, compared by hostname only
 *          (scheme and port are ignored). Requests without an `Origin` header are allowed.
 *          When `null` while the localhost host defaults are in effect (no custom `allowedHosts`),
 *          the `Origin` header is validated against `localhost`, `127.0.0.1`, `[::1]`.
 *          With custom `allowedHosts`, `null` skips origin validation.
 * @param eventStore An optional [EventStore] instance to enable resumable event stream functionality.
 *          Allows storing and replaying events.
 * @param block factory block with access to the [RoutingContext] (for reading request headers)
 *          that creates and returns the [Server] to handle the connection.
 */
@KtorDsl
public fun Application.mcpStreamableHttp(
    path: String = "/mcp",
    enableDnsRebindingProtection: Boolean = true,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore? = null,
    block: RoutingContext.() -> Server,
) {
    mcpStreamableHttp(
        path = path,
        enableDnsRebindingProtection = enableDnsRebindingProtection,
        allowedHosts = allowedHosts,
        allowedOrigins = allowedOrigins,
        configuration = StreamableHttpServerTransport.Configuration(
            eventStore = eventStore,
            enableJsonResponse = true,
        ),
        block = block,
    )
}

@Suppress("LongParameterList")
private fun Application.mcpStatelessStreamableHttp(
    path: String = "/mcp",
    enableDnsRebindingProtection: Boolean,
    allowedHosts: List<String>?,
    allowedOrigins: List<String>?,
    configuration: StreamableHttpServerTransport.Configuration,
    block: RoutingContext.() -> Server,
) {
    installMcpContentNegotiation()
    install(SSE)

    routing {
        route(path) {
            installDnsRebindingProtection(enableDnsRebindingProtection, allowedHosts, allowedOrigins)

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
 * Configures the Ktor Application to handle Model Context Protocol (MCP)
 * over _stateless_ [Streamable HTTP Transport](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#streamable-http)
 *
 * Sets up an HTTP POST endpoint at [path]. GET and DELETE requests return 405 Method Not Allowed.
 * Simple request/response pairs are returned as JSON (not SSE streams).
 *
 * Automatically installs [ContentNegotiation][io.ktor.server.plugins.contentnegotiation.ContentNegotiation]
 * with [McpJson][io.modelcontextprotocol.kotlin.sdk.types.McpJson] and [SSE].
 *
 * @param path The URL path where the server listens for incoming JSON-RPC requests. Defaults to "/mcp".
 * @param enableDnsRebindingProtection Determines whether DNS rebinding protection is enabled. Defaults to `true`.
 * @param allowedHosts A list of allowed hostnames. If `null` and DNS rebinding protection is enabled,
 * defaults to `localhost`, `127.0.0.1`, `[::1]`.
 * @param allowedOrigins A list of allowed `Origin` header values, compared by hostname only
 *      (scheme and port are ignored). Requests without an `Origin` header are allowed.
 *      If `null`, origin validation is disabled.
 * @param eventStore An optional [EventStore] implementation to provide resumability and event replay support.
 * @param block factory block with access to the [RoutingContext] (for reading request headers)
 *          that creates and returns the [Server] to handle the connection.
 */
@KtorDsl
public fun Application.mcpStatelessStreamableHttp(
    path: String = "/mcp",
    enableDnsRebindingProtection: Boolean = true,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore? = null,
    block: RoutingContext.() -> Server,
) {
    mcpStatelessStreamableHttp(
        path = path,
        enableDnsRebindingProtection = enableDnsRebindingProtection,
        allowedHosts = allowedHosts,
        allowedOrigins = allowedOrigins,
        configuration = StreamableHttpServerTransport.Configuration(
            eventStore = eventStore,
            enableJsonResponse = true,
        ),
        block = block,
    )
}

private suspend fun ServerSSESession.mcpSseEndpoint(
    postEndpoint: String,
    transportManager: TransportManager<SseServerTransport>,
    maxRequestBodySize: Long,
    block: ServerSSESession.() -> Server,
) {
    val transport = mcpSseTransport(postEndpoint, transportManager, maxRequestBodySize)

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
    maxRequestBodySize: Long,
): SseServerTransport {
    val transport = SseServerTransport(postEndpoint, this, maxRequestBodySize)
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

private fun Route.installDnsRebindingProtection(enabled: Boolean, hosts: List<String>?, origins: List<String>?) {
    if (!enabled) return
    install(DnsRebindingProtection) {
        allowedHosts = hosts ?: LOCALHOST_ALLOWED_HOSTS
        // Secure-by-default: when relying on the localhost host defaults, validate the Origin
        // header against localhost too, so a request with a valid Host but a hostile Origin
        // (e.g. a DNS-rebinding page) is rejected. Callers with custom hosts opt in explicitly.
        allowedOrigins = origins ?: LOCALHOST_ALLOWED_ORIGINS.takeIf { hosts == null }
    }
}
