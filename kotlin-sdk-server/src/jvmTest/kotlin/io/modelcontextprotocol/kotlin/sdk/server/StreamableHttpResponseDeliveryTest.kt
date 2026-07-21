package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlin.test.Test

/**
 * A client opening the standalone stream sends `Accept: text/event-stream`, which matches no JSON
 * converter. Responses rendered through ContentNegotiation used to reach such a client as an empty
 * 406 with the intended status discarded.
 */
class StreamableHttpResponseDeliveryTest {

    private val eventStream = ContentType.Text.EventStream.toString()

    private fun testServer(): Server = Server(
        Implementation("test-server", "1.0"),
        ServerOptions(capabilities = ServerCapabilities()),
    )

    @Test
    fun `stateless GET is rejected with 405 and advertises POST`() = testApplication {
        application { mcpStatelessStreamableHttp { testServer() } }

        val response = client.get("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, eventStream)
        }

        response.assertMethodNotAllowed()
    }

    @Test
    fun `stateless DELETE is rejected with 405 and advertises POST`() = testApplication {
        application { mcpStatelessStreamableHttp { testServer() } }

        val response = client.delete("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, eventStream)
        }

        response.assertMethodNotAllowed()
    }

    @Test
    fun `stateful DELETE for an unknown session returns 404`() = testApplication {
        application { mcpStreamableHttp { testServer() } }

        val response = client.delete("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, eventStream)
            header(MCP_SESSION_ID_HEADER, "unknown-session")
        }

        response.status shouldBe HttpStatusCode.NotFound
        response.decodeError().error.message shouldBe "Session not found"
    }

    @Test
    fun `POST carrying no request is acknowledged with a bodiless 202`() = testApplication {
        application { mcpStatelessStreamableHttp { testServer() } }

        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, "${ContentType.Application.Json}, $eventStream")
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        }

        response.status shouldBe HttpStatusCode.Accepted
        response.bodyAsText() shouldBe ""
    }

    private suspend fun HttpResponse.assertMethodNotAllowed() {
        status shouldBe HttpStatusCode.MethodNotAllowed
        headers[HttpHeaders.Allow] shouldBe HttpMethod.Post.value
        contentType()?.withoutParameters() shouldBe ContentType.Application.Json
        decodeError().error.code shouldBe RPCError.ErrorCode.CONNECTION_CLOSED
    }

    private suspend fun HttpResponse.decodeError(): JSONRPCError = McpJson.decodeFromString(bodyAsText())
}
