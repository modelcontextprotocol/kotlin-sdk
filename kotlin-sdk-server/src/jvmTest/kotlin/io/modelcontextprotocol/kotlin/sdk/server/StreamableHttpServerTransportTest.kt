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
import io.ktor.client.request.get
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
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import io.ktor.utils.io.readUTF8Line
import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotification
import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
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

        @JvmStatic
        fun postAcceptHeaderCases(): List<Arguments> = listOf(
            Arguments.of("application/json, text/event-stream", HttpStatusCode.OK),
            Arguments.of("Application/JSON;q=0.9, TEXT/Event-Stream;charset=utf-8", HttpStatusCode.OK),
            Arguments.of("*/*", HttpStatusCode.OK),
            Arguments.of("application/*, text/*", HttpStatusCode.OK),
            Arguments.of(null, HttpStatusCode.OK),
            Arguments.of("application/json", HttpStatusCode.NotAcceptable),
            Arguments.of("text/event-stream", HttpStatusCode.NotAcceptable),
            Arguments.of("application/jsonp, text/event-stream-bogus", HttpStatusCode.NotAcceptable),
            Arguments.of("application/json, text/event-stream;q=0", HttpStatusCode.NotAcceptable),
        )

        @JvmStatic
        fun getAcceptHeaderCases() = listOf(
            "application/json",
            "text/event-stream-bogus",
            "text/event-stream;q=0",
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

    @ParameterizedTest
    @MethodSource("postAcceptHeaderCases")
    fun `POST Accept header is matched as media ranges`(acceptHeader: String?, expectedStatus: HttpStatusCode) =
        testApplication {
            configTestServer()

            // Bare client: the ContentNegotiation plugin would supply an Accept header of its own,
            // leaving the absent-header case untestable.
            val client = createClient {}

            val transport = StreamableHttpServerTransport(enableJsonResponse = true)
            val onMessageCalled = AtomicBoolean(false)
            transport.onMessage { message ->
                onMessageCalled.set(true)
                if (message is JSONRPCRequest) {
                    transport.send(JSONRPCResponse(message.id, EmptyResult()))
                }
            }

            configureTransportEndpoint(transport)

            val response = client.post(path) {
                contentType(ContentType.Application.Json)
                acceptHeader?.let { header(HttpHeaders.Accept, it) }
                setBody(McpJson.encodeToString(JSONRPCMessage.serializer(), buildInitializeRequestPayload()))
            }

            assertEquals(expectedStatus, response.status)
            assertEquals(
                expectedStatus == HttpStatusCode.OK,
                onMessageCalled.get(),
                "Only a request that clears the Accept gate may reach the transport",
            )
        }

    @Test
    fun `POST with an unparsable Accept header is rejected without failing the request`() = testApplication {
        configTestServer()

        val client = createClient {}

        val transport = StreamableHttpServerTransport(enableJsonResponse = true)
        val onMessageCalled = AtomicBoolean(false)
        transport.onMessage {
            onMessageCalled.set(true)
        }

        configureTransportEndpoint(transport)

        val response = client.post(path) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "not-a-media-type")
            setBody(McpJson.encodeToString(JSONRPCMessage.serializer(), buildInitializeRequestPayload()))
        }

        // The exact code is decided by content negotiation once the transport has rejected the request.
        assertTrue(response.status.value in 400..499, "Expected a client error, got ${response.status}")
        assertFalse(onMessageCalled.get(), "Transport should not deliver messages when headers are invalid")
    }

    @ParameterizedTest
    @MethodSource("getAcceptHeaderCases")
    fun `GET whose Accept header admits no event-stream range is rejected`(acceptHeader: String) = testApplication {
        configTestServer()

        val client = createClient {}

        val transport = StreamableHttpServerTransport(enableJsonResponse = true)

        application {
            routing {
                get(path) {
                    transport.handleGetRequest(FakeServerSSESession(call, call.coroutineContext), call)
                }
            }
        }

        val response = client.get(path) {
            header(HttpHeaders.Accept, acceptHeader)
        }

        assertEquals(HttpStatusCode.NotAcceptable, response.status)
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
    fun `second initialization request returns JSON-RPC error with request id`() = testApplication {
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

        val secondRequest = buildInitializeRequestPayload().copy(id = RequestId("second-init"))
        val secondResponse = client.post(path) {
            addStreamableHeaders()
            header("mcp-session-id", firstResponse.headers[MCP_SESSION_ID_HEADER])
            setBody(secondRequest)
        }

        secondResponse.status shouldBe HttpStatusCode.BadRequest
        val error = secondResponse.body<JSONRPCError>()
        error.id shouldBe secondRequest.id
        error.error.message shouldBe "Invalid Request: Server already initialized"
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

    @Test
    fun `json response waits for a response produced after message delivery returns`() = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(
            StreamableHttpServerTransport.Configuration(enableJsonResponse = true),
        )
        // Simulates concurrent dispatch: message delivery returns before the handler responds.
        val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            transport.onMessage { message ->
                if (message is JSONRPCRequest) {
                    if (message.method == Method.Defined.Initialize.value) {
                        transport.send(JSONRPCResponse(message.id, EmptyResult()))
                    } else {
                        handlerScope.launch {
                            delay(100)
                            transport.send(JSONRPCResponse(message.id, EmptyResult()))
                        }
                    }
                }
            }

            configureTransportEndpoint(transport)

            val initResponse = client.post(path) {
                addStreamableHeaders()
                setBody(buildInitializeRequestPayload())
            }
            initResponse.status shouldBe HttpStatusCode.OK

            val request = JSONRPCRequest(id = RequestId("async-1"), method = Method.Defined.ToolsList.value)
            val response = withTimeout(10.seconds) {
                client.post(path) {
                    addStreamableHeaders()
                    header(MCP_SESSION_ID_HEADER, initResponse.headers[MCP_SESSION_ID_HEADER])
                    setBody(encodeMessages(listOf(request)))
                }
            }

            response.status shouldBe HttpStatusCode.OK
            response.body<JSONRPCResponse>() shouldBe JSONRPCResponse(request.id)
        } finally {
            handlerScope.cancel()
        }
    }

    @Test
    fun `json response for a batch waits until every asynchronously produced response is ready`() = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(
            StreamableHttpServerTransport.Configuration(enableJsonResponse = true),
        )
        val firstRequest = JSONRPCRequest(id = RequestId("slow"), method = Method.Defined.ToolsList.value)
        val secondRequest = JSONRPCRequest(id = RequestId("fast"), method = Method.Defined.ResourcesList.value)

        // Simulates concurrent dispatch with out-of-order completion: the second request responds
        // first; the POST must stay open until the slower first response settles too.
        val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            transport.onMessage { message ->
                if (message is JSONRPCRequest) {
                    if (message.method == Method.Defined.Initialize.value) {
                        transport.send(JSONRPCResponse(message.id, EmptyResult()))
                    } else {
                        handlerScope.launch {
                            delay(if (message.id == firstRequest.id) 200 else 50)
                            transport.send(JSONRPCResponse(message.id, EmptyResult()))
                        }
                    }
                }
            }

            configureTransportEndpoint(transport)

            val initResponse = client.post(path) {
                addStreamableHeaders()
                setBody(buildInitializeRequestPayload())
            }
            initResponse.status shouldBe HttpStatusCode.OK

            val response = withTimeout(10.seconds) {
                client.post(path) {
                    addStreamableHeaders()
                    header(MCP_SESSION_ID_HEADER, initResponse.headers[MCP_SESSION_ID_HEADER])
                    setBody(encodeMessages(listOf(firstRequest, secondRequest)))
                }
            }

            response.status shouldBe HttpStatusCode.OK
            val responses = response.body<List<JSONRPCResponse>>()
            responses.map { it.id }.toSet() shouldBe setOf(firstRequest.id, secondRequest.id)
        } finally {
            handlerScope.cancel()
        }
    }

    @Test
    fun `json response POST completes with 202 when its only request is cancelled`() = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(
            StreamableHttpServerTransport.Configuration(enableJsonResponse = true),
        )
        val requestDelivered = CompletableDeferred<Unit>()
        // Concurrent dispatch: delivery returns before a response is produced. This request is never
        // answered here, modelling a handler cancelled by notifications/cancelled.
        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                if (message.method == Method.Defined.Initialize.value) {
                    transport.send(JSONRPCResponse(message.id, EmptyResult()))
                } else {
                    requestDelivered.complete(Unit)
                }
            }
        }

        configureTransportEndpoint(transport)

        val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val initResponse = client.post(path) {
                addStreamableHeaders()
                setBody(buildInitializeRequestPayload())
            }
            initResponse.status shouldBe HttpStatusCode.OK
            val sessionId = initResponse.headers[MCP_SESSION_ID_HEADER]

            val request = JSONRPCRequest(id = RequestId("cancel-me"), method = Method.Defined.ToolsList.value)
            val requestPost = clientScope.async {
                client.post(path) {
                    addStreamableHeaders()
                    header(MCP_SESSION_ID_HEADER, sessionId)
                    setBody(encodeMessages(listOf(request)))
                }
            }

            // The request must be registered in-flight before it can be cancelled.
            withTimeout(5.seconds) { requestDelivered.await() }

            val cancellation = CancelledNotification(
                CancelledNotificationParams(requestId = request.id, reason = "client cancelled"),
            )
            val cancelResponse = client.post(path) {
                addStreamableHeaders()
                header(MCP_SESSION_ID_HEADER, sessionId)
                setBody(encodeMessages(listOf(cancellation.toJSON())))
            }
            cancelResponse.status shouldBe HttpStatusCode.Accepted

            // Without retirement of the cancelled id this await never completes and the timeout fires.
            val response = withTimeout(10.seconds) { requestPost.await() }
            response.status shouldBe HttpStatusCode.Accepted
        } finally {
            clientScope.cancel()
        }
    }

    @Test
    fun `json response for a batch returns only the non-cancelled responses`() = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(
            StreamableHttpServerTransport.Configuration(enableJsonResponse = true),
        )
        val answeredA = JSONRPCRequest(id = RequestId("a"), method = Method.Defined.ToolsList.value)
        val answeredB = JSONRPCRequest(id = RequestId("b"), method = Method.Defined.ToolsList.value)
        val cancelledC = JSONRPCRequest(id = RequestId("c"), method = Method.Defined.ToolsList.value)

        val cancelledDelivered = CompletableDeferred<Unit>()
        val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            transport.onMessage { message ->
                if (message is JSONRPCRequest) {
                    when {
                        message.method == Method.Defined.Initialize.value ->
                            transport.send(JSONRPCResponse(message.id, EmptyResult()))

                        message.id == answeredA.id || message.id == answeredB.id ->
                            handlerScope.launch { transport.send(JSONRPCResponse(message.id, EmptyResult())) }

                        message.id == cancelledC.id -> cancelledDelivered.complete(Unit)
                    }
                }
            }

            configureTransportEndpoint(transport)

            val initResponse = client.post(path) {
                addStreamableHeaders()
                setBody(buildInitializeRequestPayload())
            }
            initResponse.status shouldBe HttpStatusCode.OK
            val sessionId = initResponse.headers[MCP_SESSION_ID_HEADER]

            val batchPost = handlerScope.async {
                client.post(path) {
                    addStreamableHeaders()
                    header(MCP_SESSION_ID_HEADER, sessionId)
                    setBody(encodeMessages(listOf(answeredA, answeredB, cancelledC)))
                }
            }

            withTimeout(5.seconds) { cancelledDelivered.await() }

            val cancellation = CancelledNotification(CancelledNotificationParams(requestId = cancelledC.id))
            client.post(path) {
                addStreamableHeaders()
                header(MCP_SESSION_ID_HEADER, sessionId)
                setBody(encodeMessages(listOf(cancellation.toJSON())))
            }.status shouldBe HttpStatusCode.Accepted

            val response = withTimeout(10.seconds) { batchPost.await() }
            response.status shouldBe HttpStatusCode.OK
            val responses = response.body<List<JSONRPCResponse>>()
            responses.map { it.id }.toSet() shouldBe setOf(answeredA.id, answeredB.id)
        } finally {
            handlerScope.cancel()
        }
    }

    @Test
    fun `json response send for a retired request id is dropped without error`() = runTest {
        val transport = StreamableHttpServerTransport(enableJsonResponse = true)
        // No stream is mapped for this id (already retired by disconnect/cancellation cleanup);
        // a late terminal response must be dropped quietly, not raise "No connection established".
        transport.send(JSONRPCResponse(RequestId("retired"), EmptyResult()))
    }

    @Test
    fun `sse response send for an unknown request id still errors`() = runTest {
        val transport = StreamableHttpServerTransport(enableJsonResponse = false)
        assertFailsWith<IllegalStateException> {
            transport.send(JSONRPCResponse(RequestId("unknown"), EmptyResult()))
        }
    }

    @Test
    fun `sse terminal response is dropped without leaking when the per-request stream is gone`() = testApplication {
        configTestServer()
        val client = createTestClient()

        val transport = StreamableHttpServerTransport(
            StreamableHttpServerTransport.Configuration(enableJsonResponse = false),
        )
        transport.setSessionIdGenerator(null) // stateless: accept requests without an init handshake
        val requestId = RequestId("held-request")
        val delivered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                delivered.complete(Unit)
                release.await() // keep the POST and its stream mapping alive until the test releases it
            }
        }

        application {
            routing {
                post(path) {
                    transport.handlePostRequest(FakeServerSSESession(call, call.coroutineContext), call)
                }
            }
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val post = scope.async {
                client.post(path) {
                    addStreamableHeaders()
                    setBody(
                        encodeMessages(
                            listOf(JSONRPCRequest(id = requestId, method = Method.Defined.ToolsList.value)),
                        ),
                    )
                }
            }
            withTimeout(5.seconds) { delivered.await() }

            // Drop the per-request SSE stream while the request is still mapped in-flight; the handler's
            // terminal response is now undeliverable and must be dropped quietly rather than raise
            // "No connection established" (which would also strand the request mappings).
            transport.closeSseStream(requestId)
            transport.send(JSONRPCResponse(requestId, EmptyResult()))

            post.cancel()
        } finally {
            release.complete(Unit)
            scope.cancel()
        }
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

    @Test
    fun `POST with oversized chunked body and no Content-Length returns 413`() = testApplication {
        configTestServer()

        val client = createTestClient()

        val transport = StreamableHttpServerTransport(
            StreamableHttpServerTransport.Configuration(enableJsonResponse = true, maxRequestBodySize = 1024),
        )
        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                transport.send(JSONRPCResponse(message.id, EmptyResult()))
            }
        }

        configureTransportEndpoint(transport)

        // Stream the body as a channel so the client uses chunked transfer-encoding with no
        // Content-Length header: the size limit must hold without trusting the (absent) header.
        val oversized = ByteReadChannel("x".repeat(4096).encodeToByteArray())
        val response = client.post(path) {
            addStreamableHeaders()
            setBody(oversized)
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
            header(HttpHeaders.Host, "localhost")
            addStreamableHeaders()
            setBody(buildInitializeRequestPayload())
        }
        initResponse.status shouldBe HttpStatusCode.OK
        val sessionId = assertNotNull(initResponse.headers[MCP_SESSION_ID_HEADER])

        // Step 2: Open first GET SSE stream
        client.prepareGet(mcpPath) {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            header(MCP_SESSION_ID_HEADER, sessionId)
            header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
        }.execute { firstResponse ->
            firstResponse.status shouldBe HttpStatusCode.OK
            firstResponse.bodyAsChannel().readUTF8Line()

            // Step 3: Open a second GET — the transport closes the old session
            // and the new stream takes over.
            client.prepareGet(mcpPath) {
                header(HttpHeaders.Host, "localhost")
                header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
                header(MCP_SESSION_ID_HEADER, sessionId)
                header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
            }.execute { secondResponse ->
                secondResponse.status shouldBe HttpStatusCode.OK
                secondResponse.headers[MCP_SESSION_ID_HEADER] shouldBe sessionId

                // New stream is alive
                val secondChannel = secondResponse.bodyAsChannel()
                val firstLine = secondChannel.readLine()
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
            header(HttpHeaders.Host, "localhost")
            addStreamableHeaders()
            setBody(buildInitializeRequestPayload())
        }
        initResponse.status shouldBe HttpStatusCode.OK
        val sessionId = assertNotNull(initResponse.headers[MCP_SESSION_ID_HEADER])

        // Step 2: Open and then close a GET SSE stream
        client.prepareGet(mcpPath) {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            header(MCP_SESSION_ID_HEADER, sessionId)
            header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
        }.execute { response ->
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsChannel().readLine()
        }

        // Step 3: Immediately reconnect — the transport should close the stale
        // stream and allow the new one.
        client.prepareGet(mcpPath) {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            header(MCP_SESSION_ID_HEADER, sessionId)
            header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
        }.execute { response ->
            response.status shouldBe HttpStatusCode.OK
            response.headers[MCP_SESSION_ID_HEADER] shouldBe sessionId

            val channel = response.bodyAsChannel()
            val firstLine = channel.readLine()
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
            header(HttpHeaders.Host, "localhost")
            addStreamableHeaders()
            setBody(buildInitializeRequestPayload())
        }
        initResponse.status shouldBe HttpStatusCode.OK
        val sessionId = assertNotNull(initResponse.headers[MCP_SESSION_ID_HEADER])

        // Step 2: Open GET SSE stream with session ID
        client.prepareGet(mcpPath) {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            header(MCP_SESSION_ID_HEADER, sessionId)
            header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
        }.execute { response ->
            // Verify Mcp-Session-Id is present on the SSE response
            response.status shouldBe HttpStatusCode.OK
            response.headers[MCP_SESSION_ID_HEADER] shouldBe sessionId

            // Verify the stream is alive by reading at least one line (flush event)
            val channel = response.bodyAsChannel()
            val firstLine = channel.readLine()
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

/** No-op [ServerSSESession] double that lets a POST route drive [StreamableHttpServerTransport] in SSE mode. */
private class FakeServerSSESession(
    override val call: ApplicationCall,
    override val coroutineContext: CoroutineContext,
) : ServerSSESession {
    override suspend fun send(event: ServerSentEvent) {}
    override suspend fun close() {}
}
