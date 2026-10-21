package io.modelcontextprotocol.kotlin.sdk.client.sse

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.mcpSse
import io.modelcontextprotocol.kotlin.sdk.client.mcpSseTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SSE_URL = "http://example.com/api/mcp/sse"

class SseClientTransportTest {

    @Test
    fun `absolute path endpoint resolves against origin`() = runTest {
        val post = sendThroughEndpoint(sseUrl = SSE_URL, endpointEvent = "/messages?sessionId=abc")

        post.url.toString() shouldBe "http://example.com/messages?sessionId=abc"
    }

    @Test
    fun `relative path endpoint resolves against the sse directory`() = runTest {
        val post = sendThroughEndpoint(sseUrl = SSE_URL, endpointEvent = "post?sessionId=xyz")

        post.url.toString() shouldBe "http://example.com/api/mcp/post?sessionId=xyz"
    }

    @Test
    fun `query-only endpoint keeps the sse path`() = runTest {
        val post = sendThroughEndpoint(sseUrl = SSE_URL, endpointEvent = "?sessionId=abc")

        post.url.toString() shouldBe "http://example.com/api/mcp/sse?sessionId=abc"
    }

    @Test
    fun `query-only endpoint replaces the sse query`() = runTest {
        val post = sendThroughEndpoint(sseUrl = "$SSE_URL?token=t", endpointEvent = "?sessionId=abc")

        post.url.toString() shouldBe "http://example.com/api/mcp/sse?sessionId=abc"
    }

    @Test
    fun `relative path endpoint drops the sse query`() = runTest {
        val post = sendThroughEndpoint(sseUrl = "$SSE_URL?token=t", endpointEvent = "post?sessionId=xyz")

        post.url.toString() shouldBe "http://example.com/api/mcp/post?sessionId=xyz"
    }

    @Test
    fun `relative path endpoint resolves against a trailing-slash sse url`() = runTest {
        val post = sendThroughEndpoint(sseUrl = "http://example.com/api/mcp/", endpointEvent = "post?sessionId=xyz")

        post.url.toString() shouldBe "http://example.com/api/mcp/post?sessionId=xyz"
    }

    @Test
    fun `full url endpoint without a host is rejected`() = runTest {
        // Ktor fills an empty host with localhost, which would match this SSE origin.
        val exception = startWithRejectedEndpoint(
            sseUrl = "http://localhost/api/mcp/sse",
            endpointEvent = "http://@/messages?sessionId=abc",
        )

        exception.message shouldBe "Endpoint is not a valid URL"
    }

    @Test
    fun `full url endpoint with a different host is rejected without exposing credentials`() = runTest {
        val exception = startWithRejectedEndpoint(
            sseUrl = "http://user:secret@example.com/api/mcp/sse",
            endpointEvent = "http://evil.example.com/messages?sessionId=abc",
        )

        exception.message shouldBe
            "Endpoint origin http://evil.example.com does not match connection origin http://example.com"
    }

    @Test
    fun `full url endpoint with a different port is rejected`() = runTest {
        val exception = startWithRejectedEndpoint(
            sseUrl = SSE_URL,
            endpointEvent = "http://example.com:8080/messages?sessionId=abc",
        )

        exception.message shouldBe
            "Endpoint origin http://example.com:8080 does not match connection origin http://example.com"
    }

    @Test
    fun `full url endpoint with a different scheme is rejected`() = runTest {
        // Same explicit port on both sides, so only the scheme differs
        val exception = startWithRejectedEndpoint(
            sseUrl = "http://example.com:8080/api/mcp/sse",
            endpointEvent = "https://example.com:8080/messages?sessionId=abc",
        )

        exception.message shouldBe
            "Endpoint origin https://example.com:8080 does not match connection origin http://example.com:8080"
    }

    @Test
    fun `full url endpoint with the same origin is used as-is regardless of connection credentials`() = runTest {
        val post = sendThroughEndpoint(
            sseUrl = "http://user:secret@example.com/api/mcp/sse",
            endpointEvent = "http://example.com/messages?sessionId=abc",
        )

        post.url.toString() shouldBe "http://example.com/messages?sessionId=abc"
    }

    @Test
    fun `full url endpoint with an explicit default port is accepted`() = runTest {
        val post = sendThroughEndpoint(sseUrl = SSE_URL, endpointEvent = "http://example.com:80/messages?sessionId=abc")

        post.url.host shouldBe "example.com"
        post.url.port shouldBe 80
    }

    @Test
    fun `full url endpoint host is compared case-insensitively`() = runTest {
        val post = sendThroughEndpoint(sseUrl = SSE_URL, endpointEvent = "http://EXAMPLE.com/messages?sessionId=abc")

        post.url.toString() shouldBe "http://EXAMPLE.com/messages?sessionId=abc"
    }

    @Test
    fun `sse request redirected to a different origin is rejected`() = runTest {
        val engine = CapturingSseClientEngine(
            endpoint = "/messages?sessionId=abc",
            sseRedirectLocation = "http://evil.example.com/sse",
        )

        val exception = shouldThrow<IllegalStateException> { sseTransport(SSE_URL, engine).start() }

        exception.message shouldBe
            "SSE request to http://example.com was redirected to a different origin http://evil.example.com"
        engine.capturedPosts.shouldBeEmpty()
        engine.close()
    }

    @Test
    fun `sse request redirected within the same origin is accepted`() = runTest {
        val post = sendThroughEndpoint(
            sseUrl = SSE_URL,
            endpointEvent = "/messages?sessionId=abc",
            sseRedirectLocation = "http://example.com/v2/sse",
        )

        post.url.toString() shouldBe "http://example.com/messages?sessionId=abc"
    }

    @Test
    fun `relative sse url resolved by the default request is not treated as a redirect`() = runTest {
        val post = sendThroughEndpoint(
            sseUrl = "sse",
            endpointEvent = "/messages?sessionId=abc",
            clientConfig = { defaultRequest { url("http://example.com/api/mcp/") } },
        )

        post.url.toString() shouldBe "http://example.com/messages?sessionId=abc"
    }

    @Test
    fun `onClose callback fires when the SSE stream is disconnected by the server`() = runTest {
        val engine = CapturingSseClientEngine(endpoint = "/messages?sessionId=abc")
        val transport = sseTransport(SSE_URL, engine)
        val closed = CompletableDeferred<Unit>()
        transport.onClose { closed.complete(Unit) }

        transport.start()
        engine.disconnectSseStream()

        closed.await()
        transport.close()
        engine.close()
    }

    private fun sseTransport(
        sseUrl: String,
        engine: CapturingSseClientEngine,
        clientConfig: HttpClientConfig<*>.() -> Unit = {},
    ) = SseClientTransport(
        HttpClient(engine) {
            install(SSE)
            clientConfig()
        },
        sseUrl,
    )

    @Test
    fun `connect fails when the server never sends an endpoint event`() = runTest {
        val sseUrl = "http://example.com/api/mcp/sse"
        val timeout = 100.milliseconds
        val engine = MockSseClientEngine(endpoint = null, onPostRequest = {})
        val transport = SseClientTransport(
            client = HttpClient(engine) { install(SSE) },
            urlString = sseUrl,
            endpointDiscoveryTimeout = timeout,
        )
        val client = Client(Implementation("test-client", "1.0.0"))
        val errors = mutableListOf<Throwable>()
        var onCloseFired = false
        transport.onError { errors += it }
        transport.onClose { onCloseFired = true }

        try {
            val exception = assertFailsWith<IllegalStateException> { client.connect(transport) }

            exception.message shouldBe "Timed out waiting for the SSE endpoint event after $timeout"
            errors shouldHaveSize 1
            errors.single() shouldBe exception
            eventually(2.seconds) {
                onCloseFired shouldBe true
            }
        } finally {
            client.close()
            engine.close()
        }
    }

    private fun sseTransport(sseUrl: String, engine: MockSseClientEngine) =
        SseClientTransport(HttpClient(engine) { install(SSE) }, sseUrl)
    @Test
    fun `connect uses a real timeout clock while waiting for the SSE handshake`() = runTest {
        val timeout = 2.seconds
        val engine = object : MockSseClientEngine(endpoint = "/messages?sessionId=abc", onPostRequest = {}) {
            override suspend fun execute(data: HttpRequestData): HttpResponseData {
                if (data.method == HttpMethod.Get) delay(100)
                return super.execute(data)
            }
        }
        val client = HttpClient(engine) { install(SSE) }
        val transport = SseClientTransport(
            client = client,
            urlString = SSE_URL,
            endpointDiscoveryTimeout = timeout,
        )

        try {
            transport.start()
        } finally {
            transport.close()
            client.close()
            engine.close()
        }
    }

    @Test
    fun `connect times out while the SSE handshake is pending`() = runTest {
        val timeout = 100.milliseconds
        val engine = object : MockSseClientEngine(endpoint = null, onPostRequest = {}) {
            override suspend fun execute(data: HttpRequestData): HttpResponseData {
                if (data.method == HttpMethod.Get) awaitCancellation()
                return super.execute(data)
            }
        }
        val client = HttpClient(engine) { install(SSE) }
        val transport = SseClientTransport(
            client = client,
            urlString = SSE_URL,
            endpointDiscoveryTimeout = timeout,
        )
        val errors = mutableListOf<Throwable>()
        transport.onError { errors += it }

        try {
            val exception = assertFailsWith<IllegalStateException> { transport.start() }

            exception.message shouldBe "Timed out waiting for the SSE endpoint event after $timeout"
            errors shouldHaveSize 1
            errors.single() shouldBe exception
        } finally {
            client.close()
            engine.close()
        }
    }

    @Test
    fun `custom endpoint timeout preserves trailing request builder lambda`() = runTest {
        val engine = CapturingSseClientEngine(endpoint = "/messages?sessionId=abc")
        val client = HttpClient(engine) { install(SSE) }
        val transport = client.mcpSseTransport(urlString = SSE_URL, endpointDiscoveryTimeout = 2.seconds) {
            headers.append(HttpHeaders.Authorization, "Bearer token")
        }

        try {
            transport.start()
            transport.send(JSONRPCNotification(method = "test"))

            engine.capturedPosts.single().headers[HttpHeaders.Authorization] shouldBe "Bearer token"
        } finally {
            transport.close()
            client.close()
            engine.close()
        }

        val convenienceEngine = CapturingSseClientEngine(
            endpoint = "/messages?sessionId=xyz",
            respondToInitialize = true,
        )
        val convenienceHttpClient = HttpClient(convenienceEngine) { install(SSE) }
        val mcpClient = withContext(Dispatchers.Default) {
            convenienceHttpClient.mcpSse(
                urlString = SSE_URL,
                endpointDiscoveryTimeout = 2.seconds,
            ) {
                headers.append(HttpHeaders.Authorization, "Bearer token")
            }
        }

        try {
            mcpClient.close()
            convenienceEngine.capturedPosts.shouldHaveSize(2)
            convenienceEngine.capturedPosts.forEach {
                it.headers[HttpHeaders.Authorization] shouldBe "Bearer token"
            }
        } finally {
            convenienceHttpClient.close()
            convenienceEngine.close()
        }
    }

    private fun sseTransport(
        sseUrl: String,
        engine: CapturingSseClientEngine,
        clientConfig: HttpClientConfig<*>.() -> Unit = {},
    ) = SseClientTransport(
        HttpClient(engine) {
            install(SSE)
            clientConfig()
        },
        sseUrl,
    )

    private suspend fun startWithRejectedEndpoint(sseUrl: String, endpointEvent: String): IllegalArgumentException {
        val engine = CapturingSseClientEngine(endpoint = endpointEvent)
        val transport = sseTransport(sseUrl, engine)
        val errors = mutableListOf<Throwable>()
        transport.onError { errors += it }
        try {
            val exception = shouldThrow<IllegalArgumentException> { transport.start() }
            errors.map { it.message } shouldBe listOf(exception.message)
            engine.capturedPosts.shouldBeEmpty()
            return exception
        } finally {
            transport.close()
            engine.close()
        }
    }

    private suspend fun sendThroughEndpoint(
        sseUrl: String,
        endpointEvent: String,
        sseRedirectLocation: String? = null,
        clientConfig: HttpClientConfig<*>.() -> Unit = {},
    ): HttpRequestData {
        val engine = CapturingSseClientEngine(endpointEvent, sseRedirectLocation)
        val transport = sseTransport(sseUrl, engine, clientConfig)
        try {
            transport.start()
            transport.send(JSONRPCNotification(method = "test"))
            return engine.capturedPosts.single()
        } finally {
            transport.close()
            engine.close()
        }
    }

    private class CapturingSseClientEngine private constructor(
        endpoint: String,
        private val sseRedirectLocation: String?,
        private val capturedPostRequests: MutableList<HttpRequestData>,
        private val respondToInitialize: Boolean,
    ) : MockSseClientEngine(endpoint, capturedPostRequests::add) {

        constructor(endpoint: String, sseRedirectLocation: String? = null, respondToInitialize: Boolean = false) :
            this(endpoint, sseRedirectLocation, mutableListOf(), respondToInitialize)

        private var redirected = false

        val capturedPosts: List<HttpRequestData>
            get() = capturedPostRequests

        /** Answers the first SSE request with a redirect to [sseRedirectLocation], if it is set. */
        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            val location = sseRedirectLocation
            if (location != null && data.method == HttpMethod.Get && !redirected) {
                redirected = true
                return HttpResponseData(
                    statusCode = HttpStatusCode.Found,
                    requestTime = GMTDate(),
                    headers = headersOf(HttpHeaders.Location, location),
                    version = HttpProtocolVersion.HTTP_1_1,
                    body = ByteReadChannel.Empty,
                    callContext = dispatcher + Job(),
                )
            }

            if (respondToInitialize && data.method == HttpMethod.Post) {
                val request = McpJson.decodeFromString<JSONRPCMessage>((data.body as TextContent).text)
                if (request is JSONRPCRequest && request.method == "initialize") {
                    sendSseMessage(
                        buildJsonObject {
                            put("jsonrpc", "2.0")
                            put("id", McpJson.encodeToJsonElement(request.id))
                            put(
                                "result",
                                McpJson.encodeToJsonElement(
                                    InitializeResult(
                                        protocolVersion = LATEST_PROTOCOL_VERSION,
                                        capabilities = ServerCapabilities(),
                                        serverInfo = Implementation("mock-server", "1.0.0"),
                                    ),
                                ),
                            )
                        }.toString(),
                    )
                }
            }

            return super.execute(data)
        }
    }
}
