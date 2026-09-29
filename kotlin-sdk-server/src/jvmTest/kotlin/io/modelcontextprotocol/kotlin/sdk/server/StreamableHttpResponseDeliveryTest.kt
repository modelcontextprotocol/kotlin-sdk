package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test

/**
 * A client opening the standalone stream sends `Accept: text/event-stream`, which matches no JSON
 * converter. Responses rendered through ContentNegotiation used to reach such a client as an empty
 * 406 with the intended status discarded.
 */
class StreamableHttpResponseDeliveryTest {

    private val eventStream = ContentType.Text.EventStream.toString()

    @ParameterizedTest
    @ValueSource(strings = ["GET", "DELETE"])
    fun `stateless endpoint rejects the method with 405 and advertises POST`(method: String) = testApplication {
        application { mcpStatelessStreamableHttp { testServer() } }

        val response = client.request("/mcp") {
            this.method = HttpMethod.parse(method)
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, eventStream)
        }

        response.status shouldBe HttpStatusCode.MethodNotAllowed
        response.headers[HttpHeaders.Allow] shouldBe HttpMethod.Post.value
        response.contentType()?.withoutParameters() shouldBe ContentType.Application.Json
        response.decodeError().error.code shouldBe RPCError.ErrorCode.CONNECTION_CLOSED
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
    fun `stateful GET for an unknown session returns 404`() = testApplication {
        application { mcpStreamableHttp { testServer() } }

        val response = client.get("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, eventStream)
            header(MCP_SESSION_ID_HEADER, "unknown-session")
        }

        response.status shouldBe HttpStatusCode.NotFound
        val error = response.decodeError().error
        error.message shouldBe "Session not found"
        error.code shouldBe -32001
    }

    @Test
    fun `stateful GET without a session id returns 400`() = testApplication {
        application { mcpStreamableHttp { testServer() } }

        val response = client.get("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, eventStream)
        }

        response.status shouldBe HttpStatusCode.BadRequest
    }

    private suspend fun HttpResponse.decodeError(): JSONRPCError = McpJson.decodeFromString(bodyAsText())
}
