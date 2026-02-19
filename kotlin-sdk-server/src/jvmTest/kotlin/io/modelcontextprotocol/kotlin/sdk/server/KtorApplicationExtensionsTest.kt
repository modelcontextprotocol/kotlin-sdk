package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test

/**
 * Integration tests for Ktor Application.mcp() extension.
 *
 * Verifies that Application.mcp() installs SSE automatically and registers
 * MCP endpoints at the application root, without requiring explicit install(SSE).
 */
class KtorApplicationExtensionsTest : AbstractKtorExtensionsTest() {

    /**
     * Verifies that Application.mcp() does not interfere with other routes
     * added to the same application.
     */
    @Test
    fun `Application mcp should installs SSE and coexist with other routes`() = testApplication {
        application {
            mcp { testServer() }

            routing {
                get("/health") { call.respondText("healthy") }
            }
        }

        val healthResponse = client.get("/health")
        healthResponse.shouldHaveStatus(HttpStatusCode.OK)
        healthResponse.bodyAsText() shouldBe "healthy"

        client.assertMcpEndpointsAt("/")
    }
}
