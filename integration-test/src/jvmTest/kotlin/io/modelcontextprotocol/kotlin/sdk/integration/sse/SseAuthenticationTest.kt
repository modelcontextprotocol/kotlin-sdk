package io.modelcontextprotocol.kotlin.sdk.integration.sse

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.basicAuth
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.integration.AbstractAuthenticationTest
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlin.test.AfterTest
import io.ktor.client.engine.cio.CIO as ClientCIO

class SseAuthenticationTest : AbstractAuthenticationTest() {

    private var httpClient: HttpClient? = null

    @AfterTest
    fun closeHttpClient() {
        httpClient?.close()
        httpClient = null
    }

    override fun Route.registerMcpServer(serverFactory: ApplicationCall.() -> Server) {
        mcp {
            serverFactory(call)
        }
    }

    override fun createClientTransport(baseUrl: String, user: String, pass: String): Transport {
        val client = HttpClient(ClientCIO) { install(SSE) }
        httpClient = client
        return SseClientTransport(
            client = client,
            urlString = baseUrl,
            requestBuilder = { basicAuth(user, pass) },
        )
    }
}
