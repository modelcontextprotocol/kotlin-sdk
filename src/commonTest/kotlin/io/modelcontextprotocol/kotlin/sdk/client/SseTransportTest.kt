package io.modelcontextprotocol.kotlin.sdk.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.sse
import io.ktor.util.collections.ConcurrentMap
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpPostEndpoint
import io.modelcontextprotocol.kotlin.sdk.server.mcpSseTransport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SseTransportTest : BaseTransportTest() {

    private suspend fun EmbeddedServer<*, *>.actualPort() = engine.resolvedConnectors().first().port

    @Test
    fun `should start then close cleanly`() = runTest {
        val server = embeddedServer(CIO, port = 0) {
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

        val actualPort = server.actualPort()

        val client = HttpClient {
            install(SSE)
        }.mcpSseTransport {
            url {
                host = "localhost"
                this.port = actualPort
            }
        }

        try {
            testClientOpenClose(client)
        } finally {
            server.stopSuspend()
        }
    }

    @Test
    fun `should read messages`() = runTest {
        val server = embeddedServer(CIO, port = 0) {
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

        val actualPort = server.actualPort()

        val client = HttpClient {
            install(SSE)
        }.mcpSseTransport {
            url {
                host = "localhost"
                this.port = actualPort
            }
        }

        try {
            testClientRead(client)
        } finally {
            server.stopSuspend()
        }
    }

    @Test
    fun `test sse path not root path`() = runTest {
        val server = embeddedServer(CIO, port = 0) {
            install(io.ktor.server.sse.SSE)
            val transports = ConcurrentMap<String, SseServerTransport>()
            routing {
                route("/sse") {
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
            }
        }.startSuspend(wait = false)

        val actualPort = server.actualPort()

        val client = HttpClient {
            install(SSE)
        }.mcpSseTransport {
            url {
                host = "localhost"
                this.port = actualPort
                pathSegments = listOf("sse")
            }
        }

        try {
            testClientRead(client)
        } finally {
            server.stopSuspend()
        }
    }
}
