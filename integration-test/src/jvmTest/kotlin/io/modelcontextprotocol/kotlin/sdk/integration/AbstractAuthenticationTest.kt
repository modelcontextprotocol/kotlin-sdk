package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.basicAuth
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
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

/**
 * Base class for MCP authentication integration tests.
 */
@Suppress("InjectDispatcher")
abstract class AbstractAuthenticationTest {

    protected companion object {
        const val HOST = "127.0.0.1"
        const val AUTH_REALM = "mcp-auth"
        const val WHOAMI_URI = "whoami://me"
        const val VALID_USER = "test-user"
        const val VALID_PASSWORD = "valid-password-123"
        const val INVALID_USER = "invalid-user"
        const val INVALID_PASSWORD = "invalid-password"
    }

    /**
     * Installs Ktor plugins required by the transport under test.
     */
    protected open fun Application.configurePlugins() {
        install(ServerSSE)
        // ContentNegotiation is required by the StreamableHttp transport for JSON body handling.
        // Installing it for SSE tests as well is harmless.
        install(ContentNegotiation) { json(McpJson) }
    }

    /**
     * Registers the MCP server on the given route.
     */
    abstract fun Route.registerMcpServer(serverFactory: ApplicationCall.() -> Server)

    /**
     * Creates a client transport configured with the given credentials.
     */
    abstract fun createClientTransport(baseUrl: String, user: String, pass: String): Transport

    @Test
    fun `mcp behind basic auth rejects unauthenticated requests with 401`(): Unit = runBlocking(Dispatchers.IO) {
        val server = startAuthenticatedServer()

        val httpClient = HttpClient(ClientCIO) { expectSuccess = false }
        try {
            httpClient.get("http://$HOST:${server.actualPort()}").status shouldBe HttpStatusCode.Unauthorized
        } finally {
            httpClient.close()
            server.stopSuspend(1000, 2000)
        }
    }

    @Test
    fun `mcp rejects requests with invalid credentials`(): Unit = runBlocking(Dispatchers.IO) {
        val server = startAuthenticatedServer()

        val httpClient = HttpClient(ClientCIO) {
            expectSuccess = false
        }
        try {
            httpClient.get("http://$HOST:${server.actualPort()}") {
                basicAuth(INVALID_USER, INVALID_PASSWORD)
            }.status shouldBe HttpStatusCode.Unauthorized
        } finally {
            httpClient.close()
            server.stopSuspend(1000, 2000)
        }
    }

    @Test
    fun `authenticated mcp client can read resource scoped to principal`(): Unit = runBlocking(Dispatchers.IO) {
        val server = startAuthenticatedServer()

        val baseUrl = "http://$HOST:${server.actualPort()}"
        var mcpClient: Client? = null
        try {
            mcpClient = Client(Implementation(name = "test-client", version = "1.0.0"))
            withTimeout(5.seconds) {
                mcpClient.connect(createClientTransport(baseUrl, VALID_USER, VALID_PASSWORD))
            }

            val result = mcpClient.readResource(
                ReadResourceRequest(ReadResourceRequestParams(uri = WHOAMI_URI)),
            )

            result.contents shouldBe listOf(
                TextResourceContents(
                    text = VALID_USER,
                    uri = WHOAMI_URI,
                    mimeType = "text/plain",
                ),
            )
        } finally {
            mcpClient?.close()
            server.stopSuspend(1000, 2000)
        }
    }

    private suspend fun startAuthenticatedServer() = embeddedServer(ServerCIO, host = HOST, port = 0) {
        configurePlugins()
        installBasicAuth()
        routing {
            authenticate(AUTH_REALM) {
                registerMcpServer {
                    createMcpServer { principal<UserIdPrincipal>()?.name }
                }
            }
        }
    }.startSuspend(wait = false)

    private fun Application.installBasicAuth() {
        install(Authentication) {
            basic(AUTH_REALM) {
                validate { credentials ->
                    if (credentials.name == VALID_USER && credentials.password == VALID_PASSWORD) {
                        UserIdPrincipal(credentials.name)
                    } else {
                        null
                    }
                }
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
