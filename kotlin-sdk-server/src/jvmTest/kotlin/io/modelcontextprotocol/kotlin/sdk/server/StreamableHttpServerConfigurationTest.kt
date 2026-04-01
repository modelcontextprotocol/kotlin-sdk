package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.collections.shouldExist
import io.kotest.matchers.collections.shouldHaveSize
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlin.test.Test
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

private const val MCP_SERVER_PACKAGE = "io.modelcontextprotocol.kotlin.sdk.server"

class StreamableHttpServerConfigurationTest {

    private fun testServer(): Server = Server(
        Implementation("test-server", "1.0"),
        ServerOptions(capabilities = ServerCapabilities()),
    )

    @Test
    fun `mcpStreamableHttp warns when ContentNegotiation is pre-installed`() {
        LogCapture(MCP_SERVER_PACKAGE).use { logs ->
            testApplication {
                application {
                    install(ServerContentNegotiation) {
                        json() // Pre-install with default (wrong) config
                    }
                    mcpStreamableHttp { testServer() }
                }
                client.get("/mcp")
            }
            logs.messages.shouldExist { "ContentNegotiation is already installed" in it }
        }
    }

    @Test
    fun `mcpStatelessStreamableHttp warns when ContentNegotiation is pre-installed`() {
        LogCapture(MCP_SERVER_PACKAGE).use { logs ->
            testApplication {
                application {
                    install(ServerContentNegotiation) {
                        json() // Pre-install with default (wrong) config
                    }
                    mcpStatelessStreamableHttp { testServer() }
                }
                client.get("/mcp")
            }
            logs.messages.shouldExist { "ContentNegotiation is already installed" in it }
        }
    }

    @Test
    fun `mcp warns when ContentNegotiation is pre-installed`() {
        LogCapture(MCP_SERVER_PACKAGE).use { logs ->
            testApplication {
                application {
                    install(ServerContentNegotiation) {
                        json() // Pre-install with default (wrong) config
                    }
                    mcp { testServer() }
                }
                client.get("/sse")
            }
            logs.messages.shouldExist { "ContentNegotiation is already installed" in it }
        }
    }

    @Test
    fun `installMcpContentNegotiation is idempotent`() {
        LogCapture(MCP_SERVER_PACKAGE).use { logs ->
            testApplication {
                application {
                    installMcpContentNegotiation()
                    installMcpContentNegotiation()
                }
                client.get("/")
            }
            logs.messages.filter { "ContentNegotiation is already installed" in it }
                .shouldHaveSize(0)
        }
    }

    @Test
    fun `installMcpContentNegotiation warns exactly once when ContentNegotiation is pre-installed`() {
        LogCapture(MCP_SERVER_PACKAGE).use { logs ->
            testApplication {
                application {
                    install(ServerContentNegotiation) {
                        json()
                    }
                    installMcpContentNegotiation()
                    installMcpContentNegotiation()
                }
                client.get("/")
            }
            logs.messages.filter { "ContentNegotiation is already installed" in it }
                .shouldHaveSize(1)
        }
    }
}
