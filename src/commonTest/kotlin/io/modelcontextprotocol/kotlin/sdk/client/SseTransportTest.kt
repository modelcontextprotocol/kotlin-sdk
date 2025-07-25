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
import io.modelcontextprotocol.kotlin.sdk.server.SseTransportManager
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
            val transportManager = SseTransportManager()
            routing {
                sse {
                    mcpSseTransport("", transportManager).start()
                }

                post {
                    mcpPostEndpoint(transportManager)
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
            val transportManager = SseTransportManager()
            routing {
                sse {
                    mcpSseTransport("", transportManager).apply {
                        onMessage {
                            send(it)
                        }

                        start()
                    }
                }

                post {
                    mcpPostEndpoint(transportManager)
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
            val transportManager = SseTransportManager()
            routing {
                route("/sse") {
                    sse {
                        mcpSseTransport("", transportManager).apply {
                            onMessage {
                                send(it)
                            }

                            start()
                        }
                    }

                    post {
                        mcpPostEndpoint(transportManager)
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
