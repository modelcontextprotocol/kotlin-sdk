package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlin.test.Test
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class StreamableHttpServerConfigurationTest {

    private fun testServer() = Server(
        Implementation("test-server", "1.0"),
        ServerOptions(capabilities = ServerCapabilities()),
    )

    @Test
    fun `mcpStreamableHttp warns when ContentNegotiation is pre-installed`() {
        val logOutput = captureStderr {
            testApplication {
                application {
                    install(ServerContentNegotiation) {
                        json() // Pre-install with default (wrong) config
                    }
                    mcpStreamableHttp { testServer() }
                }
                client.get("/mcp")
            }
        }

        logOutput.shouldNotBeEmpty()
        logOutput shouldContain "already installed with an unknown JSON configuration"
    }

    @Test
    fun `mcpStatelessStreamableHttp warns when ContentNegotiation is pre-installed`() {
        val logOutput = captureStderr {
            testApplication {
                application {
                    install(ServerContentNegotiation) {
                        json() // Pre-install with default (wrong) config
                    }
                    mcpStatelessStreamableHttp { testServer() }
                }
                client.get("/mcp")
            }
        }

        logOutput.shouldNotBeEmpty()
        logOutput shouldContain "already installed with an unknown JSON configuration"
    }

    @Test
    fun `mcp warns when ContentNegotiation is pre-installed`() {
        val logOutput = captureStderr {
            testApplication {
                application {
                    install(ServerContentNegotiation) {
                        json() // Pre-install with default (wrong) config
                    }
                    mcp { testServer() }
                }
                client.get("/sse")
            }
        }

        logOutput shouldContain "already installed with an unknown JSON configuration"
    }
}
