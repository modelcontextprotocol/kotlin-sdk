package client

import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import mcpSse
import mcpSseTransport
import io.modelcontextprotocol.kotlin.sdk.client.mcpSseTransport
import org.junit.jupiter.api.Test

private const val PORT = 8080

class SseTransportTest : BaseTransportTest() {
    @Test
    fun `should start then close cleanly`() = runTest {
        val server = embeddedServer(CIO, port = PORT) {
            install(io.ktor.server.sse.SSE)
            routing {
                mcpSse()
            }
        }.start(wait = false)

        val client = HttpClient {
            install(SSE)
        }.mcpSseTransport {
            url {
                host = "localhost"
                port = PORT
            }
        }

        testClientOpenClose(client)

        server.stop()
    }

    @Test
    fun `should read messages`() = runTest {
        val clientFinished = CompletableDeferred<Unit>()

        val server = embeddedServer(CIO, port = PORT) {
            install(io.ktor.server.sse.SSE)
            routing {
                mcpSseTransport {
                    onMessage = {
                        send(it)
                    }
                }
            }
        }.start(wait = false)

        val client = HttpClient {
            install(SSE)
        }.mcpSseTransport {
            url {
                host = "localhost"
                port = PORT
            }
        }

        testClientRead(client)
        server.stop()
    }
}
