package io.modelcontextprotocol.kotlin.sdk.integration.sse

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpSseTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

open class AbstractSseIntegrationTest {

    suspend fun initTestClient(serverPort: Int, name: String? = null): Client {
        val client = Client(
            Implementation(name = name ?: DEFAULT_CLIENT_NAME, version = VERSION),
        )

        val httpClient = HttpClient(ClientCIO) {
            install(SSE)
        }

        // Create a transport wrapper that captures the session ID and received messages
        val transport = httpClient.mcpSseTransport {
            url {
                host = URL
                port = serverPort
            }
        }

        client.connect(transport)

        return client
    }

    suspend fun initTestServer(
        name: String? = null,
    ): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        val server = Server(
            Implementation(name = name ?: DEFAULT_SERVER_NAME, version = VERSION),
            ServerOptions(
                capabilities = ServerCapabilities(prompts = ServerCapabilities.Prompts(listChanged = true)),
            ),
        ) {
            addPrompt(
                name = "prompt",
                description = "Prompt description",
                arguments = listOf(
                    PromptArgument(
                        name = "client",
                        description = "Client name who requested a prompt",
                        required = true,
                    ),
                ),
            ) { request ->
                GetPromptResult(
                    messages = listOf(
                        PromptMessage(
                            role = Role.User,
                            content = TextContent("Prompt for client ${request.params.arguments?.get("client")}"),
                        ),
                    ),
                    description = "Prompt for ${request.params.name}",
                )
            }
        }

        val ktorServer = embeddedServer(
            ServerCIO,
            host = URL,
            port = PORT,
        ) {
            install(ServerSSE)
            routing {
                mcp { server }
            }
        }

        return ktorServer.startSuspend(wait = false)
    }

    companion object {
        private const val DEFAULT_CLIENT_NAME = "sse-test-client"
        private const val DEFAULT_SERVER_NAME = "sse-test-server"
        private const val VERSION = "1.0.0"
        private const val URL = "127.0.0.1"
        private const val PORT = 0
    }
}
