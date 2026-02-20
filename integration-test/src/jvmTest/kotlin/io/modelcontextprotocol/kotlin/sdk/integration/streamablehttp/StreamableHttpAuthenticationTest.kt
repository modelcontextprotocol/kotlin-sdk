package io.modelcontextprotocol.kotlin.sdk.integration.streamablehttp

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.basicAuth
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.integration.AbstractAuthenticationTest
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.ktor.client.engine.cio.CIO as ClientCIO

class StreamableHttpAuthenticationTest : AbstractAuthenticationTest() {

    override fun Route.registerMcpServer(serverFactory: ApplicationCall.() -> Server) {
        mcpStreamableHttp {
            serverFactory(call)
        }
    }

    override fun createClientTransport(baseUrl: String, user: String, pass: String): Transport =
        StreamableHttpClientTransport(
            client = HttpClient(ClientCIO) { install(SSE) },
            url = baseUrl,
            requestBuilder = { basicAuth(user, pass) },
        )
}
