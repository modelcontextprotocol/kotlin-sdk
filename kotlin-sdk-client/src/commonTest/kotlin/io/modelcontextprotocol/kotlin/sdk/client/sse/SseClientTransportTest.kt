package io.modelcontextprotocol.kotlin.sdk.client.sse

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestData
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
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
    fun `full url endpoint with a different origin is rejected`() = runTest {
        // Given
        val sseUrl = "http://example.com/api/mcp/sse"

        // And
        val endpointEvent = "https://evil.example.com/messages?sessionId=abc"

        // And
        val engine = CapturingSseClientEngine(endpoint = endpointEvent)
        val transport = sseTransport(sseUrl, engine)

        // When
        val exception = assertFailsWith<IllegalArgumentException> {
            transport.start()
        }

        // Then
        exception.message shouldBe "Endpoint origin https://evil.example.com does not match connection origin http://example.com"
        engine.capturedPosts shouldHaveSize 0

        // Cleanup
        transport.close()
        engine.close()
    }

    @Test
    fun `full url endpoint with the same origin is used as-is`() = runTest {
        // Given
        val sseUrl = "http://example.com/api/mcp/sse"

        // And
        val endpointEvent = "http://example.com/messages?sessionId=abc"

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

    private class CapturingSseClientEngine private constructor(
        endpoint: String,
        private val capturedPostRequests: MutableList<HttpRequestData> = mutableListOf(),
    ) : MockSseClientEngine(endpoint, capturedPostRequests::add) {

        constructor(endpoint: String) : this(endpoint, mutableListOf())

        val capturedPosts: List<HttpRequestData>
            get() = capturedPostRequests
    }
}
