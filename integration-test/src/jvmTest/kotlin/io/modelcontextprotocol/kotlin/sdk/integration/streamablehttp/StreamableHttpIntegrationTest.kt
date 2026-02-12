package io.modelcontextprotocol.kotlin.sdk.integration.streamablehttp

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.test.utils.actualPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import io.ktor.client.engine.cio.CIO as ClientCIO

class StreamableHttpIntegrationTest : AbstractStreamableHttpIntegrationTest() {

    @Test
    fun `client should receive responses via POST json and server notifications via GET sse`() =
        runBlocking(Dispatchers.IO) {
            var server: StreamableHttpTestServer? = null
            var client: Client? = null
            var httpClient: HttpClient? = null

            try {
                server = initTestServer()
                val port = server.ktorServer.actualPort()

                httpClient = HttpClient(ClientCIO) { install(SSE) }
                val transport = StreamableHttpClientTransport(
                    client = httpClient,
                    url = "http://$URL:$port/mcp",
                ).apply {
                    protocolVersion = LATEST_PROTOCOL_VERSION
                }

                client = Client(Implementation("streamable-http-client", VERSION))
                client.connect(transport)

                val prompt = getPrompt(client, "Client A")
                assertTrue { "Client A" in prompt }
            } finally {
                client?.close()
                httpClient?.close()
                server?.ktorServer?.stopSuspend(1000, 2000)
            }
        }

    private suspend fun getPrompt(client: Client, clientName: String): String {
        val response = client.getPrompt(
            GetPromptRequest(
                GetPromptRequestParams(
                    name = "prompt",
                    arguments = mapOf("client" to clientName),
                ),
            ),
        )

        return (response.messages.first().content as? TextContent)?.text
            ?: error("Failed to receive prompt for Client $clientName")
    }
}
