package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Verifies that the stateless Streamable HTTP endpoint does not leak server sessions:
 * every session created to serve a single request must be closed and removed from the
 * session registry once that request completes (https://github.com/modelcontextprotocol/kotlin-sdk/issues/786).
 */
class StatelessStreamableHttpSessionLifecycleTest {

    private fun testServer(): Server = Server(
        Implementation("test-server", "1.0"),
        ServerOptions(capabilities = ServerCapabilities()),
    )

    @Test
    fun `stateless endpoint removes session after each request`() = testApplication {
        val server = testServer()
        application {
            mcpStatelessStreamableHttp { server }
        }

        repeat(5) {
            val response = client.post("/mcp") {
                addStreamableHeaders()
                setBody(initializeRequestBody())
            }
            response.status shouldBe HttpStatusCode.OK
        }

        eventually(5.seconds) {
            server.sessions.shouldBeEmpty()
        }
    }

    @Test
    fun `stateless endpoint removes session when the request is rejected`() = testApplication {
        val server = testServer()
        application {
            mcpStatelessStreamableHttp { server }
        }

        // Missing "text/event-stream" in Accept makes the transport reject the request.
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            contentType(ContentType.Application.Json)
            setBody(initializeRequestBody())
        }
        response.status shouldBe HttpStatusCode.NotAcceptable

        eventually(5.seconds) {
            server.sessions.shouldBeEmpty()
        }
    }

    private fun HttpRequestBuilder.addStreamableHeaders() {
        header(HttpHeaders.Host, "localhost")
        header(
            HttpHeaders.Accept,
            listOf(ContentType.Application.Json, ContentType.Text.EventStream).joinToString(", "),
        )
        contentType(ContentType.Application.Json)
    }

    private fun initializeRequestBody(): String {
        val request = InitializeRequest(
            InitializeRequestParams(
                protocolVersion = LATEST_PROTOCOL_VERSION,
                capabilities = ClientCapabilities(),
                clientInfo = Implementation(name = "test-client", version = "1.0.0"),
            ),
        ).toJSON()

        return McpJson.encodeToString(JSONRPCMessage.serializer(), request)
    }
}
