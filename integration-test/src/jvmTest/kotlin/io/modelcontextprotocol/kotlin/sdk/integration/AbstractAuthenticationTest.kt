package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.test.utils.actualPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

/**
 * Base class for MCP authentication integration tests.
 *
 * This class provides a common setup for testing MCP servers behind Ktor authentication.
 * It verifies that unauthenticated requests are rejected and that the authenticated principal
 * is accessible within MCP resource handlers.
 */
abstract class AbstractAuthenticationTest {

    protected companion object {
        const val HOST = "127.0.0.1"
        const val AUTH_REALM = "mcp-auth"
        const val WHOAMI_URI = "whoami://me"
    }

    protected val validUser: String = "user-${UUID.randomUUID().toString().take(8)}"
    protected val validPassword: String = UUID.randomUUID().toString()

    /**
     * Installs Ktor plugins required by the transport under test.
     *
     * The default installs [ServerSSE] (required by both SSE and StreamableHttp transports)
     * and [ContentNegotiation] with [McpJson] (required by StreamableHttp for JSON body
     * serialization). Subclasses may override to add transport-specific plugins.
     */
    protected open fun Application.configurePlugins() {
        install(ServerSSE)
        // ContentNegotiation is required by the StreamableHttp transport for JSON body handling.
        // Installing it for SSE tests as well is harmless.
        install(ContentNegotiation) { json(McpJson) }
    }

    /**
     * Registers the MCP server on the given route.
     * Concrete implementations should use transport-specific extensions (e.g., [Route.mcp] for SSE).
     */
    abstract fun Route.registerMcpServer(serverFactory: ApplicationCall.() -> Server)

    /**
     * Creates a client transport configured with the given credentials.
     */
    abstract fun createClientTransport(baseUrl: String, user: String, pass: String): Transport

    @Test
    fun `mcp behind basic auth rejects unauthenticated requests with 401`() {
        runBlocking(Dispatchers.IO) {
            val server = embeddedServer(ServerCIO, host = HOST, port = 0) {
                configurePlugins()
                install(Authentication) {
                    basic(AUTH_REALM) {
                        validate { credentials ->
                            if (credentials.name == validUser && credentials.password == validPassword) {
                                UserIdPrincipal(credentials.name)
                            } else {
                                null
                            }
                        }
                    }
                }
                routing {
                    authenticate(AUTH_REALM) {
                        registerMcpServer {
                            createMcpServer { principal<UserIdPrincipal>()?.name }
                        }
                    }
                }
            }.startSuspend(wait = false)

            val httpClient = HttpClient(ClientCIO)
            try {
                httpClient.get("http://$HOST:${server.actualPort()}").status shouldBe HttpStatusCode.Unauthorized
            } finally {
                httpClient.close()
                server.stopSuspend(500, 1000)
            }
        }
    }

    @Test
    fun `authenticated mcp client can read resource scoped to principal`() {
        runBlocking(Dispatchers.IO) {
            val server = embeddedServer(ServerCIO, host = HOST, port = 0) {
                configurePlugins()
                install(Authentication) {
                    basic(AUTH_REALM) {
                        validate { credentials ->
                            if (credentials.name == validUser && credentials.password == validPassword) {
                                UserIdPrincipal(credentials.name)
                            } else {
                                null
                            }
                        }
                    }
                }
                routing {
                    authenticate(AUTH_REALM) {
                        registerMcpServer {
                            // `this` is the ApplicationCall at connection time.
                            // The lambda passed to createMcpServer captures this call;
                            // principal<T>() is safe to call from resource handlers because
                            // the call's authentication context remains valid for the session lifetime.
                            createMcpServer { principal<UserIdPrincipal>()?.name }
                        }
                    }
                }
            }.startSuspend(wait = false)

            val baseUrl = "http://$HOST:${server.actualPort()}"
            var mcpClient: Client? = null
            try {
                mcpClient = Client(Implementation(name = "test-client", version = "1.0.0"))
                mcpClient.connect(createClientTransport(baseUrl, validUser, validPassword))

                val result = mcpClient.readResource(
                    ReadResourceRequest(ReadResourceRequestParams(uri = WHOAMI_URI)),
                )

                result.contents shouldBe listOf(
                    TextResourceContents(
                        text = validUser,
                        uri = WHOAMI_URI,
                        mimeType = "text/plain",
                    ),
                )
            } finally {
                mcpClient?.close()
                server.stopSuspend(500, 1000)
            }
        }
    }

    protected fun createMcpServer(principalProvider: () -> String?): Server = Server(
        serverInfo = Implementation(name = "test-server", version = "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                resources = ServerCapabilities.Resources(),
            ),
        ),
    ).apply {
        addResource(
            uri = WHOAMI_URI,
            name = "Current User",
            description = "Returns the name of the authenticated user",
            mimeType = "text/plain",
        ) {
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = principalProvider() ?: "anonymous",
                        uri = WHOAMI_URI,
                        mimeType = "text/plain",
                    ),
                ),
            )
        }
    }
}
