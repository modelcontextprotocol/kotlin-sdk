package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.matchers.collections.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.HttpStatusCode
import io.ktor.sse.ServerSentEvent
import io.modelcontextprotocol.kotlin.sdk.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Integration tests for the `StreamableHttpClientTransport` implementation
 * using the [Mokksy](https://mokksy.dev) library
 * to simulate Streaming HTTP with server-sent events (SSE).
 * @author Konstantin Pavlov
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StreamableHttpClientTest {

    // start mokksy on random port
    private val mockMcp: MockMcp = MockMcp(verbose = true)

    @AfterTest
    fun afterEach() {
        mockMcp.checkForUnmatchedRequests()
    }

    @Test
    @Suppress("LongMethod")
    fun `test streamableHttpClient`(): Unit = runBlocking {
        val client = Client(
            clientInfo = Implementation(name = "sample-client", version = "1.0.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(),
            ),
        )

        val sessionId = UUID.randomUUID().toString()

        mockMcp.onJSONRPCRequest(
            jsonRpcMethod = "initialize",
            sessionId = sessionId,
        ) {
            // language=json
            """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "result": {
                "capabilities": {
                  "tools": {
                     "listChanged": false
                  }
                },
                "protocolVersion": "2025-03-26",
                "serverInfo": {
                  "name": "Mock MCP Server",
                  "version": "1.0.0"
                },
                "_meta": {
                  "foo": "bar"
                }
              }
            }
            """.trimIndent()
        }

        mockMcp.onJSONRPCRequest(
            jsonRpcMethod = "notifications/initialized",
            expectedSessionId = sessionId,
            sessionId = sessionId,
            statusCode = HttpStatusCode.Accepted,
        ) {
            ""
        }

        mockMcp.onSubscribeWithGet(sessionId) {
            flow {
                delay(500.milliseconds)
                emit(
                    ServerSentEvent(
                        event = "message",
                        id = "1",
                        data = @Suppress("MaxLineLength")
                        //language=json
                        """{"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":"upload-123","progress":50,"total":100}}""",
                    ),
                )
                delay(200.milliseconds)
                emit(
                    ServerSentEvent(
                        data = @Suppress("MaxLineLength")
                        //language=json
                        """{"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":"upload-123","progress":50,"total":100}}""",
                    ),
                )
            }
        }

        client.connect(
            StreamableHttpClientTransport(
                url = mockMcp.url,
                client = HttpClient(Apache5) {
                    install(SSE)
                    install(Logging) {
                        level = LogLevel.ALL
                    }
                },
            ),
        )

        // TODO: how to get notifications via Client API?

        mockMcp.onJSONRPCRequest(
            jsonRpcMethod = "tools/list",
            sessionId = sessionId,
        ) {
            // language=json
            """
             {
              "jsonrpc": "2.0",
              "id": 3,
              "result": {
                "tools": [
                  {
                    "name": "get_weather",
                    "title": "Weather Information Provider",
                    "description": "Get current weather information for a location",
                    "inputSchema": {
                      "type": "object",
                      "properties": {
                        "location": {
                          "type": "string",
                          "description": "City name or zip code"
                        }
                      },
                      "required": ["location"]
                    },
                    "outputSchema": {
                      "type": "object",
                      "properties": {
                        "temperature": {
                          "type": "number",
                          "description": "Temperature, Celsius"
                        }
                      },
                      "required": ["temperature"]
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        }

        val listToolsResult = client.listTools()

        listToolsResult.tools shouldContain Tool(
            name = "get_weather",
            title = "Weather Information Provider",
            description = "Get current weather information for a location",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("location") {
                        put("type", "string")
                        put("description", "City name or zip code")
                    }
                },
                required = listOf("location"),
            ),
            outputSchema = Tool.Output(
                properties = buildJsonObject {
                    putJsonObject("temperature") {
                        put("type", "number")
                        put("description", "Temperature, Celsius")
                    }
                },
                required = listOf("temperature"),
            ),
            annotations = null,
        )

        mockMcp.mockUnsubscribeRequest(sessionId = sessionId)

        client.close()
    }
}
