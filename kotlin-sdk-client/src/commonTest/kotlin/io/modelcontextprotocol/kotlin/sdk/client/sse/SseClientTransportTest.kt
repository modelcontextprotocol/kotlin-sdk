package io.modelcontextprotocol.kotlin.sdk.client.sse

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlin.test.Test

private const val SSE_URL = "http://example.com/api/mcp/sse"

class SseClientTransportTest {

    @Test
    fun `absolute path endpoint resolves against origin`() = runTest {
        val post = sendThroughEndpoint(sseUrl = SSE_URL, endpointEvent = "/messages?sessionId=abc")

        post.url.toString() shouldBe "http://example.com/messages?sessionId=abc"
    }

    @Test
    fun `relative path endpoint resolves against baseUrl`() = runTest {
        val post = sendThroughEndpoint(sseUrl = SSE_URL, endpointEvent = "post?sessionId=xyz")

        post.url.toString() shouldBe "http://example.com/api/mcp/post?sessionId=xyz"
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

    @Test
    fun `message before endpoint event does not block initialization`() = runTest {
        val messages = listOf(
            JSONRPCNotification(method = "notifications/first"),
            JSONRPCNotification(method = "notifications/second"),
        )
        val engine = MockSseClientEngine(
            endpoint = "/messages",
            onPostRequest = {},
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            messagesBeforeEndpoint = messages.map { McpJson.encodeToString(it) },
        )
        val transport = SseClientTransport(HttpClient(engine) { install(SSE) }, SSE_URL)
        val receivedMessages = mutableListOf<JSONRPCMessage>()
        transport.onMessage {
            transport.send(JSONRPCNotification(method = "notifications/test"))
            receivedMessages += it
        }

        try {
            withTimeout(1_000) { transport.start() }

            receivedMessages shouldBe messages
        } finally {
            transport.close()
            engine.close()
        }
    }

    private fun sseTransport(sseUrl: String, engine: CapturingSseClientEngine) =
        SseClientTransport(HttpClient(engine) { install(SSE) }, sseUrl)

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
    ): HttpRequestData {
        val engine = CapturingSseClientEngine(endpointEvent, sseRedirectLocation)
        val transport = sseTransport(sseUrl, engine)
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
    ) : MockSseClientEngine(endpoint, capturedPostRequests::add) {

        constructor(endpoint: String, sseRedirectLocation: String? = null) :
            this(endpoint, sseRedirectLocation, mutableListOf())

        private var redirected = false

        val capturedPosts: List<HttpRequestData>
            get() = capturedPostRequests

        /** Answers the first SSE request with a redirect to [sseRedirectLocation], if it is set. */
        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            val location = sseRedirectLocation
            if (location == null || data.method != HttpMethod.Get || redirected) return super.execute(data)
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
    }
}
