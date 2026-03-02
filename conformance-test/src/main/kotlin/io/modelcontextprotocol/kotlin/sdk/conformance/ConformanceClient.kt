package io.modelcontextprotocol.kotlin.sdk.conformance

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val serverUrl = args.firstOrNull() ?: error("Server URL required as first argument")

    runBlocking {
        val httpClient = HttpClient(CIO) { install(SSE) }
        val transport = StreamableHttpClientTransport(httpClient, serverUrl)
        val client = Client(
            clientInfo = Implementation("mcp-kotlin-sdk-conformance-client", "0.1.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(
                    sampling = ClientCapabilities.sampling,
                    elicitation = ClientCapabilities.elicitation,
                    roots = ClientCapabilities.Roots(listChanged = true),
                ),
            ),
        )
        client.connect(transport)

        try {
            // List and call tools
            val tools = client.listTools()
            for (tool in tools.tools) {
                runCatching {
                    client.callTool(
                        CallToolRequest(CallToolRequestParams(name = tool.name)),
                    )
                }
            }

            // List and get prompts
            val prompts = client.listPrompts()
            for (prompt in prompts.prompts) {
                runCatching {
                    client.getPrompt(
                        GetPromptRequest(GetPromptRequestParams(name = prompt.name)),
                    )
                }
            }

            // List and read resources
            val resources = client.listResources()
            for (resource in resources.resources) {
                runCatching {
                    client.readResource(
                        ReadResourceRequest(ReadResourceRequestParams(uri = resource.uri)),
                    )
                }
            }
        } finally {
            client.close()
            httpClient.close()
        }
    }
}
