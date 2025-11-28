package io.modelcontextprotocol.kotlin.sdk.conformance

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    require(args.isNotEmpty()) {
        "Usage: ConformanceClient <server-url>"
    }

    val serverUrl = args.last()
    logger.info { "Connecting to test server at: $serverUrl" }

    val httpClient = HttpClient(CIO) {
        install(SSE)
    }
    val transport: Transport = StreamableHttpClientTransport(httpClient, serverUrl)

    val client = Client(
        clientInfo = Implementation(
            name = "kotlin-conformance-client",
            version = "1.0.0",
        ),
    )

    var exitCode = 0

    runBlocking {
        try {
            client.connect(transport)
            logger.info { "✅ Connected to server successfully" }

            try {
                val tools = client.listTools()
                logger.info { "Available tools: ${tools.tools.map { it.name }}" }

                val addNumbersTool = tools.tools.find { it.name == "add_numbers" }
                if (addNumbersTool != null) {
                    logger.info { "Calling tool: add_numbers" }
                    val result = client.callTool(
                        CallToolRequest(
                            params = CallToolRequestParams(
                                name = "add_numbers",
                                arguments = buildJsonObject {
                                    put("a", JsonPrimitive(5))
                                    put("b", JsonPrimitive(3))
                                },
                            ),
                        ),
                    )
                    logger.info { "Tool result: ${result.content}" }
                } else if (tools.tools.isNotEmpty()) {
                    val toolName = tools.tools.first().name
                    logger.info { "Calling tool: $toolName" }

                    val result = client.callTool(
                        CallToolRequest(
                            params = CallToolRequestParams(
                                name = toolName,
                                arguments = buildJsonObject {
                                    put("input", JsonPrimitive("test"))
                                },
                            ),
                        ),
                    )
                    logger.info { "Tool result: ${result.content}" }
                }
            } catch (e: Exception) {
                logger.debug(e) { "Error during tool operations (may be expected for some scenarios)" }
            }

            logger.info { "✅ Client operations completed successfully" }
        } catch (e: Exception) {
            logger.error(e) { "❌ Client failed" }
            exitCode = 1
        } finally {
            try {
                transport.close()
            } catch (e: Exception) {
                logger.warn(e) { "Error closing transport" }
            }
            httpClient.close()
        }
    }

    kotlin.system.exitProcess(exitCode)
}
