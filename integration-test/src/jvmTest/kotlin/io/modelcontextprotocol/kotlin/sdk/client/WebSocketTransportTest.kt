package io.modelcontextprotocol.kotlin.sdk.client

import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.modelcontextprotocol.kotlin.sdk.server.mcpWebSocket
import io.modelcontextprotocol.kotlin.sdk.server.mcpWebSocketTransport
import io.modelcontextprotocol.kotlin.sdk.shared.BaseTransportTest
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(30, unit = TimeUnit.SECONDS)
class WebSocketTransportTest : BaseTransportTest() {
    @Test
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
