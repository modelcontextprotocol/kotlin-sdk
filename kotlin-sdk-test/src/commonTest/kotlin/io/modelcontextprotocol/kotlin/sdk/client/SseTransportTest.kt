package io.modelcontextprotocol.kotlin.sdk.client

import io.ktor.client.HttpClient
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.shared.BaseTransportTest
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import io.ktor.client.plugins.sse.SSE as ClientSSE
import io.ktor.server.sse.SSE as ServerSSE

class SseTransportTest : BaseTransportTest() {

    private suspend fun EmbeddedServer<*, *>.actualPort() = engine.resolvedConnectors().first().port

    private lateinit var mcpServer: Server

    @BeforeTest
    fun setUp() {
        mcpServer = Server(
            serverInfo = Implementation(
                name = "test-server",
                version = "1.0"
            ),
            options = ServerOptions(ServerCapabilities())
        )
    }

    @Test
    fun `should start then close cleanly`() = runTest {
        val server = embeddedServer(CIO, port = 0) {
            install(ServerSSE)
            routing {
                mcp { mcpServer }
            }
        }.startSuspend(wait = false)

        val actualPort = server.actualPort()

        val client = HttpClient {
            install(ClientSSE)
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

    @Ignore
    @Test
    fun `should read messages`() = runTest {
        val server = embeddedServer(CIO, port = 0) {
            install(ServerSSE)
            routing {
                mcp { mcpServer }
//                sse {
//                    mcpSseTransport("", transports).apply {
//                        onMessage {
//                            send(it)
//                        }
//
//                        start()
//                    }
//                }
//
//                post {
//                    mcpPostEndpoint(transports)
//                }
            }
        }.startSuspend(wait = false)

        val actualPort = server.actualPort()

        val client = HttpClient {
            install(ClientSSE)
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

    @Ignore
    @Test
    fun `test sse path not root path`() = runTest {
        val server = embeddedServer(CIO, port = 0) {
            install(ServerSSE)
            routing {
                mcp("/sse") { mcpServer }
//                route("/sse") {
//                    sse {
//                        mcpSseTransport("", transports).apply {
//                            onMessage {
//                                send(it)
//                            }
//
//                            start()
//                        }
//                    }
//
//                    post {
//                        mcpPostEndpoint(transports)
//                    }
//                }
            }
        }.startSuspend(wait = false)

        val actualPort = server.actualPort()

        val client = HttpClient {
            install(ClientSSE)
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
