package io.modelcontextprotocol.kotlin.sdk.integration.sse

import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.modelcontextprotocol.kotlin.sdk.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SSeIntegrationTest : AbstractSseIntegrationTest() {

    @Test
    fun `client should be able to connect to sse server`() = runTest(timeout = 5.seconds) {
        var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
        var client: Client? = null

        try {
            withContext(Dispatchers.Default) {
                server = initTestServer()
                val port = server.engine.resolvedConnectors().single().port
                client = initTestClient(serverPort = port)
            }
        } finally {
            client?.close()
            server?.stopSuspend(1000, 2000)
        }
    }

    /**
     * Test Case #1: One opened connection, a client gets a prompt
     *
     * 1. Open SSE from Client A.
     * 2. Send a POST request from Client A to POST /prompts/get.
     * 3. Observe that Client A receives a response related to it.
     */
    @Test
    fun `single sse connection`() = runTest(timeout = 5.seconds) {
        var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
        var client: Client? = null
        try {
            withContext(Dispatchers.Default) {
                server = initTestServer()
                val port = server.engine.resolvedConnectors().single().port
                client = initTestClient(port, "Client A")

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
     * 1. Open SSE connection #1 from Client A and note the sessionId=<sessionId#1> value.
     * 2. Open SSE connection #2 from Client B and note the sessionId=<sessionId#2> value.
     * 3. Send a POST request to POST /message with the corresponding sessionId#1.
     * 4. Observe that Client B (connection #2) receives a response related to sessionId#1.
     */
    @Test
    fun `multiple sse connections`() = runTest(timeout = 5.seconds) {
        var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
        var clientA: Client? = null
        var clientB: Client? = null

        try {
            withContext(Dispatchers.Default) {
                server = initTestServer()
                val port = server.engine.resolvedConnectors().first().port

                clientA = initTestClient(port, "Client A")
                clientB = initTestClient(port, "Client B")

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
}
