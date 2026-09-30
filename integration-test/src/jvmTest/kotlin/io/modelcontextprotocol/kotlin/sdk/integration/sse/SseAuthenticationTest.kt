package io.modelcontextprotocol.kotlin.sdk.integration.sse

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.basicAuth
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.test.utils.actualPort
import io.modelcontextprotocol.kotlin.test.utils.runIntegrationTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

private const val HOST = "127.0.0.1"
private const val AUTH_REALM = "mcp-auth"
private const val WHOAMI_URI = "whoami://me"
private const val USER = "test-user"
private const val PASSWORD = "valid-password-123"

class SseAuthenticationTest {

    @Test
    fun `authenticated mcp client can read resource scoped to principal`() = runIntegrationTest(timeout = 20.seconds) {
        val server = embeddedServer(ServerCIO, host = HOST, port = 0) {
            install(ServerSSE)
            install(Authentication) {
                basic(AUTH_REALM) {
                    validate { credentials ->
                        UserIdPrincipal(credentials.name).takeIf {
                            credentials.name == USER && credentials.password == PASSWORD
                        }
                    }
                }
            }
            routing {
                authenticate(AUTH_REALM) {
                    mcp { whoAmIServer(call) }
                }
            }
        }.startSuspend(wait = false)
        val httpClient = HttpClient(ClientCIO) { install(SSE) }
        val client = Client(Implementation("test-client", "1.0.0"))
        try {
            // Credentials must reach both the SSE GET and the message POSTs.
            client.connect(
                SseClientTransport(
                    client = httpClient,
                    urlString = "http://$HOST:${server.actualPort()}",
                    requestBuilder = { basicAuth(USER, PASSWORD) },
                ),
            )

            val result = client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = WHOAMI_URI)))

            result.contents shouldBe
                listOf(TextResourceContents(text = USER, uri = WHOAMI_URI, mimeType = "text/plain"))
        } finally {
            client.close()
            httpClient.close()
            server.stopSuspend(gracePeriodMillis = 0, timeoutMillis = 1_000)
        }
    }

    private fun whoAmIServer(call: ApplicationCall): Server = Server(
        serverInfo = Implementation("test-server", "1.0.0"),
        options = ServerOptions(ServerCapabilities(resources = ServerCapabilities.Resources())),
    ) {
        addResource(
            uri = WHOAMI_URI,
            name = "Current user",
            description = "Authenticated user",
            mimeType = "text/plain",
        ) {
            val user = call.principal<UserIdPrincipal>()?.name ?: "anonymous"
            ReadResourceResult(listOf(TextResourceContents(text = user, uri = WHOAMI_URI, mimeType = "text/plain")))
        }
    }
}
