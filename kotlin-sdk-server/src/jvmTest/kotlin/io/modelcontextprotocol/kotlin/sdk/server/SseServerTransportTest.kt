package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.testing.testApplication
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test

class SseServerTransportTest {

    @Test
    fun `handlePostMessage on a not-started transport does not deliver the message`() = testApplication {
        // A registered onMessage callback opens the delivery gate, so if the not-initialized branch
        // falls through it would wrongly hand the message to the application. It must return instead.
        val delivered = AtomicBoolean(false)
        application {
            routing {
                post("/messages") {
                    val transport = SseServerTransport("/messages", FakeServerSSESession(call, call.coroutineContext))
                    transport.onMessage { delivered.set(true) }
                    transport.handlePostMessage(call)
                }
            }
        }

        val response = client.post("/messages") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","id":1,"method":"ping"}""")
        }

        response.status shouldBe HttpStatusCode.InternalServerError
        delivered.get() shouldBe false
    }

    @Test
    fun `SSE POST exceeding maxRequestBodySize is rejected with 413`() = testApplication {
        application {
            install(SSE)
            routing {
                mcp(enableDnsRebindingProtection = false, maxRequestBodySize = MAX_BODY) { testServer() }
            }
        }

        client.prepareGet("/").execute { response ->
            val sessionId = response.readSseSessionId().shouldNotBeNull()

            // A body one byte over the configured limit must be rejected before processing.
            val postResponse = client.post("/?sessionId=$sessionId") {
                contentType(ContentType.Application.Json)
                setBody("x".repeat((MAX_BODY + 1).toInt()))
            }
            postResponse.status shouldBe HttpStatusCode.PayloadTooLarge
        }
    }

    private companion object {
        const val MAX_BODY = 1024L
    }
}
