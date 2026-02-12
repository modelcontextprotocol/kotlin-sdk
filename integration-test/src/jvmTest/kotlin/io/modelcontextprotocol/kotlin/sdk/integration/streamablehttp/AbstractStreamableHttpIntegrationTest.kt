package io.modelcontextprotocol.kotlin.sdk.integration.streamablehttp

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.ktor.server.cio.CIO as ServerCIO

open class AbstractStreamableHttpIntegrationTest {

    suspend fun initTestServer(name: String? = null): StreamableHttpTestServer {
        val mcpServer = Server(
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
            install(ContentNegotiation) {
                json(McpJson)
            }
            mcpStreamableHttp { mcpServer }
        }

        return StreamableHttpTestServer(
            mcpServer = mcpServer,
            ktorServer = ktorServer.startSuspend(wait = false),
        )
    }

    data class StreamableHttpTestServer(
        val mcpServer: Server,
        val ktorServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>,
    )

    companion object {
        internal const val DEFAULT_SERVER_NAME = "streamable-http-test-server"
        internal const val VERSION = "1.0.0"
        internal const val URL = "127.0.0.1"
        internal const val PORT = 0
    }
}
