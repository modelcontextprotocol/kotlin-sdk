package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
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
import io.ktor.utils.io.readUTF8Line
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
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class StreamableHttpServerTransportTest {
    companion object {
        @JvmStatic
        @MethodSource
        fun invalidPayloads() = listOf(
            "",
            "   ",
            "  \n  \t  ",
            null,
            "lolol",
        )

        private val sizeTestPayload = "x".repeat(64)

        @JvmStatic
        fun maxBodySizeTestCases(): List<Arguments> = listOf(
            Arguments.of(sizeTestPayload.length.toLong() - 1, HttpStatusCode.PayloadTooLarge),
            Arguments.of(sizeTestPayload.length.toLong(), HttpStatusCode.BadRequest),
            Arguments.of(sizeTestPayload.length.toLong() + 1, HttpStatusCode.BadRequest),
        )
    }

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
    fun `second initialization request returns an HTTP error`() = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(enableJsonResponse = true)
        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                transport.send(JSONRPCResponse(message.id, EmptyResult()))
            }
        }

        configureTransportEndpoint(transport)

        val payload = buildInitializeRequestPayload()

        val firstResponse = client.post(path) {
            addStreamableHeaders()
            setBody(payload)
        }

        firstResponse.status shouldBe HttpStatusCode.OK

        val secondResponse = client.post(path) {
            addStreamableHeaders()
            header("mcp-session-id", firstResponse.headers[MCP_SESSION_ID_HEADER])
            setBody(payload)
        }

        secondResponse.status shouldBe HttpStatusCode.BadRequest
    }

    @Test
    fun `init request with unsupported protocol version returns an HTTP error`() = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(enableJsonResponse = true)
        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                transport.send(JSONRPCResponse(message.id, EmptyResult()))
            }
        }

        configureTransportEndpoint(transport)

        val initResponse = client.post(path) {
            addStreamableHeaders()
            header("mcp-protocol-version", "1900-01-01")
            setBody(buildInitializeRequestPayload())
        }

        initResponse.status shouldBe HttpStatusCode.BadRequest
        initResponse.headers[MCP_SESSION_ID_HEADER] shouldBe null
    }

    @Test
    fun `request with unsupported protocol version returns an HTTP error`() = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(enableJsonResponse = true)
        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                transport.send(JSONRPCResponse(message.id, EmptyResult()))
            }
        }

        configureTransportEndpoint(transport)

        val initResponse = client.post(path) {
            addStreamableHeaders()
            setBody(buildInitializeRequestPayload())
        }

        initResponse.status shouldBe HttpStatusCode.OK
        val sessionId = initResponse.headers[MCP_SESSION_ID_HEADER]
        assertNotNull(sessionId)

        val response = client.post(path) {
            addStreamableHeaders()
            header("mcp-session-id", sessionId)
            header("mcp-protocol-version", "1900-01-01")
            setBody(
                encodeMessages(
                    listOf(
                        JSONRPCRequest(
                            id = RequestId("test-1"),
                            method = Method.Defined.ToolsList.value,
                        ),
                    ),
                ),
            )
        }

        response.status shouldBe HttpStatusCode.BadRequest
    }

    @Test
    fun `notifications only payload is accepted`() = testApplication {
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

        val firstResult = ListToolsResult(
            tools = listOf(
                Tool(name = "tool-1", inputSchema = ToolSchema()),
            ),
            meta = buildJsonObject { put("label", "first") },
        )
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

        val responses = response.body<List<JSONRPCResponse>>()
        val results = responses.map { it.result }
        results.shouldContainAll(firstResult, secondResult)

        // Check responses' order

        // TODO Uncomment when fixed https://github.com/modelcontextprotocol/kotlin-sdk/issues/548
        /*assertEquals(listOf(firstRequest.id, secondRequest.id), responses.map { it.id })
        val firstMeta = (responses[0] as ListToolsResult).meta
        val secondMeta = (responses[1] as ListResourcesResult).meta
        assertEquals("first", firstMeta?.get("label")?.jsonPrimitive?.content)
        assertEquals("second", secondMeta?.get("label")?.jsonPrimitive?.content)*/
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    fun `POST with a null or empty body returns an error`(payload: String?) = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(enableJsonResponse = true)
        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                transport.send(JSONRPCResponse(message.id, EmptyResult()))
            }
        }

        configureTransportEndpoint(transport)

        val response = client.post(path) {
            addStreamableHeaders()
            setBody(payload)
        }

        response.status shouldBe HttpStatusCode.BadRequest
    }

    @Test
    fun `POST with payload at max size is accepted`() = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(enableJsonResponse = true)
        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                transport.send(JSONRPCResponse(message.id, EmptyResult()))
            }
        }

        configureTransportEndpoint(transport)

        val maxSizePayload = "x".repeat(4 * 1024 * 1024)

        val response = client.post(path) {
            addStreamableHeaders()
            setBody(maxSizePayload)
        }

        response.status shouldBe HttpStatusCode.BadRequest
    }

    @Test
    fun `POST with oversized body returns an error`() = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(enableJsonResponse = true)
        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                transport.send(JSONRPCResponse(message.id, EmptyResult()))
            }
        }

        configureTransportEndpoint(transport)

        val oversizedPayload = "x".repeat(4 * 1024 * 1024 + 1)

        val response = client.post(path) {
            addStreamableHeaders()
            setBody(oversizedPayload)
        }

        response.status shouldBe HttpStatusCode.PayloadTooLarge
    }

    @ParameterizedTest
    @MethodSource("maxBodySizeTestCases")
    fun `POST with custom max request body size validates payload size`(
        maxSize: Long,
        expectedStatus: HttpStatusCode,
    ) = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(
            StreamableHttpServerTransport.Configuration(
                enableJsonResponse = true,
                maxRequestBodySize = maxSize,
            ),
        )
        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                transport.send(JSONRPCResponse(message.id, EmptyResult()))
            }
        }

        configureTransportEndpoint(transport)

        val response = client.post(path) {
            addStreamableHeaders()
            setBody(sizeTestPayload)
        }

        response.status shouldBe expectedStatus
    }

    @Test
    fun `Configuration with negative maxRequestBodySize throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            StreamableHttpServerTransport.Configuration(maxRequestBodySize = -1)
        }
    }

    @Test
    fun `second concurrent GET SSE closes old stream and takes over`() = testApplication {
        val mcpPath = "/mcp"

        application {
            mcpStreamableHttp(mcpPath) {
                Server(
                    Implementation("test-server", "1.0.0"),
                    ServerOptions(capabilities = ServerCapabilities()),
                )
            }
        }

        val client = createTestClient()

        // Step 1: Initialize session via POST
        val initResponse = client.post(mcpPath) {
            addStreamableHeaders()
            setBody(buildInitializeRequestPayload())
        }
        initResponse.status shouldBe HttpStatusCode.OK
        val sessionId = assertNotNull(initResponse.headers[MCP_SESSION_ID_HEADER])

        // Step 2: Open first GET SSE stream
        client.prepareGet(mcpPath) {
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            header(MCP_SESSION_ID_HEADER, sessionId)
            header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
        }.execute { firstResponse ->
            firstResponse.status shouldBe HttpStatusCode.OK
            firstResponse.bodyAsChannel().readUTF8Line()

            // Step 3: Open a second GET — the transport closes the old session
            // and the new stream takes over.
            client.prepareGet(mcpPath) {
                header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
                header(MCP_SESSION_ID_HEADER, sessionId)
                header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
            }.execute { secondResponse ->
                secondResponse.status shouldBe HttpStatusCode.OK
                secondResponse.headers[MCP_SESSION_ID_HEADER] shouldBe sessionId

                // New stream is alive
                val secondChannel = secondResponse.bodyAsChannel()
                val firstLine = secondChannel.readUTF8Line()
                firstLine.shouldNotBeNull()
                secondChannel.isClosedForRead shouldBe false
            }
        }
    }

    @Test
    fun `GET SSE reconnect after previous stream disconnects should succeed`() = testApplication {
        val mcpPath = "/mcp"

        application {
            mcpStreamableHttp(mcpPath) {
                Server(
                    Implementation("test-server", "1.0.0"),
                    ServerOptions(capabilities = ServerCapabilities()),
                )
            }
        }

        val client = createTestClient()

        // Step 1: Initialize session via POST
        val initResponse = client.post(mcpPath) {
            addStreamableHeaders()
            setBody(buildInitializeRequestPayload())
        }
        initResponse.status shouldBe HttpStatusCode.OK
        val sessionId = assertNotNull(initResponse.headers[MCP_SESSION_ID_HEADER])

        // Step 2: Open and then close a GET SSE stream
        client.prepareGet(mcpPath) {
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            header(MCP_SESSION_ID_HEADER, sessionId)
            header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
        }.execute { response ->
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsChannel().readUTF8Line()
        }

        // Step 3: Immediately reconnect — the transport should close the stale
        // stream and allow the new one.
        client.prepareGet(mcpPath) {
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            header(MCP_SESSION_ID_HEADER, sessionId)
            header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
        }.execute { response ->
            response.status shouldBe HttpStatusCode.OK
            response.headers[MCP_SESSION_ID_HEADER] shouldBe sessionId

            val channel = response.bodyAsChannel()
            val firstLine = channel.readUTF8Line()
            firstLine.shouldNotBeNull()
            channel.isClosedForRead shouldBe false
        }
    }

    @Test
    fun `GET SSE stream includes Mcp-Session-Id header and stays open`() = testApplication {
        val mcpPath = "/mcp"

        application {
            mcpStreamableHttp(mcpPath) {
                Server(
                    Implementation("test-server", "1.0.0"),
                    ServerOptions(capabilities = ServerCapabilities()),
                )
            }
        }

        val client = createTestClient()

        // Step 1: Initialize session via POST
        val initResponse = client.post(mcpPath) {
            addStreamableHeaders()
            setBody(buildInitializeRequestPayload())
        }
        initResponse.status shouldBe HttpStatusCode.OK
        val sessionId = assertNotNull(initResponse.headers[MCP_SESSION_ID_HEADER])

        // Step 2: Open GET SSE stream with session ID
        client.prepareGet(mcpPath) {
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            header(MCP_SESSION_ID_HEADER, sessionId)
            header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
        }.execute { response ->
            // Verify Mcp-Session-Id is present on the SSE response
            response.status shouldBe HttpStatusCode.OK
            response.headers[MCP_SESSION_ID_HEADER] shouldBe sessionId

            // Verify the stream is alive by reading at least one line (flush event)
            val channel = response.bodyAsChannel()
            val firstLine = channel.readUTF8Line()
            firstLine.shouldNotBeNull()
            channel.isClosedForRead shouldBe false
        }
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
