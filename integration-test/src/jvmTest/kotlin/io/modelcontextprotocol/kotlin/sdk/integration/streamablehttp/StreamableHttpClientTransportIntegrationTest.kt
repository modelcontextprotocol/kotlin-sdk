package io.modelcontextprotocol.kotlin.sdk.integration.streamablehttp

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.test.utils.actualPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

private const val SESSION_ID_HEADER = "mcp-session-id"
private const val HOST = "127.0.0.1"
private const val MCP_PATH = "/mcp"

private val INITIALIZE_RESULT = """
    {
      "capabilities": {"tools": {"listChanged": false}},
      "protocolVersion": "2025-03-26",
      "serverInfo": {"name": "test-server", "version": "1.0.0"}
    }
""".trimIndent()

private val TOOLS_LIST_RESULT = """
    {
      "tools": [
        {
          "name": "get_weather",
          "title": "Weather Information Provider",
          "description": "Get current weather information for a location",
          "inputSchema": {
            "type": "object",
            "properties": {
              "location": {"type": "string", "description": "City name or zip code"}
            },
            "required": ["location"]
          },
          "outputSchema": {
            "type": "object",
            "properties": {
              "temperature": {"type": "number", "description": "Temperature, Celsius"}
            },
            "required": ["temperature"]
          },
          "_meta": {}
        }
      ]
    }
""".trimIndent()

private const val PROGRESS_NOTIFICATION_1 =
    """{"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":"upload-123","progress":50,"total":100}}"""

private const val PROGRESS_NOTIFICATION_2 =
    """{"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":"upload-123","progress":75,"total":100}}"""

internal class StreamableHttpClientTransportIntegrationTest {

    @Test
    fun `client receives progress notifications via GET SSE and lists tools`(): Unit = runBlocking(Dispatchers.IO) {
        val sessionId = "sid-progress"
        runWithServer({
            mcpPostRoute(sessionId, extraResults = mapOf("tools/list" to TOOLS_LIST_RESULT))
            get(MCP_PATH) {
                call.response.header(SESSION_ID_HEADER, sessionId)
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    write(sseEvent(id = "1", data = PROGRESS_NOTIFICATION_1))
                    flush()
                    delay(200)
                    write(sseEvent(id = "2", data = PROGRESS_NOTIFICATION_2))
                    flush()
                    awaitCancellation()
                }
            }
        }) { url ->
            val client = newClient()
            client.connect(StreamableHttpClientTransport(client = newHttpClient(), url = url))

            val listToolsResult = client.listTools()

            listToolsResult.tools shouldContain Tool(
                name = "get_weather",
                title = "Weather Information Provider",
                description = "Get current weather information for a location",
                inputSchema = ToolSchema(
                    properties = buildJsonObject {
                        putJsonObject("location") {
                            put("type", "string")
                            put("description", "City name or zip code")
                        }
                    },
                    required = listOf("location"),
                ),
                outputSchema = ToolSchema(
                    properties = buildJsonObject {
                        putJsonObject("temperature") {
                            put("type", "number")
                            put("description", "Temperature, Celsius")
                        }
                    },
                    required = listOf("temperature"),
                ),
                annotations = null,
                meta = EmptyJsonObject,
            )

            client.close()
        }
    }

    @Test
    fun `terminateSession sends DELETE request and clears sessionId`(): Unit = runBlocking(Dispatchers.IO) {
        val sessionId = "sid-terminate"
        runWithServer({
            mcpPostRoute(sessionId)
            get(MCP_PATH) {
                call.response.header(SESSION_ID_HEADER, sessionId)
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    awaitCancellation()
                }
            }
            delete(MCP_PATH) {
                call.respond(HttpStatusCode.OK)
            }
        }) { url ->
            val client = newClient()
            val transport = StreamableHttpClientTransport(client = newHttpClient(), url = url)
            client.connect(transport)

            transport.sessionId shouldBe sessionId
            transport.terminateSession()
            transport.sessionId shouldBe null

            client.close()
        }
    }

    @Test
    fun `client survives 405 on GET SSE`(): Unit = runBlocking(Dispatchers.IO) {
        val sessionId = "sid-405"
        runWithServer({
            mcpPostRoute(sessionId, extraResults = mapOf("ping" to "{}"))
            get(MCP_PATH) {
                call.respondText(
                    text = "",
                    contentType = ContentType.Text.EventStream,
                    status = HttpStatusCode.MethodNotAllowed,
                )
            }
        }) { url ->
            val client = newClient()
            client.connect(StreamableHttpClientTransport(client = newHttpClient(), url = url))

            client.ping()

            client.close()
        }
    }

    @Test
    fun `client survives non streaming JSON response on GET SSE`(): Unit = runBlocking(Dispatchers.IO) {
        val sessionId = "sid-json"
        runWithServer({
            mcpPostRoute(sessionId, extraResults = mapOf("ping" to "{}"))
            get(MCP_PATH) {
                call.response.header(SESSION_ID_HEADER, sessionId)
                call.respondText(
                    text = "",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                )
            }
        }) { url ->
            val client = newClient()
            client.connect(StreamableHttpClientTransport(client = newHttpClient(), url = url))

            client.ping()

            client.close()
        }
    }

    private fun Route.mcpPostRoute(sessionId: String, extraResults: Map<String, String> = emptyMap()) {
        post(MCP_PATH) {
            val body = call.receiveText()
            val parsed = Json.parseToJsonElement(body).jsonObject
            val method = parsed["method"]?.jsonPrimitive?.content
            val idJson = parsed["id"]?.toString() ?: "null"
            call.response.header(SESSION_ID_HEADER, sessionId)
            when {
                method == "initialize" -> call.respondText(
                    text = jsonRpcResult(idJson, INITIALIZE_RESULT),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                )

                method == "notifications/initialized" -> call.respond(HttpStatusCode.Accepted)

                method != null && method in extraResults -> call.respondText(
                    text = jsonRpcResult(idJson, extraResults.getValue(method)),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                )

                else -> call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    private suspend fun runWithServer(routes: Route.() -> Unit, block: suspend (url: String) -> Unit) {
        val server = embeddedServer(ServerCIO, host = HOST, port = 0) {
            routing { routes() }
        }.startSuspend(wait = false)
        try {
            val port = server.actualPort()
            block("http://$HOST:$port$MCP_PATH")
        } finally {
            server.stopSuspend(1000, 2000)
        }
    }

    private fun newClient(): Client = Client(
        clientInfo = Implementation(name = "client1", version = "1.0.0"),
        options = ClientOptions(capabilities = ClientCapabilities()),
    )

    private fun newHttpClient(): HttpClient = HttpClient(ClientCIO) {
        install(SSE)
    }

    private fun jsonRpcResult(idJson: String, resultJson: String): String =
        """{"jsonrpc":"2.0","id":$idJson,"result":$resultJson}"""

    private fun sseEvent(id: String, data: String): String = buildString {
        append("event: message\n")
        append("id: $id\n")
        append("data: $data\n\n")
    }
}
