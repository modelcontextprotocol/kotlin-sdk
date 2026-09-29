package io.modelcontextprotocol.kotlin.sdk.client.sse

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldHaveSize
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
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class SseClientTransportTest {

    @Test
    fun `absolute path endpoint resolves against origin`() = runTest {
        // Given
        val sseUrl = "http://example.com/api/mcp/sse"

        // And
        val endpointEvent = "/messages?sessionId=abc"

        // And
        val engine = CapturingSseClientEngine(endpoint = endpointEvent)
        val transport = sseTransport(sseUrl, engine)

        // When
        transport.start()
        transport.send(JSONRPCNotification(method = "test"))

        // Then
        val capturedPosts = engine.capturedPosts
        capturedPosts shouldHaveSize 1
        capturedPosts[0].url.toString() shouldBe "http://example.com/messages?sessionId=abc"

        // Cleanup
        transport.close()
        engine.close()
    }

    @Test
    fun `relative path endpoint resolves against baseUrl`() = runTest {
        // Given
        val sseUrl = "http://example.com/api/mcp/sse"

        // And
        val endpointEvent = "post?sessionId=xyz"

        // And
        val engine = CapturingSseClientEngine(endpoint = endpointEvent)
        val transport = sseTransport(sseUrl, engine)

        // When
        transport.start()
        transport.send(JSONRPCNotification(method = "test"))

        // Then
        val capturedPosts = engine.capturedPosts
        capturedPosts shouldHaveSize 1
        capturedPosts[0].url.toString() shouldBe "http://example.com/api/mcp/post?sessionId=xyz"

        // Cleanup
        transport.close()
        engine.close()
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
            sseUrl = "http://example.com/api/mcp/sse",
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
        val post = sendThroughEndpoint(
            sseUrl = "http://example.com/api/mcp/sse",
            endpointEvent = "http://example.com:80/messages?sessionId=abc",
        )

        post.url.host shouldBe "example.com"
        post.url.port shouldBe 80
    }

    @Test
    fun `full url endpoint host is compared case-insensitively`() = runTest {
        val post = sendThroughEndpoint(
            sseUrl = "http://example.com/api/mcp/sse",
            endpointEvent = "http://EXAMPLE.com/messages?sessionId=abc",
        )

        post.url.toString() shouldBe "http://EXAMPLE.com/messages?sessionId=abc"
    }

    @Test
    fun `sse request redirected to a different origin is rejected`() = runTest {
        // Given
        val sseUrl = "http://example.com/api/mcp/sse"

        // And
        val engine = CapturingSseClientEngine(
            endpoint = "/messages?sessionId=abc",
            sseRedirectLocation = "http://evil.example.com/sse",
        )
        val transport = sseTransport(sseUrl, engine)

        // When
        val exception = assertFailsWith<IllegalStateException> {
            transport.start()
        }

        // Then
        exception.message shouldBe
            "SSE request to http://example.com was redirected to a different origin http://evil.example.com"
        engine.capturedPosts shouldHaveSize 0

        // Cleanup
        transport.close()
        engine.close()
    }

    @Test
    fun `sse request redirected within the same origin is accepted`() = runTest {
        // Given
        val sseUrl = "http://example.com/api/mcp/sse"

        // And
        val engine = CapturingSseClientEngine(
            endpoint = "/messages?sessionId=abc",
            sseRedirectLocation = "http://example.com/v2/sse",
        )
        val transport = sseTransport(sseUrl, engine)

        // When
        transport.start()
        transport.send(JSONRPCNotification(method = "test"))

        // Then
        val capturedPosts = engine.capturedPosts
        capturedPosts shouldHaveSize 1
        capturedPosts[0].url.toString() shouldBe "http://example.com/messages?sessionId=abc"

        // Cleanup
        transport.close()
        engine.close()
    }

    @Test
    fun `onClose callback fires when the SSE stream is disconnected by the server`() = runTest {
        // Given
        val sseUrl = "http://example.com/api/mcp/sse"

        // And
        val engine = CapturingSseClientEngine(endpoint = "/messages?sessionId=abc")
        val transport = sseTransport(sseUrl, engine)
        var onCloseFired = false
        transport.onClose { onCloseFired = true }

        // When
        transport.start()
        engine.disconnectSseStream()

        // Then
        eventually(2.seconds) {
            onCloseFired shouldBe true
        }

        // Cleanup
        transport.close()
        engine.close()
    }

    private fun sseTransport(sseUrl: String, engine: CapturingSseClientEngine) =
        SseClientTransport(HttpClient(engine) { install(SSE) }, sseUrl)

    private suspend fun startWithRejectedEndpoint(sseUrl: String, endpointEvent: String): IllegalArgumentException {
        val engine = CapturingSseClientEngine(endpoint = endpointEvent)
        val transport = sseTransport(sseUrl, engine)
        val errors = mutableListOf<Throwable>()
        transport.onError { errors += it }
        try {
            val exception = assertFailsWith<IllegalArgumentException> { transport.start() }
            errors.map { it.message } shouldBe listOf(exception.message)
            engine.capturedPosts shouldHaveSize 0
            return exception
        } finally {
            transport.close()
            engine.close()
        }
    }

    private suspend fun sendThroughEndpoint(sseUrl: String, endpointEvent: String): HttpRequestData {
        val engine = CapturingSseClientEngine(endpoint = endpointEvent)
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
