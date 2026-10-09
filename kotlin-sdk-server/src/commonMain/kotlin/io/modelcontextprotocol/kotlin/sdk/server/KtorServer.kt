package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.MissingApplicationPluginException
import io.ktor.server.application.install
import io.ktor.server.application.isHandled
import io.ktor.server.http.HttpRequestLifecycle
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
import io.ktor.server.sse.Heartbeat
import io.ktor.server.sse.SSE
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.heartbeat
import io.ktor.server.sse.sse
import io.ktor.utils.io.KtorDsl
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Registers MCP over [Server-Sent Events (SSE) Transport](https://modelcontextprotocol.io/specification/2024-11-05/basic/transports#http-with-sse)
 * at the specified [path] on this [Route].
 *
 * **Precondition:** the [SSE] plugin must be installed on the application before calling this function.
 * Use [Application.mcp] if you want SSE to be installed automatically.
 *
 * Installs [HttpRequestLifecycle] with `cancelCallOnClose` on this route, unless it is already installed here,
 * on a parent route, or on the application, so that an SSE session ends when its client disconnects.
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
 * Installs [HttpRequestLifecycle] with `cancelCallOnClose` on this route, unless it is already installed here,
 * on a parent route, or on the application, so that an SSE session ends when its client disconnects.
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
    installCancelCallOnClose()

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
 * with [McpJson][io.modelcontextprotocol.kotlin.sdk.types.McpJson] and [SSE], and, like [Route.mcp],
 * [HttpRequestLifecycle] on the routing root.
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
    sseHeartbeatConfig: (Heartbeat.() -> Unit)?,
    sessions: StreamableHttpSessionManager<StreamableHttpServerTransport>,
    block: RoutingContext.() -> Server,
) {
    installMcpContentNegotiation()
    install(SSE)

    if (sessions.expires) {
        // The application scope is cancelled when the application stops, which ends this loop.
        launch(CoroutineName("mcp-session-expiry")) { sessions.closeExpiredSessionsPeriodically() }
    }

    routing {
        route(path) {
            installDnsRebindingProtection(enableDnsRebindingProtection, allowedHosts, allowedOrigins)
            installCancelCallOnClose()

            // Ktor's sse {} commits a 200 before its handler runs. A GET for a missing, expired, or deleted
            // session is therefore rejected here, while its status can still be set: the spec requires 404
            // for a terminated session. A GET that goes through gets Mcp-Session-Id.
            intercept(ApplicationCallPipeline.Plugins) {
                if (context.request.httpMethod != HttpMethod.Get || context.isHandled) return@intercept
                val sessionId = context.sessionIdOrReject() ?: return@intercept finish()
                // Touched, not just looked up, so that the session cannot expire before sse {} runs.
                if (sessions.touch(sessionId) == null) {
                    context.rejectSessionNotFound()
                    return@intercept finish()
                }
                context.response.header(MCP_SESSION_ID_HEADER, sessionId)
            }

            sse {
                // Validated by the interceptor above; an open stream does not keep the session alive.
                val transport = call.request.sessionId()?.let { sessions.touch(it) } ?: return@sse
                sseHeartbeatConfig?.let { config -> heartbeat(config) }
                transport.handleRequest(this, call)
            }

            post {
                if (call.request.sessionId() == null) {
                    openStreamableSession(sessions, configuration, block)
                } else {
                    call.withStreamableSession(sessions) { transport -> transport.handleRequest(null, call) }
                }
            }

            delete {
                call.withStreamableSession(sessions) { transport -> transport.handleRequest(null, call) }
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
 * A session expires after [sessionIdleTimeout] without requests (an open GET stream does not count), after which
 * its id answers `404 Not Found`. At most [maxSessions] sessions are open at once; beyond that, a new session is
 * refused with `503 Service Unavailable`.
 *
 * Also installs [HttpRequestLifecycle] with `cancelCallOnClose` on the route, unless it is already installed.
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
 * @param sseHeartbeatConfig The heartbeat configuration option for SSE connections. `null` means no heartbeat is sent.
 * @param sessionIdleTimeout How long a session may go without activity before it is closed. Defaults to 30 minutes.
 *          Expiry is checked every few seconds, so a session can outlive this by up to 5 seconds.
 *          [Duration.INFINITE] disables expiry, so abandoned sessions pile up until [maxSessions] is reached.
 * @param maxSessions The maximum number of sessions open at once, counting those still initializing.
 *          Defaults to 10,000.
 * @param block factory block with access to the [RoutingContext] (for reading request headers)
 *          that creates and returns the [Server] to handle the connection.
 * @throws IllegalArgumentException if [sessionIdleTimeout] or [maxSessions] is not positive.
 */
@KtorDsl
public fun Application.mcpStreamableHttp(
    path: String = "/mcp",
    enableDnsRebindingProtection: Boolean = true,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore? = null,
    sseHeartbeatConfig: (Heartbeat.() -> Unit)? = null,
    sessionIdleTimeout: Duration = DEFAULT_SESSION_IDLE_TIMEOUT,
    maxSessions: Int = DEFAULT_MAX_SESSIONS,
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
        sseHeartbeatConfig = sseHeartbeatConfig,
        sessions = StreamableHttpSessionManager(idleTimeout = sessionIdleTimeout, maxSessions = maxSessions),
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
                call.rejectUnsupportedMethod()
            }
            delete {
                call.rejectUnsupportedMethod()
            }
        }
    }
}

/**
 * Configures the Ktor Application to handle Model Context Protocol (MCP)
 * over _stateless_ [Streamable HTTP Transport](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#streamable-http)
 *
 * Sets up an HTTP POST endpoint at [path]. GET and DELETE requests return 405 Method Not Allowed.
 * Every request/response pair is returned as JSON, so this endpoint opens no SSE stream and
 * offers no resumability. Use [mcpStreamableHttp] when either is required.
 *
 * Automatically installs [ContentNegotiation][io.ktor.server.plugins.contentnegotiation.ContentNegotiation]
 * with [McpJson][io.modelcontextprotocol.kotlin.sdk.types.McpJson].
 *
 * @param path The URL path where the server listens for incoming JSON-RPC requests. Defaults to "/mcp".
 * @param enableDnsRebindingProtection Determines whether DNS rebinding protection is enabled. Defaults to `true`.
 * @param allowedHosts A list of allowed hostnames. If `null` and DNS rebinding protection is enabled,
 * defaults to `localhost`, `127.0.0.1`, `[::1]`.
 * @param allowedOrigins A list of allowed `Origin` header values, compared by hostname only
 *      (scheme and port are ignored). Requests without an `Origin` header are allowed.
 *      If `null`, origin validation is disabled.
 * @param block factory block with access to the [RoutingContext] (for reading request headers)
 *          that creates and returns the [Server] to handle the connection.
 */
@KtorDsl
public fun Application.mcpStatelessStreamableHttp(
    path: String = "/mcp",
    enableDnsRebindingProtection: Boolean = true,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    block: RoutingContext.() -> Server,
) {
    mcpStatelessStreamableHttp(
        path = path,
        enableDnsRebindingProtection = enableDnsRebindingProtection,
        allowedHosts = allowedHosts,
        allowedOrigins = allowedOrigins,
        configuration = StreamableHttpServerTransport.Configuration(
            enableJsonResponse = true,
        ),
        block = block,
    )
}

/**
 * Retained only so that existing call sites get a migration hint instead of an unresolved parameter name.
 *
 * @param eventStore never consulted by a stateless endpoint. Resumption is driven by a `GET` carrying
 *          `Last-Event-ID`, and this endpoint answers `GET` with `405 Method Not Allowed`, so no stream
 *          exists to store events on or replay them to. Use [mcpStreamableHttp] when you need resumability.
 */
@Deprecated(
    "Use mcpStatelessStreamableHttp without eventStore.",
    ReplaceWith("mcpStatelessStreamableHttp(path, enableDnsRebindingProtection, allowedHosts, allowedOrigins, block)"),
    DeprecationLevel.ERROR,
)
@Suppress("UnusedParameter")
public fun Application.mcpStatelessStreamableHttp(
    path: String = "/mcp",
    enableDnsRebindingProtection: Boolean = true,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
    eventStore: EventStore?,
    block: RoutingContext.() -> Server,
) {
    mcpStatelessStreamableHttp(
        path = path,
        enableDnsRebindingProtection = enableDnsRebindingProtection,
        allowedHosts = allowedHosts,
        allowedOrigins = allowedOrigins,
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
    val transportClosed = CompletableDeferred<Unit>()
    // Registered before the session starts, so a close right after it starts is not missed.
    transport.onClose {
        logger.info { "Server connection closed for sessionId: ${transport.sessionId}" }
        transportClosed.complete(Unit)
    }

    try {
        block().createSession(transport)
        logger.debug { "Server connected to transport for sessionId: ${transport.sessionId}" }

        // The connection lasts until the server closes the session, or until the client goes away, which
        // cancels this call (see installCancelCallOnClose).
        transportClosed.await()
    } finally {
        // Without its SSE connection the transport can no longer answer, so stop routing POSTs to it.
        transportManager.removeTransport(transport.sessionId)
    }
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
    val session = server.createSession(transport)

    try {
        transport.handleRequest(null, this.call)
        logger.debug { "Server connected to transport without sessionId" }
    } finally {
        // A stateless session serves exactly one request: close it so the server's
        // session registry and notification subscriptions do not grow unboundedly.
        session.close()
        logger.debug { "Stateless session closed after request completion" }
    }
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

/** A stateless endpoint serves POST only, offering neither an SSE stream to open nor a session to delete. */
private suspend fun ApplicationCall.rejectUnsupportedMethod() {
    response.header(HttpHeaders.Allow, HttpMethod.Post.value)
    reject(
        HttpStatusCode.MethodNotAllowed,
        RPCError.ErrorCode.CONNECTION_CLOSED,
        "Method not allowed.",
    )
}

private fun ApplicationRequest.sessionId(): String? = header(MCP_SESSION_ID_HEADER)

/**
 * Runs [block] with the transport of the session named by the `Mcp-Session-Id` header, keeping that session from
 * expiring until [block] returns. Rejects the call when the header is missing or names no open session.
 */
private suspend fun ApplicationCall.withStreamableSession(
    sessions: StreamableHttpSessionManager<StreamableHttpServerTransport>,
    block: suspend (StreamableHttpServerTransport) -> Unit,
) {
    val sessionId = sessionIdOrReject() ?: return
    if (!sessions.withSession(sessionId, block)) rejectSessionNotFound()
}

/** Returns the `Mcp-Session-Id` header, or answers `400 Bad Request` and returns `null` when it is missing. */
private suspend fun ApplicationCall.sessionIdOrReject(): String? {
    val sessionId = request.sessionId()
    if (sessionId.isNullOrEmpty()) {
        reject(
            HttpStatusCode.BadRequest,
            RPCError.ErrorCode.CONNECTION_CLOSED,
            "Bad Request: No valid session ID provided",
        )
        return null
    }
    return sessionId
}

/**
 * Serves a POST that carries no `Mcp-Session-Id` on a new session. The session is kept only if the POST
 * initializes it; otherwise it is closed once the POST completes.
 */
private suspend fun RoutingContext.openStreamableSession(
    sessions: StreamableHttpSessionManager<StreamableHttpServerTransport>,
    configuration: StreamableHttpServerTransport.Configuration,
    block: RoutingContext.() -> Server,
) {
    if (!sessions.tryReserve()) {
        logger.warn { "Rejecting a new StreamableHttp session: ${sessions.maxSessions} sessions are already open" }
        call.reject(
            HttpStatusCode.ServiceUnavailable,
            RPCError.ErrorCode.INTERNAL_ERROR,
            "Service Unavailable: Too many open sessions",
        )
        return
    }

    val transport = StreamableHttpServerTransport(configuration)

    transport.setOnSessionInitialized { initializedSessionId ->
        sessions.register(initializedSessionId, transport)
        logger.info { "New StreamableHttp connection established and stored with sessionId: $initializedSessionId" }
    }

    transport.setOnSessionClosed { closedSession ->
        sessions.remove(closedSession, transport)
        logger.info { "Closed StreamableHttp connection and removed sessionId: $closedSession" }
    }

    try {
        val session = block().createSession(transport)
        // Registered on the session rather than on the Server, which may be shared by every session.
        session.onClose {
            transport.sessionId?.let { sessionId ->
                sessions.remove(sessionId, transport)
                logger.debug { "Server connection closed for sessionId: $sessionId" }
            }
        }
        transport.handleRequest(null, call)
    } finally {
        // The transport gets its session id when it initializes, which is also when the session is registered.
        val sessionId = transport.sessionId
        if (sessionId != null) {
            sessions.release(sessionId, transport)
        } else {
            sessions.cancelReservation()
            // No request can reach a transport that never initialized. Close it so that a Server shared
            // across sessions drops the session created for it.
            transport.close()
        }
    }
}

/**
 * Has the engine cancel a call once its client disconnects. Otherwise a handler that only waits, like the one
 * holding an SSE connection open, keeps running after its client is gone, and so does its session. Only the CIO
 * and Netty engines support this; others notice a gone client only when writing to it. An [HttpRequestLifecycle]
 * already installed on this route, a parent route, or the application is kept as it is.
 */
private fun Route.installCancelCallOnClose() {
    try {
        plugin(HttpRequestLifecycle)
    } catch (_: MissingApplicationPluginException) {
        install(HttpRequestLifecycle) { cancelCallOnClose = true }
    }
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
