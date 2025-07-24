package io.modelcontextprotocol.kotlin.sdk.integration

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
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
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpSseTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertTrue
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Test class to reproduce the bug scenarios where messages sent to one session ID
 * are incorrectly delivered to a different client.
 */
class SseBugReproductionTest {
    private val logger = KotlinLogging.logger {}


    /**
     * Test Case #1: Two open connections
     *
     * 1. Open SSE connection #1 from Client A and note the sessionId=<sessionId#1> value.
     * 2. Send a POST request to POST /prompts/get with the corresponding sessionId#1.
     * 3. Observe that Client A (connection #1) receives a response related to sessionId#1.
     */
    @Test
    fun `test case 1 - one open connections`() = runTest {
        val serverEngine = initServer()

        try {
            // Step 1: Open SSE connection #1 from Client A
            val clientA = initClient("Client A")

            // Step 2: Send a prompt request from Client A
            val promptA = getPrompt(clientA, "Client A")

            assertTrue { "Client A" in promptA }
        } finally {
            serverEngine.stop(1000, 2000)
        }
    }

    /**
     * Test Case #1: Two open connections
     *
     * 1. Open SSE connection #1 from Client A and note the sessionId=<sessionId#1> value.
     * 2. Open SSE connection #2 from Client B and note the sessionId=<sessionId#2> value.
     * 3. Send a POST request to POST /message with the corresponding sessionId#1.
     * 4. Observe that Client B (connection #2) receives a response related to sessionId#1.
     */
    @Test
    fun `test case 2 - two open connections`() = runTest {
        val serverEngine = initServer()

        try {
            // Step 1: Open SSE connection #1 from Client A
            val clientA = initClient("Client A")

            // Step 2: Open SSE connection #2 from Client B
            val clientB = initClient("Client B")

            withTimeoutOrNull(1000) {
                // Step 3: Send a prompt request from Client A
                val promptA = getPrompt(clientA, "Client A")
                //  Step 4: Send a prompt request from Client B
                val promptB = getPrompt(clientB, "Client B")

                assertTrue { "Client A" in promptA }
                assertTrue { "Client B" in promptB }
            }

        } finally {
            serverEngine.stop(1000, 2000)
        }
    }

    /**
     * Initialize the server
     */
    private suspend fun initServer(): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        val server = Server(
            Implementation(name = "sse-server", version = "1.0.0"),
            ServerOptions(
                capabilities =
                    ServerCapabilities(prompts = ServerCapabilities.Prompts(listChanged = true))
            ),
        )

        server.addPrompt(
            name = "prompt",
            description = "Prompt description",
            arguments = listOf(
                PromptArgument(
                    name = "client",
                    description = "Client name who requested a prompt",
                    required = true
                )
            )
        ) { request ->
            GetPromptResult(
                "Prompt for ${request.name}",
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = TextContent("Prompt for client ${request.arguments?.get("client")}")
                    )
                )
            )
        }

        return embeddedServer(ServerCIO, host = URL, port = PORT) {
            install(io.ktor.server.sse.SSE)
            routing {
                mcp { server }
            }
        }.startSuspend(wait = false)
    }

    /**
     * Initialize a client with a custom transport that captures the session ID and received messages
     */
    private suspend fun initClient(name: String): Client {
        val client = Client(
            Implementation(name = name, version = "1.0.0")
        )

        val httpClient = HttpClient(ClientCIO) {
            install(SSE)
        }

        // Create a transport wrapper that captures the session ID and received messages
        val transport = httpClient.mcpSseTransport {
            url {
                host = URL
                port = PORT
            }
        }

        withContext(Dispatchers.Default) {
            client.connect(transport)
        }

        return client
    }

    /**
     * Retrieves a prompt result using the provided client and client name.
     */
    private suspend fun getPrompt(client: Client, clientName: String): String {
        return withContext(Dispatchers.Default) {
            val response = client.getPrompt(
                GetPromptRequest(
                    "prompt",
                    arguments = mapOf("client" to clientName)
                )
            )

            (response?.messages?.first()?.content as? TextContent)?.text
                ?: error("Failed to resieve prompt for Client A")
        }
    }


    companion object {
        private const val PORT = 3002
        private const val URL = "localhost"
    }
}