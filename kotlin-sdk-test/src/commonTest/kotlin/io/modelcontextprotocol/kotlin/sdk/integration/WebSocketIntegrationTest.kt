package io.modelcontextprotocol.kotlin.sdk.integration

import io.ktor.client.HttpClient
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.Role
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpWebSocketTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpWebSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSocket
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

class WebSocketIntegrationTest {

    @Test
    fun `client should be able to connect to websocket server 2`() = runTest(timeout = 5.seconds) {
        var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
        var client: Client? = null

        try {
            withContext(Dispatchers.Default) {
                server = initServer()
                val port = server.engine.resolvedConnectors().first().port
                client = initClient(serverPort = port)
            }
        } finally {
            client?.close()
            server?.stopSuspend(1000, 2000)
        }
    }

    /**
     * Test Case #1: One opened connection, a client gets a prompt
     *
     * 1. Open WebSocket from Client A.
     * 2. Send a POST request from Client A to POST /prompts/get.
     * 3. Observe that Client A receives a response related to it.
     */
    @Test
    fun `single websocket connection`() = runTest(timeout = 5.seconds) {
        var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
        var client: Client? = null

        try {
            withContext(Dispatchers.Default) {
                server = initServer()
                val port = server.engine.resolvedConnectors().first().port
                client = initClient("Client A", port)

                val promptA = getPrompt(client, "Client A")
                assertTrue { "Client A" in promptA }
            }
        } finally {
            client?.close()
            server?.stopSuspend(1000, 2000)
        }
    }

    /**
     * Test Case #1: Two open connections, each client gets a client-specific prompt
     *
     * 1. Open WebSocket connection #1 from Client A and note the sessionId=<sessionId#1> value.
     * 2. Open WebSocket connection #2 from Client B and note the sessionId=<sessionId#2> value.
     * 3. Send a POST request to POST /message with the corresponding sessionId#1.
     * 4. Observe that Client B (connection #2) receives a response related to sessionId#1.
     */
    @Test
    fun `multiple websocket connections`() = runTest(timeout = 5.seconds) {
        var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
        var clientA: Client? = null
        var clientB: Client? = null

        try {
            withContext(Dispatchers.Default) {
                server = initServer()
                val port = server.engine.resolvedConnectors().first().port
                clientA = initClient("Client A", port)
                clientB = initClient("Client B", port)

                // Step 3: Send a prompt request from Client A
                val promptA = getPrompt(clientA, "Client A")
                //  Step 4: Send a prompt request from Client B
                val promptB = getPrompt(clientB, "Client B")

                assertTrue { "Client A" in promptA }
                assertTrue { "Client B" in promptB }
            }
        } finally {
            clientA?.close()
            clientB?.close()
            server?.stopSuspend(1000, 2000)
        }
    }

    private suspend fun initClient(name: String = "", serverPort: Int): Client {
        val client = Client(
            Implementation(name = name, version = "1.0.0"),
        )

        val httpClient = HttpClient(ClientCIO) {
            install(ClientWebSocket)
        }

        // Create a transport wrapper that captures the session ID and received messages
        val transport = httpClient.mcpWebSocketTransport {
            url {
                host = URL
                port = serverPort
            }
        }

        client.connect(transport)

        return client
    }

    private suspend fun initServer(): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        val server = Server(
            Implementation(name = "websocket-server", version = "1.0.0"),
            ServerOptions(
                capabilities = ServerCapabilities(prompts = ServerCapabilities.Prompts(listChanged = true)),
            ),
        )

        server.addPrompt(
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
                "Prompt for ${request.name}",
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = TextContent("Prompt for client ${request.arguments?.get("client")}"),
                    ),
                ),
            )
        }

        val ktorServer = embeddedServer(ServerCIO, host = URL, port = PORT) {
            install(ServerWebSockets)
            routing {
                mcpWebSocket(block = { server })
            }
        }

        return ktorServer.startSuspend(wait = false)
    }

    /**
     * Retrieves a prompt result using the provided client and client name.
     */
    private suspend fun getPrompt(client: Client, clientName: String): String {
        val response = client.getPrompt(
            GetPromptRequest(
                "prompt",
                arguments = mapOf("client" to clientName),
            ),
        )

        return (response.messages.first().content as? TextContent)?.text
            ?: error("Failed to receive prompt for Client $clientName")
    }

    companion object {
        private const val PORT = 0
        private const val URL = "127.0.0.1"
    }
}
