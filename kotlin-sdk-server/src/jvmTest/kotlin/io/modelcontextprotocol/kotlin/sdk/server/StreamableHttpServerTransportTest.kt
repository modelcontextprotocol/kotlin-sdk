package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.discard
import io.ktor.utils.io.readLine
import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotification
import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
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
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class StreamableHttpServerTransportTest {
    companion object {
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
            Arguments.of("not-a-media-type", HttpStatusCode.NotAcceptable),
        )

        @JvmStatic
        fun unparsableBodyCases(): List<Arguments> = listOf(
            Arguments.of("", RPCError.ErrorCode.PARSE_ERROR),
            Arguments.of("lolol", RPCError.ErrorCode.INVALID_REQUEST),
        )

        private const val SIZE_TEST_PAYLOAD_LENGTH = 64

        @JvmStatic
        fun maxBodySizeTestCases(): List<Arguments> = listOf(
            Arguments.of(SIZE_TEST_PAYLOAD_LENGTH - 1L, HttpStatusCode.PayloadTooLarge),
            // At the limit the body is read, then rejected as unparsable.
            Arguments.of(SIZE_TEST_PAYLOAD_LENGTH.toLong(), HttpStatusCode.BadRequest),
        )
    }

    private val path = "/transport"
    private val mcpPath = "/mcp"

    @ParameterizedTest
    @MethodSource("postAcceptHeaderCases")
    fun `POST Accept header is matched as media ranges`(acceptHeader: String?, expectedStatus: HttpStatusCode) =
        testApplication {
            val transport = postEndpoint()
            val onMessageCalled = AtomicBoolean(false)
            transport.onMessage { message ->
                onMessageCalled.set(true)
                if (message is JSONRPCRequest) transport.send(JSONRPCResponse(message.id, EmptyResult()))
            }

            // The default client sends no Accept header of its own, so the absent-header row is testable.
            val response = client.post(path) {
                contentType(ContentType.Application.Json)
                acceptHeader?.let { header(HttpHeaders.Accept, it) }
                setBody(encode(initializeRequest()))
            }

            response.status shouldBe expectedStatus
            withClue("only a request that clears the Accept gate may reach the transport") {
                onMessageCalled.get() shouldBe (expectedStatus == HttpStatusCode.OK)
            }
        }

    @Test
    fun `GET whose Accept header admits no event-stream range is rejected`() = testApplication {
        val transport = StreamableHttpServerTransport(StreamableHttpServerTransport.Configuration())
        application {
            routing {
                get(path) {
                    transport.handleGetRequest(FakeServerSSESession(call, call.coroutineContext), call)
                }
            }
        }

        val response = client.get(path) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }

        response.status shouldBe HttpStatusCode.NotAcceptable
    }

    @Test
    fun `initialization request establishes session and returns json response`() = testApplication {
        val client = jsonClient()
        val transport = postEndpoint()
        transport.setSessionIdGenerator { "session-test-id" }
        var observedRequest: JSONRPCRequest? = null
        transport.onMessage { message ->
            if (message is JSONRPCRequest) {
                observedRequest = message
                transport.send(JSONRPCResponse(message.id, EmptyResult()))
            }
        }

        val response = client.post(path) {
            streamableHeaders()
            setBody(initializeRequest())
        }

        response.status shouldBe HttpStatusCode.OK
        response.headers[MCP_SESSION_ID_HEADER] shouldBe "session-test-id"
        val request = observedRequest.shouldNotBeNull()
        response.body<JSONRPCResponse>() shouldBe JSONRPCResponse(request.id)
    }

    @Test
    fun `second initialization request returns JSON-RPC error with request id`() = testApplication {
        val client = jsonClient()
        postEndpoint().answerRequests()
        val sessionId = client.initialize()

        val secondRequest = initializeRequest().copy(id = RequestId("second-init"))
        val response = client.post(path) {
            streamableHeaders()
            header(MCP_SESSION_ID_HEADER, sessionId)
            setBody(secondRequest)
        }

        response.status shouldBe HttpStatusCode.BadRequest
        val error = response.body<JSONRPCError>()
        error.id shouldBe secondRequest.id
        error.error.message shouldBe "Invalid Request: Server already initialized"
    }

    @Test
    fun `init request with unsupported protocol version returns an HTTP error`() = testApplication {
        val client = jsonClient()
        postEndpoint().answerRequests()
        val request = initializeRequest()

        val response = client.post(path) {
            streamableHeaders()
            header("mcp-protocol-version", "1900-01-01")
            setBody(request)
        }

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers[MCP_SESSION_ID_HEADER] shouldBe null
        response.body<JSONRPCError>().id shouldBe request.id
    }

    @Test
    fun `request with unsupported protocol version returns an HTTP error`() = testApplication {
        val client = jsonClient()
        postEndpoint().answerRequests()
        val sessionId = client.initialize().shouldNotBeNull()

        val response = client.postMessages(sessionId, toolsList("test-1")) {
            header("mcp-protocol-version", "1900-01-01")
        }

        response.status shouldBe HttpStatusCode.BadRequest
        response.body<JSONRPCError>().id shouldBe RequestId("test-1")
    }

    @Test
    fun `batch with an initialization request echoes the initialize id when rejected`() = testApplication {
        val client = jsonClient()
        postEndpoint().answerRequests()
        val initRequest = initializeRequest()

        val response = client.postMessages(null, initRequest, toolsList("extra"))

        response.status shouldBe HttpStatusCode.BadRequest
        val error = response.body<JSONRPCError>()
        error.error.message shouldBe "Invalid Request: Only one initialization request is allowed"
        error.id shouldBe initRequest.id
    }

    @Test
    fun `non-init request before initialization echoes the request id`() = testApplication {
        val client = jsonClient()
        postEndpoint().answerRequests()

        val response = client.postMessages(null, toolsList("before-init"))

        response.status shouldBe HttpStatusCode.BadRequest
        val error = response.body<JSONRPCError>()
        error.error.message shouldBe "Bad Request: Server not initialized"
        error.id shouldBe RequestId("before-init")
    }

    @Test
    fun `notifications only payload is acknowledged with a bodiless 202`() = testApplication {
        val client = jsonClient()
        val transport = postEndpoint()
        val receivedMessages = mutableListOf<JSONRPCMessage>()
        transport.onMessage { message ->
            receivedMessages.add(message)
            if (message is JSONRPCRequest) transport.send(JSONRPCResponse(message.id, EmptyResult()))
        }
        val initRequest = initializeRequest()
        val sessionId = client.initialize(initRequest)

        val response = client.postMessages(sessionId, InitializedNotification().toJSON())

        response.status shouldBe HttpStatusCode.Accepted
        response.bodyAsText() shouldBe ""
        receivedMessages shouldBe listOf(initRequest, InitializedNotification().toJSON())
    }

    @Test
    fun `json response for a batch waits until every asynchronously produced response is ready`() = testApplication {
        val client = jsonClient()
        val transport = postEndpoint()
        val slowRequest = toolsList("slow")
        val fastRequest = JSONRPCRequest(id = RequestId("fast"), method = Method.Defined.ResourcesList.value)
        val slowResult = ListToolsResult(tools = listOf(Tool(name = "tool-1", inputSchema = ToolSchema())))
        val fastResult = ListResourcesResult(resources = emptyList())
        val fastAnswered = CompletableDeferred<Unit>()

        // Concurrent dispatch with out-of-order completion: delivery returns before any response is
        // produced and the second request is answered first; the POST must stay open for both.
        val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            transport.onMessage { message ->
                if (message !is JSONRPCRequest) return@onMessage
                when (message.id) {
                    slowRequest.id -> handlerScope.launch {
                        fastAnswered.await()
                        transport.send(JSONRPCResponse(message.id, slowResult))
                    }

                    fastRequest.id -> handlerScope.launch {
                        transport.send(JSONRPCResponse(message.id, fastResult))
                        fastAnswered.complete(Unit)
                    }

                    else -> transport.send(JSONRPCResponse(message.id, EmptyResult()))
                }
            }
            val sessionId = client.initialize()

            val response = withTimeout(10.seconds) { client.postMessages(sessionId, slowRequest, fastRequest) }

            response.status shouldBe HttpStatusCode.OK
            response.body<List<JSONRPCResponse>>().map { it.result } shouldContainExactlyInAnyOrder
                listOf(slowResult, fastResult)
        } finally {
            handlerScope.cancel()
        }
    }

    @Test
    fun `json response POST completes with 202 when its only request is cancelled`() = testApplication {
        val client = jsonClient()
        val transport = postEndpoint()
        val requestDelivered = CompletableDeferred<Unit>()
        // Concurrent dispatch: delivery returns before a response is produced. This request is never
        // answered here, modelling a handler cancelled by notifications/cancelled.
        transport.onMessage { message ->
            if (message !is JSONRPCRequest) return@onMessage
            if (message.method == Method.Defined.Initialize.value) {
                transport.send(JSONRPCResponse(message.id, EmptyResult()))
            } else {
                requestDelivered.complete(Unit)
            }
        }
        val sessionId = client.initialize()
        val request = toolsList("cancel-me")

        val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val requestPost = clientScope.async { client.postMessages(sessionId, request) }

            // The request must be registered in-flight before it can be cancelled.
            withTimeout(5.seconds) { requestDelivered.await() }

            val cancellation = CancelledNotification(
                CancelledNotificationParams(requestId = request.id, reason = "client cancelled"),
            )
            client.postMessages(sessionId, cancellation.toJSON()).status shouldBe HttpStatusCode.Accepted

            // Without retirement of the cancelled id this await never completes and the timeout fires.
            withTimeout(10.seconds) { requestPost.await() }.status shouldBe HttpStatusCode.Accepted
        } finally {
            clientScope.cancel()
        }
    }

    @Test
    fun `json response for a batch returns only the non-cancelled responses`() = testApplication {
        val client = jsonClient()
        val transport = postEndpoint()
        val answeredA = toolsList("a")
        val answeredB = toolsList("b")
        val cancelledC = toolsList("c")
        val cancelledDelivered = CompletableDeferred<Unit>()

        val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            transport.onMessage { message ->
                if (message !is JSONRPCRequest) return@onMessage
                when (message.id) {
                    cancelledC.id -> cancelledDelivered.complete(Unit)

                    answeredA.id, answeredB.id -> handlerScope.launch {
                        transport.send(JSONRPCResponse(message.id, EmptyResult()))
                    }

                    else -> transport.send(JSONRPCResponse(message.id, EmptyResult()))
                }
            }
            val sessionId = client.initialize()

            val batchPost = handlerScope.async { client.postMessages(sessionId, answeredA, answeredB, cancelledC) }
            withTimeout(5.seconds) { cancelledDelivered.await() }

            val cancellation = CancelledNotification(CancelledNotificationParams(requestId = cancelledC.id))
            client.postMessages(sessionId, cancellation.toJSON()).status shouldBe HttpStatusCode.Accepted

            val response = withTimeout(10.seconds) { batchPost.await() }
            response.status shouldBe HttpStatusCode.OK
            response.body<List<JSONRPCResponse>>().map { it.id } shouldContainExactlyInAnyOrder
                listOf(answeredA.id, answeredB.id)
        } finally {
            handlerScope.cancel()
        }
    }

    @Test
    fun `json response send for a retired request id is dropped without error`() = runTest {
        val transport = StreamableHttpServerTransport(
            StreamableHttpServerTransport.Configuration(enableJsonResponse = true),
        )
        // No stream is mapped for this id (already retired by disconnect/cancellation cleanup);
        // a late terminal response must be dropped quietly, not raise "No connection established".
        transport.send(JSONRPCResponse(RequestId("retired"), EmptyResult()))
    }

    @Test
    fun `sse response send for an unknown request id still errors`() = runTest {
        val transport = StreamableHttpServerTransport(
            StreamableHttpServerTransport.Configuration(enableJsonResponse = false),
        )
        shouldThrow<IllegalStateException> {
            transport.send(JSONRPCResponse(RequestId("unknown"), EmptyResult()))
        }
    }

    @Test
    fun `sse terminal response is dropped without leaking when the per-request stream is gone`() = testApplication {
        val client = jsonClient()
        val transport = postEndpoint(StreamableHttpServerTransport.Configuration(enableJsonResponse = false))
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

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val post = scope.async { client.postMessages(null, toolsList(requestId)) }
            withTimeout(5.seconds) { delivered.await() }

            // Drop the per-request SSE stream while the request is still in flight: its terminal response
            // is now undeliverable and must be dropped quietly rather than raise "No connection established".
            transport.closeSseStream(requestId)
            val response = JSONRPCResponse(requestId, EmptyResult())
            transport.send(response)

            // The request was retired rather than leaked, so its id is now unknown to the transport.
            shouldThrow<IllegalStateException> { transport.send(response) }

            post.cancel()
        } finally {
            release.complete(Unit)
            scope.cancel()
        }
    }

    @ParameterizedTest
    @MethodSource("unparsableBodyCases")
    fun `POST with an unparsable body returns a JSON-RPC error`(body: String, expectedCode: Int) = testApplication {
        val client = jsonClient()
        postEndpoint().answerRequests()

        val response = client.post(path) {
            streamableHeaders()
            setBody(body)
        }

        response.status shouldBe HttpStatusCode.BadRequest
        response.body<JSONRPCError>().error.code shouldBe expectedCode
    }

    @Test
    fun `POST with oversized chunked body and no Content-Length returns 413`() = testApplication {
        postEndpoint(
            StreamableHttpServerTransport.Configuration(enableJsonResponse = true, maxRequestBodySize = 1024),
        ).answerRequests()

        // Stream the body as a channel so the client uses chunked transfer-encoding with no
        // Content-Length header: the size limit must hold without trusting the (absent) header.
        val response = client.post(path) {
            streamableHeaders()
            setBody(ByteReadChannel("x".repeat(4096).encodeToByteArray()))
        }

        response.status shouldBe HttpStatusCode.PayloadTooLarge
    }

    @ParameterizedTest
    @MethodSource("maxBodySizeTestCases")
    fun `POST with custom max request body size validates payload size`(
        maxSize: Long,
        expectedStatus: HttpStatusCode,
    ) = testApplication {
        postEndpoint(
            StreamableHttpServerTransport.Configuration(enableJsonResponse = true, maxRequestBodySize = maxSize),
        ).answerRequests()

        val response = client.post(path) {
            streamableHeaders()
            setBody("x".repeat(SIZE_TEST_PAYLOAD_LENGTH))
        }

        response.status shouldBe expectedStatus
    }

    @Test
    fun `Configuration with negative maxRequestBodySize throws IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            StreamableHttpServerTransport.Configuration(maxRequestBodySize = -1)
        }
    }

    @Test
    fun `second concurrent GET SSE closes old stream and takes over`() = testApplication {
        application {
            mcpStreamableHttp(mcpPath) { testServer() }
        }
        val sessionId = client.initialize(endpoint = mcpPath).shouldNotBeNull()

        client.prepareGet(mcpPath) { standaloneStreamHeaders(sessionId) }.execute { firstResponse ->
            firstResponse.status shouldBe HttpStatusCode.OK
            val firstChannel = firstResponse.bodyAsChannel()
            firstChannel.readLine()

            client.prepareGet(mcpPath) { standaloneStreamHeaders(sessionId) }.execute { secondResponse ->
                secondResponse.assertLiveStream(sessionId)

                // The takeover closes the old stream: draining it reaches the end instead of timing out.
                withTimeout(5.seconds) { firstChannel.discard() }
                firstChannel.isClosedForRead shouldBe true
            }
        }
    }

    @Test
    fun `GET SSE reconnect after previous stream disconnects should succeed`() = testApplication {
        application {
            mcpStreamableHttp(mcpPath) { testServer() }
        }
        val sessionId = client.initialize(endpoint = mcpPath).shouldNotBeNull()

        client.prepareGet(mcpPath) { standaloneStreamHeaders(sessionId) }.execute { response ->
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsChannel().readLine()
        }

        // Immediately reconnect: the transport replaces the stale stream and allows the new one.
        client.prepareGet(mcpPath) { standaloneStreamHeaders(sessionId) }.execute { response ->
            response.assertLiveStream(sessionId)
        }
    }

    /** Serves POSTs at [path] through a new transport; SSE mode gets a no-op SSE session. */
    private fun ApplicationTestBuilder.postEndpoint(
        configuration: StreamableHttpServerTransport.Configuration =
            StreamableHttpServerTransport.Configuration(enableJsonResponse = true),
    ): StreamableHttpServerTransport {
        val transport = StreamableHttpServerTransport(configuration)
        application {
            install(ServerContentNegotiation) { json(McpJson) }
            routing {
                post(path) {
                    val session = if (configuration.enableJsonResponse) {
                        null
                    } else {
                        FakeServerSSESession(call, call.coroutineContext)
                    }
                    transport.handlePostRequest(session, call)
                }
            }
        }
        return transport
    }

    /** Answers every request with an empty result. */
    private fun StreamableHttpServerTransport.answerRequests() = onMessage { message ->
        if (message is JSONRPCRequest) send(JSONRPCResponse(message.id, EmptyResult()))
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
        install(ClientContentNegotiation) { json(McpJson) }
    }

    /** Initializes a session at [endpoint] and returns the session id the transport assigned, if any. */
    private suspend fun HttpClient.initialize(
        request: JSONRPCRequest = initializeRequest(),
        endpoint: String = path,
    ): String? {
        val response = post(endpoint) {
            header(HttpHeaders.Host, "localhost")
            streamableHeaders()
            setBody(encode(request))
        }
        response.status shouldBe HttpStatusCode.OK
        return response.headers[MCP_SESSION_ID_HEADER]
    }

    /** Posts [messages] as one JSON-RPC batch. */
    private suspend fun HttpClient.postMessages(
        sessionId: String?,
        vararg messages: JSONRPCMessage,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = post(path) {
        streamableHeaders()
        sessionId?.let { header(MCP_SESSION_ID_HEADER, it) }
        block()
        setBody(McpJson.encodeToString(ListSerializer(JSONRPCMessage.serializer()), messages.toList()))
    }

    private fun HttpRequestBuilder.standaloneStreamHeaders(sessionId: String) {
        header(HttpHeaders.Host, "localhost")
        header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
        header(MCP_SESSION_ID_HEADER, sessionId)
        header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
    }

    /** A live standalone stream carries the session id and stays open after its first event. */
    private suspend fun HttpResponse.assertLiveStream(sessionId: String) {
        status shouldBe HttpStatusCode.OK
        headers[MCP_SESSION_ID_HEADER] shouldBe sessionId
        val channel = bodyAsChannel()
        channel.readLine().shouldNotBeNull()
        channel.isClosedForRead shouldBe false
    }

    private fun encode(message: JSONRPCMessage): String = McpJson.encodeToString(JSONRPCMessage.serializer(), message)

    private fun toolsList(id: String): JSONRPCRequest = toolsList(RequestId(id))

    private fun toolsList(id: RequestId): JSONRPCRequest =
        JSONRPCRequest(id = id, method = Method.Defined.ToolsList.value)
}
