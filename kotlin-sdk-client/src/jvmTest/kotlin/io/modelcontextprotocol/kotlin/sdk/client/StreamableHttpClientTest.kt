package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.sse.ServerSentEvent
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressToken
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class StreamableHttpClientTest : AbstractStreamableHttpClientTest() {

    @Test
    fun `Should skip empty SSE`(): Unit = runBlocking {
        val client = Client(
            clientInfo = Implementation(
                name = "client1",
                version = "1.0.0",
            ),
            options = ClientOptions(
                capabilities = ClientCapabilities(),
            ),
        )
        val sessionId = Uuid.random().toString()

        mockMcp.onJSONRPCRequest(
            httpMethod = HttpMethod.Post,
            jsonRpcMethod = "initialize",
        ) respondsWithSseStream {
            headers += MCP_SESSION_ID_HEADER to sessionId
            // empty data — should be skipped by client
            chunks += ServerSentEvent(data = "", id = Uuid.random().toString())
            // whitespace-only data — should be skipped by client
            chunks += ServerSentEvent(data = "  \t ", id = Uuid.random().toString())
            // valid initialize response with multiline JSON
            @Suppress("MaxLineLength")
            chunks += ServerSentEvent(
                event = "message",
                id = Uuid.random().toString(),
                //language=json
                data = """{
                    |"result":{
                    |  "protocolVersion":"2025-06-18",
                    |  "capabilities":{},
                    |  "serverInfo":{"name":"simple-streamable-http-server","version":"1.0.0"}
                    |},
                    |"jsonrpc":"2.0",
                    |"id":"7ce065b0678f49e5b04ce5a0fcc7d518"
                    |}
                    |""".trimMargin(),
            )
        }

        mockMcp.handleJSONRPCRequest(
            jsonRpcMethod = "notifications/initialized",
            expectedSessionId = sessionId,
            sessionId = sessionId,
            statusCode = HttpStatusCode.Accepted,
        )

        mockMcp.handleSubscribeWithGet(sessionId) {
            emptyFlow()
        }

        connect(client)
    }

    @Test
    fun `test streamableHttpClient`() = runBlocking {
        val client = Client(
            clientInfo = Implementation(
                name = "client1",
                version = "1.0.0",
            ),
            options = ClientOptions(
                capabilities = ClientCapabilities(),
            ),
        )

        val sessionId = Uuid.random().toString()

        mockMcp.onInitialize(
            clientName = "client1",
            sessionId = sessionId,
        )

        mockMcp.handleJSONRPCRequest(
            jsonRpcMethod = "notifications/initialized",
            expectedSessionId = sessionId,
            sessionId = sessionId,
            statusCode = HttpStatusCode.Accepted,
        )

        @Suppress("MaxLineLength")
        mockMcp.handleSubscribeWithGet(sessionId) {
            flow {
                for (i in 0..10) {
                    delay(100.milliseconds)
                    emit(
                        ServerSentEvent(
                            event = "message",
                            id = "1",
                            data =
                                //language=json
                                """{"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":"upload-123","progress":${i * 10},"total":100}}""",
                        ),
                    )
                }
                awaitCancellation()
            }
        }

        val receivedNotifications = CopyOnWriteArrayList<ProgressNotification>()
        client.setNotificationHandler<ProgressNotification>(Method.Defined.NotificationsProgress) {
            receivedNotifications.add(it)
            CompletableDeferred(Unit)
        }

        mockMcp.handleWithResult(
            jsonRpcMethod = "tools/list",
            sessionId = sessionId,
            // language=json
            result = """
              {
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
                    },
                    "_meta": {}
                  }
                ]
              }
            """.trimIndent(),
        )

        connect(client)

        eventually(5.seconds) {
            receivedNotifications.size shouldBe 11 // 0..100 with step 10

            receivedNotifications.forEachIndexed { index, notification ->
                notification.params.progressToken shouldBe ProgressToken("upload-123")
                notification.params.progress shouldBe index * 10.0
                notification.params.total shouldBe 100.0
            }
        }

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

    @Test
    fun `terminateSession sends DELETE request`() = runBlocking {
        val client = Client(
            clientInfo = Implementation(name = "client1", version = "1.0.0"),
            options = ClientOptions(capabilities = ClientCapabilities()),
        )
        val sessionId = Uuid.random().toString()

        mockMcp.onInitialize(clientName = "client1", sessionId = sessionId)
        mockMcp.handleJSONRPCRequest(
            jsonRpcMethod = "notifications/initialized",
            expectedSessionId = sessionId,
            sessionId = sessionId,
            statusCode = HttpStatusCode.Accepted,
        )
        mockMcp.handleSubscribeWithGet(sessionId) { emptyFlow() }

        connect(client)

        mockMcp.mockUnsubscribeRequest(sessionId = sessionId)
        (client.transport as StreamableHttpClientTransport).terminateSession()
        (client.transport as StreamableHttpClientTransport).sessionId shouldBe null

        client.close()
    }

    @Test
    fun `handle MethodNotAllowed`() = runBlocking {
        checkSupportNonStreamingResponse(
            ContentType.Text.EventStream,
            HttpStatusCode.MethodNotAllowed,
        )
    }

    @Test
    fun `handle non-streaming response`() = runBlocking {
        checkSupportNonStreamingResponse(
            ContentType.Application.Json,
            HttpStatusCode.OK,
        )
    }

    private suspend fun checkSupportNonStreamingResponse(contentType: ContentType, statusCode: HttpStatusCode) {
        val sessionId = "SID_${Uuid.random().toHexString()}"
        val clientName = "client-${Uuid.random().toHexString()}"
        val client = Client(
            clientInfo = Implementation(name = clientName, version = "1.0.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(),
            ),
        )

        mockMcp.onInitialize(clientName = clientName, sessionId = sessionId)

        mockMcp.handleJSONRPCRequest(
            jsonRpcMethod = "notifications/initialized",
            expectedSessionId = sessionId,
            sessionId = sessionId,
            statusCode = HttpStatusCode.Accepted,
        )

        mockMcp.onSubscribe(
            httpMethod = HttpMethod.Get,
            sessionId = sessionId,
        ) respondsWith {
            headers += MCP_SESSION_ID_HEADER to sessionId
            body = null
            httpStatus = statusCode
            this.contentType = contentType
        }

        mockMcp.handleWithResult(jsonRpcMethod = "ping", sessionId = sessionId) {
            buildJsonObject {}
        }

        connect(client)

        delay(1.seconds)

        client.ping() // connection is still alive

        client.close()
    }
}
