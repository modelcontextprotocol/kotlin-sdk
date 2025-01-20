package client

import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.sse.sse
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.test.runTest
import io.modelcontextprotocol.kotlin.sdk.client.mcpSseTransport
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpPostEndpoint
import io.modelcontextprotocol.kotlin.sdk.server.mcpSseTransport
import org.junit.jupiter.api.Test

private const val PORT = 8080

class SseTransportTest : BaseTransportTest() {
    @Test
    fun `should start then close cleanly`() = runTest {
        val server = embeddedServer(CIO, port = PORT) {
            install(io.ktor.server.sse.SSE)
            val transports = ConcurrentMap<String, SseServerTransport>()
            routing {
                sse {
                    mcpSseTransport("", transports).start()
                }

                post {
                    mcpPostEndpoint(transports)
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

        testClientOpenClose(client)

        server.stop()
    }

    @Test
    fun `should read messages`() = runTest {
        val server = embeddedServer(CIO, port = PORT) {
            install(io.ktor.server.sse.SSE)
            val transports = ConcurrentMap<String, SseServerTransport>()
            routing {
                sse {
                    mcpSseTransport("", transports).apply {
                        onMessage {
                            send(it)
                        }

                        start()
                    }
                }

                post {
                    mcpPostEndpoint(transports)
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
