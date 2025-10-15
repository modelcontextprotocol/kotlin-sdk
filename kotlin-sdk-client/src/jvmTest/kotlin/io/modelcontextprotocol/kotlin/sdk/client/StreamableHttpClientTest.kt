package io.modelcontextprotocol.kotlin.sdk.client

import dev.mokksy.mokksy.Mokksy
import dev.mokksy.mokksy.StubConfiguration
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
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StreamableHttpClientTest {

    private val mokksy = Mokksy(verbose = true)

    @AfterTest
    fun afterEach() {
        mokksy.checkForUnmatchedRequests()
    }

    @AfterAll
    fun afterAll() {
        mokksy.shutdown()
    }

    @Test
    fun `test streamableHttpClient`(): Unit = runBlocking {
        val client = Client(
            clientInfo = Implementation(name = "sample-client", version = "1.0.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(),
            ),
        )

        val sessionId = UUID.randomUUID().toString()

        mockPostRequest(
            method = "initialize",
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

        mockPostRequest(
            method = "notifications/initialized",
            sessionId = sessionId,
            statusCode = HttpStatusCode.Accepted,
        ) {
            ""
        }

        mokksy.get(name = "MCP GETs", requestType = Any::class) {
            path("/mcp")
            containsHeader("Mcp-Session-Id", sessionId)
            containsHeader("Accept", "text/event-stream,text/event-stream") // todo: why 2 times?
            containsHeader("Cache-Control", "no-store")
        } respondsWithSseStream {
            headers += "Mcp-Session-Id" to sessionId
            flow =
                flow {
                    delay(500.milliseconds)
                    emit(
                        ServerSentEvent(
                            event = "message",
                            id = "1",
                            data = @Suppress("ktlint:standard:max-line-length")
                            //language=json
                            """{"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":"upload-123","progress":50,"total":100}}""",
                        ),
                    )
                    delay(200.milliseconds)
                    emit(
                        ServerSentEvent(
                            data = @Suppress("ktlint:standard:max-line-length")
                            //language=json
                            """{"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":"upload-123","progress":50,"total":100}}""",
                        ),
                    )
                }
        }

        client.connect(
            StreamableHttpClientTransport(
                url = "http://localhost:${mokksy.port()}/mcp",
                client = HttpClient(Apache5) {
                    install(SSE)
                    install(Logging) {
                        level = LogLevel.ALL
                    }
                },
            ),
        )

        // TODO: get notifications

        mockPostRequest(
            method = "tools/list",
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

        println("✅  $listToolsResult")

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
    }

    private fun mockPostRequest(
        method: String,
        sessionId: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        bodyBuilder: () -> String,
    ) {
        mokksy.post(
            configuration = StubConfiguration(removeAfterMatch = true),
            requestType = JSONRPCRequest::class,
        ) {
            path("/mcp")
            bodyMatchesPredicates(
                {
                    it!!.method == method
                },
                {
                    it!!.jsonrpc == "2.0"
                },
            )
        } respondsWith {
            body = bodyBuilder.invoke()
            headers += "Content-Type" to "application/json; charset=utf-8"
            headers += "Mcp-Session-Id" to sessionId
            httpStatus = statusCode
        }
    }
}
