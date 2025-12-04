package io.modelcontextprotocol.kotlin.sdk.client.streamable.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.modelcontextprotocol.kotlin.sdk.client.AbstractClientTransportLifecycleTest
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class StreamingHttpClientTransportLifecycleTest :
    AbstractClientTransportLifecycleTest<StreamableHttpClientTransport>() {

    /**
     * Dummy method to make IDE treat this class as a test
     */
    @Test
    @Ignore
    fun dummyTest() = Unit

    override fun createTransport(): StreamableHttpClientTransport {
        val mockEngine = MockEngine {
            respond(
                "this is not valid json",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(SSE) {
                reconnectionTime = 1.seconds
            }
        }

        return StreamableHttpClientTransport(httpClient, url = "http://localhost:8080/mcp")
    }
}
