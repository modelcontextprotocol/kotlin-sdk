package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test

class KtorTransportDecoratorTest : AbstractKtorExtensionsTest() {

    @Test
    fun `Application mcp connects the decorated SSE transport`() = testApplication {
        lateinit var decoratedTransport: StartTrackingTransport

        application {
            mcp(
                enableDnsRebindingProtection = false,
                transportDecorator = { transport ->
                    StartTrackingTransport(transport).also { decoratedTransport = it }
                },
            ) { testServer() }
        }

        client.assertMcpEndpointsAt("/")
        decoratedTransport.startCalls.get() shouldBe 1
    }

    @Test
    fun `Route mcp with path connects the decorated SSE transport`() = testApplication {
        lateinit var decoratedTransport: StartTrackingTransport

        application {
            install(SSE)
            routing {
                mcp(
                    path = "/mcp",
                    enableDnsRebindingProtection = false,
                    transportDecorator = { transport ->
                        StartTrackingTransport(transport).also { decoratedTransport = it }
                    },
                ) { testServer() }
            }
        }

        client.assertMcpEndpointsAt("/mcp")
        decoratedTransport.startCalls.get() shouldBe 1
    }

    @Test
    fun `Streamable HTTP connects the decorated transport`() = testApplication {
        lateinit var decoratedTransport: StartTrackingTransport

        application {
            mcpStreamableHttp(
                enableDnsRebindingProtection = false,
                transportDecorator = { transport ->
                    StartTrackingTransport(transport).also { decoratedTransport = it }
                },
            ) { testServer() }
        }

        val response = client.post("/mcp") {
            header(
                HttpHeaders.Accept,
                "${ContentType.Application.Json}, ${ContentType.Text.EventStream}",
            )
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "method": "initialize",
                  "params": {
                    "protocolVersion": "$LATEST_PROTOCOL_VERSION",
                    "capabilities": {},
                    "clientInfo": {"name": "test-client", "version": "1.0.0"}
                  }
                }
                """.trimIndent(),
            )
        }

        response.shouldHaveStatus(HttpStatusCode.OK)
        decoratedTransport.startCalls.get() shouldBe 1
    }
}

private class StartTrackingTransport(private val delegate: Transport) : Transport by delegate {
    val startCalls: AtomicInteger = AtomicInteger()

    override suspend fun start() {
        startCalls.incrementAndGet()
        delegate.start()
    }
}
