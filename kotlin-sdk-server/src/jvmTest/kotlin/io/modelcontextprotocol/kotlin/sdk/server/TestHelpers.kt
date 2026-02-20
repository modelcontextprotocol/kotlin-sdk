package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.ktor.client.shouldHaveContentType
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
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
import io.ktor.utils.io.readUTF8Line
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

internal fun testServer() = Server(
    serverInfo = Implementation(name = "test-server", version = "1.0.0"),
    options = ServerOptions(capabilities = ServerCapabilities()),
)

/**
 * Asserts that stateless Streamable HTTP MCP endpoints are registered at [path]:
 * - GET returns 405 Method Not Allowed (explicitly rejected by the stateless routing layer)
 * - DELETE returns 405 Method Not Allowed (same)
 * - POST is routed to the transport (returns 406 for a deliberately wrong Accept, confirming the route exists)
 *
 * Use [configureRequest] to add headers (e.g. `basicAuth(...)`) to every request.
 */
internal suspend fun HttpClient.assertStatelessStreamableHttpEndpointsAt(
    path: String,
    configureRequest: HttpRequestBuilder.() -> Unit = {},
) {
    get(path) { configureRequest() }.shouldHaveStatus(HttpStatusCode.MethodNotAllowed)
    delete(path) { configureRequest() }.shouldHaveStatus(HttpStatusCode.MethodNotAllowed)

    post(path) {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Accept, ContentType.Text.Plain.toString())
        configureRequest()
    }.shouldHaveStatus(HttpStatusCode.NotAcceptable)
}

/**
 * Asserts that stateful Streamable HTTP MCP endpoints are registered at [path]:
 * - GET opens an SSE connection (200 OK); session validation inside the SSE body cannot change
 *   the already-committed status, so the connection closes immediately without a session
 * - DELETE without a session ID returns 400 Bad Request
 * - POST is routed to the transport (returns 406 for a deliberately wrong Accept, confirming the route exists)
 *
 * Use [configureRequest] to add headers (e.g. `basicAuth(...)`) to every request.
 */
internal suspend fun HttpClient.assertStreamableHttpEndpointsAt(
    path: String,
    configureRequest: HttpRequestBuilder.() -> Unit = {},
) {
    // GET starts an SSE handshake — 200 is committed before the body runs
    get(path) { configureRequest() }.shouldHaveStatus(HttpStatusCode.OK)

    // DELETE without session ID is rejected by the route handler
    delete(path) { configureRequest() }.shouldHaveStatus(HttpStatusCode.BadRequest)

    // POST reaches the transport: a wrong Accept header triggers 406, not 404
    post(path) {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Accept, ContentType.Text.Plain.toString())
        configureRequest()
    }.shouldHaveStatus(HttpStatusCode.NotAcceptable)
}

/**
 * Asserts that both MCP transport endpoints are registered at [path]:
 * - GET returns 200 with `text/event-stream` content type (SSE endpoint)
 * - POST with a valid MCP payload and session returns 202 Accepted
 * - POST without a sessionId returns 400 Bad Request
 *
 * Use [configureRequest] to add headers (e.g. `basicAuth(...)`) to every request.
 */
internal suspend fun HttpClient.assertMcpEndpointsAt(
    path: String,
    configureRequest: HttpRequestBuilder.() -> Unit = {},
) {
    prepareGet(path) { configureRequest() }.execute { response ->
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.shouldHaveContentType(ContentType("text", "event-stream"))

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

        val resolvedSessionId = withClue("sessionId not found in SSE endpoint event") {
            sessionId.shouldNotBeNull()
        }

        // POST a valid JSON-RPC ping while the SSE connection is alive
        val postResponse = post("$path?sessionId=$resolvedSessionId") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","id":1,"method":"ping"}""")
            configureRequest()
        }
        postResponse.shouldHaveStatus(HttpStatusCode.Accepted)
    }

    // POST without sessionId returns 400 Bad Request
    post(path) { configureRequest() }.shouldHaveStatus(HttpStatusCode.BadRequest)
}
