package io.modelcontextprotocol.kotlin.sdk.client

import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.modelcontextprotocol.kotlin.sdk.server.mcpWebSocket
import io.modelcontextprotocol.kotlin.sdk.server.mcpWebSocketTransport
import io.modelcontextprotocol.kotlin.sdk.shared.BaseTransportTest
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Ignore
import kotlin.test.Test

class WebSocketTransportTest : BaseTransportTest() {
    @Test
    @Ignore // "Test disabled for investigation #17"
    fun `should start then close cleanly`() = testApplication {
        install(WebSockets)
        routing {
            mcpWebSocket()
        }

        val client = createClient {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }.mcpWebSocketTransport()

        testTransportOpenClose(client)
    }

    @Test
    @Ignore // "Test disabled for investigation #17"
    fun `should read messages`() = testApplication {
        val clientFinished = CompletableDeferred<Unit>()

        install(WebSockets)
        routing {
            mcpWebSocketTransport {
                onMessage {
                    send(it)
                }

                clientFinished.await()
            }
        }

        val transport = createClient {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }.mcpWebSocketTransport()

        testTransportRead(transport)

        clientFinished.complete(Unit)
    }
}
