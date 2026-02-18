package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

@OptIn(ExperimentalMcpApi::class)
class StreamableHttpServerTransportTest {
    private val path = "/transport"

    @Test
    fun `POST without event-stream accept header is rejected`() = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(enableJsonResponse = true)
        val onMessageCalled = AtomicBoolean(false)
        transport.onMessage {
            onMessageCalled.set(true)
        }

        configureTransportEndpoint(transport)

        val payload = buildInitializeRequestPayload()

        val response = client.post(path) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            setBody(payload)
        }

        assertEquals(HttpStatusCode.NotAcceptable, response.status)
        assertFalse(onMessageCalled.get(), "Transport should not deliver messages when headers are invalid")
    }

    @Test
    fun `initialization request establishes session and returns json response`() = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(enableJsonResponse = true)
        val expectedSessionId = "session-test-id"
        transport.setSessionIdGenerator { expectedSessionId }

        var observedRequest: JSONRPCRequest? = null
        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                observedRequest = message
                transport.send(JSONRPCResponse(message.id, EmptyResult()), null)
            }
        }

        configureTransportEndpoint(transport)

        val payload = buildInitializeRequestPayload()

        val response = client.post(path) {
            addStreamableHeaders()
            setBody(payload)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(expectedSessionId, response.headers[MCP_SESSION_ID_HEADER])
        val request = assertNotNull(observedRequest, "Initialization request should be forwarded")

        response.body<JSONRPCResponse>() shouldBe JSONRPCResponse(request.id)
    }

    @Test
    fun `notifications only payload responds with 202 Accepted`() = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(enableJsonResponse = true)
        val receivedMessages = mutableListOf<JSONRPCMessage>()
        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                transport.send(JSONRPCResponse(message.id, EmptyResult()))
            }
            receivedMessages.add(message)
        }

        configureTransportEndpoint(transport)

        val initRequest = buildInitializeRequestPayload()

        val responseInit = client.post(path) {
            addStreamableHeaders()
            setBody(initRequest)
        }

        val notificationPayload = encodeMessages(
            listOf(InitializedNotification().toJSON()),
        )

        val response = client.post(path) {
            addStreamableHeaders()
            header("mcp-session-id", responseInit.headers[MCP_SESSION_ID_HEADER])
            setBody(notificationPayload)
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        receivedMessages shouldBeEqual listOf(initRequest, InitializedNotification().toJSON())
    }

    @Test
    fun `batched requests wait for all responses before replying`() = testApplication {
        configTestServer()

        val client = createTestClient(logging = true)

        val transport = StreamableHttpServerTransport(enableJsonResponse = true)
        val firstRequest = JSONRPCRequest(id = RequestId("first"), method = Method.Defined.ToolsList.value)
        val secondRequest = JSONRPCRequest(id = RequestId("second"), method = Method.Defined.ResourcesList.value)

        val firstResult = ListToolsResult {
            tool {
                name = "tool-1"
                inputSchema { }
            }
            meta {
                put("label", "first")
            }
        }
        val secondResult = ListResourcesResult(
            resources = emptyList(),
            meta = buildJsonObject { put("label", "second") },
        )

        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                val result = when (message.id) {
                    firstRequest.id -> firstResult
                    secondRequest.id -> secondResult
                    else -> EmptyResult()
                }
                transport.send(JSONRPCResponse(message.id, result), null)
            }
        }

        configureTransportEndpoint(transport)

        val initRequest = buildInitializeRequestPayload()

        val responseInit = client.post(path) {
            addStreamableHeaders()
            setBody(initRequest)
        }

        val payload = encodeMessages(listOf(firstRequest, secondRequest))

        val response = client.post(path) {
            addStreamableHeaders()
            header("mcp-session-id", responseInit.headers[MCP_SESSION_ID_HEADER])
            setBody(payload)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val responses = response.body<List<JSONRPCResponse>>().map { it.result }
        responses shouldContain (firstResult)
        responses shouldContain (secondResult)
        // TODO(check order)
//        assertEquals(listOf(firstRequest.id, secondRequest.id), responses.map { it.id })
//        val firstMeta = (responses[0].result as EmptyResult).meta
//        val secondMeta = (responses[1].result as EmptyResult).meta
//        assertEquals("first", firstMeta?.get("label")?.jsonPrimitive?.content)
//        assertEquals("second", secondMeta?.get("label")?.jsonPrimitive?.content)
    }

    private fun ApplicationTestBuilder.configureTransportEndpoint(transport: StreamableHttpServerTransport) {
        application {
            routing {
                post(path) {
                    transport.handlePostRequest(null, call)
                }
            }
        }
    }

    private fun HttpRequestBuilder.addStreamableHeaders() {
        header(
            HttpHeaders.Accept,
            listOf(ContentType.Application.Json, ContentType.Text.EventStream).joinToString(", ") {
                it.toString()
            },
        )
        contentType(ContentType.Application.Json)
    }

    private fun buildInitializeRequestPayload(): JSONRPCRequest {
        val request = InitializeRequest(
            InitializeRequestParams(
                protocolVersion = LATEST_PROTOCOL_VERSION,
                capabilities = ClientCapabilities(),
                clientInfo = Implementation(name = "test-client", version = "1.0.0"),
            ),
        ).toJSON()

        return request
    }

    private fun encodeMessages(messages: List<JSONRPCMessage>): String =
        McpJson.encodeToString(ListSerializer(JSONRPCMessage.serializer()), messages)

    private fun ApplicationTestBuilder.configTestServer() {
        application {
            install(ServerContentNegotiation) {
                json(McpJson)
            }
        }
    }

    private fun ApplicationTestBuilder.createTestClient(logging: Boolean = false): HttpClient = createClient {
        install(ClientContentNegotiation) {
            json(McpJson)
        }
        if (logging) {
            install(Logging) {
                level = LogLevel.ALL
            }
        }
    }
}
