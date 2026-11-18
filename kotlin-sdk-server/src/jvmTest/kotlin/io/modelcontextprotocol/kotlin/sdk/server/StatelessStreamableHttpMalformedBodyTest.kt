// ABOUTME: Checks how the stateless Streamable HTTP endpoint answers a POST whose body is not JSON.
// ABOUTME: A handshake-era sender gets the legacy transport's answer, including its 406/415 precedence.
package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import kotlin.test.Test

/**
 * An unparseable body carries no request-scoped claim, so it is handshake-era traffic unless the
 * version header names a request-scoped revision. Handshake-era traffic is served exactly as a
 * handshake-only endpoint serves it: `Accept` and `Content-Type` are judged before the body is.
 */
class StatelessStreamableHttpMalformedBodyTest {

    @Test
    fun `a malformed body with an unacceptable Accept header answers 406`() = testApplication {
        application { mcpStatelessStreamableHttp { testServer() } }

        val response = postMalformed {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            contentType(ContentType.Application.Json)
        }

        response.status shouldBe HttpStatusCode.NotAcceptable
    }

    @Test
    fun `a malformed body with an unsupported Content-Type answers 415`() = testApplication {
        application { mcpStatelessStreamableHttp { testServer() } }

        val response = postMalformed {
            header(HttpHeaders.Accept, "${ContentType.Application.Json}, ${ContentType.Text.EventStream}")
            contentType(ContentType.Text.Plain)
        }

        response.status shouldBe HttpStatusCode.UnsupportedMediaType
    }

    @Test
    fun `a malformed body with acceptable headers answers a JSON-RPC parse error on 400`() = testApplication {
        application { mcpStatelessStreamableHttp { testServer() } }

        val response = postMalformed { streamableHeaders() }

        response.status shouldBe HttpStatusCode.BadRequest
        McpJson.decodeFromString<JSONRPCError>(response.bodyAsText()).error.code shouldBe
            RPCError.ErrorCode.PARSE_ERROR
    }

    private suspend fun ApplicationTestBuilder.postMalformed(headers: HttpRequestBuilder.() -> Unit): HttpResponse =
        client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            headers()
            setBody("{not json")
        }
}
