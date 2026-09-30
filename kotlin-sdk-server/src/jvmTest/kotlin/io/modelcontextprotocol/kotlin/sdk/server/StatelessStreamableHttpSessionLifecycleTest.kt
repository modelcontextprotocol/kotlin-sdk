package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/** A stateless session serves exactly one request (https://github.com/modelcontextprotocol/kotlin-sdk/issues/786). */
class StatelessStreamableHttpSessionLifecycleTest {

    @Test
    fun `stateless endpoint removes the session of every served or rejected request`() = testApplication {
        val server = testServer()
        application {
            mcpStatelessStreamableHttp { server }
        }
        val body = McpJson.encodeToString(JSONRPCMessage.serializer(), initializeRequest())

        client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            streamableHeaders()
            setBody(body)
        }.status shouldBe HttpStatusCode.OK

        // Missing "text/event-stream" in Accept makes the transport reject the request.
        client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            contentType(ContentType.Application.Json)
            setBody(body)
        }.status shouldBe HttpStatusCode.NotAcceptable

        eventually(5.seconds) {
            server.sessions.shouldBeEmpty()
        }
    }
}
