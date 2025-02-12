package io.modelcontextprotocol.kotlin.sdk.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.sse
import io.ktor.util.collections.ConcurrentMap
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpPostEndpoint
import io.modelcontextprotocol.kotlin.sdk.server.mcpSseTransport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SseTransportTest : BaseTransportTest() {
    @Test
    fun `should start then close cleanly`() = runTest {
        val port = 8080
        val server = embeddedServer(CIO, port = port) {
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
        }.startSuspend(wait = false)

        val client = HttpClient {
            install(SSE)
        }.mcpSseTransport {
            url {
                host = "localhost"
                this.port = port
            }
        }

        testClientOpenClose(client)

        server.stopSuspend()
    }

    @Test
    fun `should read messages`() = runTest {
        val port = 3003
        val server = embeddedServer(CIO, port = port) {
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
        }.startSuspend(wait = false)

        val client = HttpClient {
            install(SSE)
        }.mcpSseTransport {
            url {
                host = "localhost"
                this.port = port
            }
        }

        testClientRead(client)
        server.stopSuspend()
    }

    @Test
    fun `test sse path not root path`() = runTest {
        val server = embeddedServer(CIO, port = PORT) {
            install(io.ktor.server.sse.SSE)
            val transports = ConcurrentMap<String, SseServerTransport>()
            routing {
                sse("/sse") {
                    mcpSseTransport("/messages", transports).apply {
                        onMessage {
                            send(it)
                        }

                        start()
                    }
                }

                post("/messages") {

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
                pathSegments = listOf("sse")
            }
        }

        testClientRead(client)
        server.stop()
    }

    @Test
    fun `test sse path not root path`() = runTest {
        val server = embeddedServer(CIO, port = PORT) {
            install(io.ktor.server.sse.SSE)
            routing {
                mcpSseTransport(path = "/sse", incomingPath = "/messages") {
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
                pathSegments = listOf("sse")
            }
        }

        testClientRead(client)
        server.stop()
    }
}
