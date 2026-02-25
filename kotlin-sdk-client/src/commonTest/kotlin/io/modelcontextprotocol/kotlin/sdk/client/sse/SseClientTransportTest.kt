package io.modelcontextprotocol.kotlin.sdk.client.sse

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestData
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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
