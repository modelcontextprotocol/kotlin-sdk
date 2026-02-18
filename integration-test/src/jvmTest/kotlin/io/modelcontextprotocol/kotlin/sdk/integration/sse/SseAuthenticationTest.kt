package io.modelcontextprotocol.kotlin.sdk.integration.sse

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.basicAuth
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.integration.AbstractAuthenticationTest
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.ktor.client.engine.cio.CIO as ClientCIO

/**
 * Integration tests for MCP-over-SSE placed behind Ktor authentication middleware.
 *
 * Demonstrates the pattern for issue #236: [io.ktor.server.auth.principal] is
 * accessible inside the `mcp { }` factory block via
 * [io.ktor.server.sse.ServerSSESession.call]. The principal is captured once per
 * SSE connection and can be closed over in resource/tool handlers to scope
 * responses to the authenticated user.
 */
class SseAuthenticationTest : AbstractAuthenticationTest() {

    override fun Route.registerMcpServer(serverFactory: ApplicationCall.() -> Server) {
        mcp {
            serverFactory(call)
        }
    }

    override fun createClientTransport(baseUrl: String, user: String, pass: String): Transport = SseClientTransport(
        client = HttpClient(ClientCIO) { install(SSE) },
        urlString = baseUrl,
        requestBuilder = { basicAuth(user, pass) },
    )
}
