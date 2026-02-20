package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Integration tests for Ktor Route.mcp() extensions.
 *
 * Verifies issue #237: Route.mcp() should work on subpaths and with middleware.
 * The key issue was that Routing.mcp() registered at top-level, preventing use on subpaths.
 * Now Route.mcp() allows registration on any route path.
 */
class KtorRouteExtensionsTest {

    /**
     * Verifies that Route.mcp() throws immediately at route registration time
     * when the SSE plugin has not been installed, rather than failing silently on the first request.
     */
    @Test
    fun `Route mcp should throw at registration time if SSE plugin is not installed`() {
        val exception = assertFailsWith<IllegalStateException> {
            testApplication {
                application {
                    // Intentionally omit install(SSE)
                    routing {
                        mcp { testServer() }
                    }
                }
                client.get("/")
            }
        }
        exception.message shouldNotBeNull {
            shouldContain("SSE")
            shouldContain("install")
        }
    }

    /**
     * Verifies that Route.mcp() registers SSE and POST endpoints on the subpath,
     * not at root, and does not disturb sibling routes.
     */
    @Test
    fun `Route mcp should register SSE and POST endpoints at the given subpath`() = testApplication {
        application {
            install(SSE)

            routing {
                get("/") { call.respondText("root") }

                route("/api/mcp") {
                    get("/test") { call.respondText("test-endpoint") }
                    mcp { testServer() }
                }
            }
        }

        // Sibling routes are unaffected
        val rootResponse = client.get("/")
        rootResponse.shouldHaveStatus(HttpStatusCode.OK)
        rootResponse.bodyAsText() shouldBe "root"

        val testResponse = client.get("/api/mcp/test")
        testResponse.shouldHaveStatus(HttpStatusCode.OK)
        testResponse.bodyAsText() shouldBe "test-endpoint"

        client.assertMcpEndpointsAt("/api/mcp")
    }

    /**
     * Verifies that Route.mcp() registers endpoints at the correct fully-qualified
     * path when nested inside multiple route() calls.
     */
    @Test
    fun `Route mcp should register endpoints at the full nested path`() = testApplication {
        application {
            install(SSE)

            routing {
                route("/v1") {
                    route("/services") {
                        route("/mcp") {
                            mcp { testServer() }
                        }
                    }
                }
            }
        }

        client.assertMcpEndpointsAt("/v1/services/mcp")
    }

    /**
     * Verifies that mcp(path) registers SSE and POST endpoints at the sub-path
     * relative to the enclosing route, not at the root.
     */
    @Test
    fun `Route mcp with path parameter should register endpoints at the resolved path`() = testApplication {
        application {
            install(SSE)

            routing {
                route("/api") {
                    mcp("/mcp-endpoint") { testServer() }
                }
            }
        }

        client.assertMcpEndpointsAt("/api/mcp-endpoint")

        // Root /api is not an MCP endpoint
        client.post("/api").shouldHaveStatus(HttpStatusCode.NotFound)
    }
}
