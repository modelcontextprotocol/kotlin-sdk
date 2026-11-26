package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
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

    @Test
    fun `stateful GET whose Accept header excludes event-stream returns 406`() = testApplication {
        application { mcpStreamableHttp { testServer() } }
        val sessionId = client.initialize()

        val response = client.get("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(MCP_SESSION_ID_HEADER, sessionId)
        }

        response.status shouldBe HttpStatusCode.NotAcceptable
        response.contentType()?.withoutParameters() shouldBe ContentType.Application.Json
        val error = response.decodeError().error
        error.message shouldBe "Not Acceptable: Client must accept text/event-stream"
        error.code shouldBe RPCError.ErrorCode.CONNECTION_CLOSED
    }

    @Test
    fun `stateful GET with an unsupported protocol version returns 400`() = testApplication {
        application { mcpStreamableHttp { testServer() } }
        val sessionId = client.initialize()

        val response = client.get("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, eventStream)
            header(MCP_SESSION_ID_HEADER, sessionId)
            header("mcp-protocol-version", "1900-01-01")
        }

        response.status shouldBe HttpStatusCode.BadRequest
        response.contentType()?.withoutParameters() shouldBe ContentType.Application.Json
        val error = response.decodeError().error
        error.message shouldStartWith "Bad Request: Unsupported protocol version"
        error.code shouldBe RPCError.ErrorCode.CONNECTION_CLOSED
    }

    @Test
    fun `GET routes next to the stateful endpoint are not checked for a session`() = testApplication {
        application {
            mcpStreamableHttp { testServer() }
            routing { get("/health") { call.respondText("ok") } }
        }

        val response = client.get("/health")

        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe "ok"
    }

    private suspend fun HttpClient.initialize(): String {
        val response = post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            streamableHeaders()
            setBody(McpJson.encodeToString(JSONRPCMessage.serializer(), initializeRequest()))
        }
        response.status shouldBe HttpStatusCode.OK
        return response.headers[MCP_SESSION_ID_HEADER].shouldNotBeNull()
    }

    private suspend fun HttpResponse.decodeError(): JSONRPCError = McpJson.decodeFromString(bodyAsText())
}
