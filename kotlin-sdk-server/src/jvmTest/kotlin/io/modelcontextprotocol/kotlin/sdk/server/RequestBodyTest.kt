package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test

class RequestBodyTest {

    /** Wires a POST endpoint that echoes the body when it fits, or replies 413 when it is too large. */
    private fun ApplicationTestBuilder.installEchoEndpoint(maxBytes: Long) {
        install(io.ktor.server.sse.SSE)
        application {
            routing {
                post("/echo") {
                    val text = try {
                        call.receiveTextWithLimit(maxBytes)
                    } catch (e: RequestBodyTooLargeException) {
                        call.respondText(e.message ?: "too large", status = HttpStatusCode.PayloadTooLarge)
                        return@post
                    }
                    call.respondText(text)
                }
            }
        }
    }

    @Test
    fun `body under the limit is returned intact`() = testApplication {
        installEchoEndpoint(maxBytes = 1024)
        val payload = "hello world"

        val response = client.post("/echo") { setBody(payload) }

        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe payload
    }

    @Test
    fun `body exactly at the limit is accepted`() = testApplication {
        val payload = "x".repeat(64)
        installEchoEndpoint(maxBytes = payload.length.toLong())

        val response = client.post("/echo") { setBody(payload) }

        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe payload
    }

    @Test
    fun `body exceeding the limit is rejected with 413`() = testApplication {
        val payload = "x".repeat(65)
        installEchoEndpoint(maxBytes = 64)

        val response = client.post("/echo") { setBody(payload) }

        response.status shouldBe HttpStatusCode.PayloadTooLarge
    }

    @Test
    fun `large body exceeding the limit is rejected without buffering it whole`() = testApplication {
        // 8 MB body against a 4 MB limit: must be rejected.
        installEchoEndpoint(maxBytes = 4L * 1024 * 1024)
        val payload = "x".repeat(8 * 1024 * 1024)

        val response = client.post("/echo") { setBody(payload) }

        response.status shouldBe HttpStatusCode.PayloadTooLarge
    }

    @Test
    fun `multibyte UTF-8 body larger than a read chunk decodes intact`() = testApplication {
        // Emoji is 4 bytes in UTF-8; a payload larger than the internal read chunk forces the
        // accumulated bytes to span multiple chunks. Decoding once at the end must not corrupt
        // characters that straddle a chunk boundary.
        val unit = "a😀b" // mixes 1- and 4-byte code units so boundaries land mid-character
        val payload = unit.repeat(40_000) // well over a 64 KiB read chunk
        installEchoEndpoint(maxBytes = payload.encodeToByteArray().size.toLong())

        val response = client.post("/echo") { setBody(payload) }

        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe payload
    }

    @Test
    fun `body is returned intact when the limit is Long MAX_VALUE`() = testApplication {
        installEchoEndpoint(maxBytes = Long.MAX_VALUE)
        val payload = "hello world"

        val response = client.post("/echo") { setBody(payload) }

        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe payload
    }
}
