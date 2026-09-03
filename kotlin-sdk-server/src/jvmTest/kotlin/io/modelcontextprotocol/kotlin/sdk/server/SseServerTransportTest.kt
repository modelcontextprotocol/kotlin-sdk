package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.readLine
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class SseServerTransportTest : AbstractKtorExtensionsTest() {

    @Test
    fun `close awaits suspending callback without waiting for the session job`() = runTest {
        val session = mockk<ServerSSESession>(relaxed = true)
        every { session.coroutineContext } returns currentCoroutineContext()
        val transport = SseServerTransport("/messages", session)
        val callbackStarted = CompletableDeferred<Unit>()
        val releaseCallback = CompletableDeferred<Unit>()

        transport.onClose {
            callbackStarted.complete(Unit)
            releaseCallback.await()
        }
        transport.start()

        val closeJob = launch { transport.close() }
        withTimeout(1.seconds) { callbackStarted.await() }
        closeJob.isActive shouldBe true

        releaseCallback.complete(Unit)
        closeJob.join()
    }

    @Test
    fun `handlePostMessage on a not-started transport does not deliver the message`() = testApplication {
        // A registered onMessage callback opens the delivery gate, so if the not-initialized branch
        // falls through it would wrongly hand the message to the application. It must return instead.
        val transport = SseServerTransport("/messages", mockk<ServerSSESession>(relaxed = true))
        val delivered = AtomicBoolean(false)
        transport.onMessage { delivered.set(true) }

        application {
            routing {
                post("/messages") { transport.handlePostMessage(call) }
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
            val sessionId = response.readSessionId()
            requireNotNull(sessionId) { "sessionId not found in SSE endpoint event" }

            // A body one byte over the configured limit must be rejected before processing.
            val oversized = "x".repeat((MAX_BODY + 1).toInt())
            val postResponse = client.post("/?sessionId=$sessionId") {
                contentType(ContentType.Application.Json)
                setBody(oversized)
            }
            postResponse.status shouldBe HttpStatusCode.PayloadTooLarge
        }
    }

    private suspend fun HttpResponse.readSessionId(): String? {
        val channel = bodyAsChannel()
        var eventName: String? = null
        while (!channel.isClosedForRead) {
            val line = channel.readLine() ?: break
            when {
                line.startsWith("event:") -> eventName = line.substringAfter("event:").trim()

                line.startsWith("data:") && eventName == "endpoint" -> {
                    return line.substringAfter("data:").trim().substringAfter("sessionId=").ifEmpty { null }
                }
            }
        }
        return null
    }

    private companion object {
        const val MAX_BODY = 1024L
    }
}
