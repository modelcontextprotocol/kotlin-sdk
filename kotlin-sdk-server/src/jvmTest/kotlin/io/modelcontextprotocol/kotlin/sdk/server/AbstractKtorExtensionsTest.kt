package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.ktor.client.shouldHaveContentType
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

@Suppress("AbstractClassCanBeConcreteClass")
abstract class AbstractKtorExtensionsTest {

    protected val sseContentType = ContentType("text", "event-stream")

    protected fun testServer() = Server(
        serverInfo = Implementation(name = "test-server", version = "1.0.0"),
        options = ServerOptions(capabilities = ServerCapabilities()),
    )

    /**
     * Asserts that both MCP transport endpoints are registered at [path]:
     * - GET returns 200 with `text/event-stream` content type (SSE endpoint)
     * - POST with a valid MCP payload and session returns 202 Accepted
     * - POST without a sessionId returns 400 Bad Request
     *
     * Use [configureRequest] to add headers (e.g. `basicAuth(...)`) to every request.
     */
    protected suspend fun HttpClient.assertMcpEndpointsAt(
        path: String,
        configureRequest: HttpRequestBuilder.() -> Unit = {},
    ) {
        prepareGet(path) { configureRequest() }.execute { response ->
            response.shouldHaveStatus(HttpStatusCode.OK)
            response.shouldHaveContentType(sseContentType)

            // Extract sessionId from the SSE "endpoint" event
            val channel = response.bodyAsChannel()
            var eventName: String? = null
            var sessionId: String? = null

            while (sessionId == null && !channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.startsWith("event:") -> eventName = line.substringAfter("event:").trim()

                    line.startsWith("data:") && eventName == "endpoint" -> {
                        val data = line.substringAfter("data:").trim()
                        sessionId = data.substringAfter("sessionId=").ifEmpty { null }
                    }
                }
            }

            requireNotNull(sessionId) { "sessionId not found in SSE endpoint event" }

            // POST a valid JSON-RPC ping while the SSE connection is alive
            val postResponse = post("$path?sessionId=$sessionId") {
                contentType(ContentType.Application.Json)
                setBody("""{"jsonrpc":"2.0","id":1,"method":"ping"}""")
                configureRequest()
            }
            postResponse.shouldHaveStatus(HttpStatusCode.Accepted)
        }

        // POST without sessionId returns 400 Bad Request
        post(path) { configureRequest() }.shouldHaveStatus(HttpStatusCode.BadRequest)
    }
}
