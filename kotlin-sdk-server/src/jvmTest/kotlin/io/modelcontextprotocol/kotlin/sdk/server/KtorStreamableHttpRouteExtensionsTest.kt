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
 * Integration tests for [Route.mcpStreamableHttp] and [Route.mcpStatelessStreamableHttp].
 *
 * Route-level tests focus on routing correctness (correct paths registered, fail-fast
 * on missing SSE plugin, sibling routes unaffected).
 */
class KtorStreamableHttpRouteExtensionsTest {

    @Test
    fun `Route mcpStreamableHttp should throw at registration time if SSE plugin is not installed`() {
        val exception = assertFailsWith<IllegalStateException> {
            testApplication {
                application {
                    // Intentionally omit install(SSE)
                    routing {
                        mcpStreamableHttp { testServer() }
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

    @Test
    fun `Route mcpStreamableHttp should register GET DELETE and POST endpoints at the current route`() =
        testApplication {
            application {
                install(SSE)
                routing {
                    route("/mcp") {
                        mcpStreamableHttp { testServer() }
                    }
                }
            }

            client.assertStreamableHttpEndpointsAt("/mcp")
        }

    @Test
    fun `Route mcpStreamableHttp should register endpoints at the full nested path`() = testApplication {
        application {
            install(SSE)
            routing {
                route("/v1") {
                    route("/services") {
                        route("/mcp") {
                            mcpStreamableHttp { testServer() }
                        }
                    }
                }
            }
        }

        client.assertStreamableHttpEndpointsAt("/v1/services/mcp")
    }

    @Test
    fun `Route mcpStreamableHttp with path should register endpoints at the resolved subpath`() = testApplication {
        application {
            install(SSE)
            routing {
                route("/api") {
                    mcpStreamableHttp("/mcp-endpoint") { testServer() }
                }
            }
        }

        client.assertStreamableHttpEndpointsAt("/api/mcp-endpoint")

        // The parent route /api is not an MCP endpoint
        client.post("/api").shouldHaveStatus(HttpStatusCode.NotFound)
    }

    @Test
    fun `Route mcpStreamableHttp should not interfere with sibling routes`() = testApplication {
        application {
            install(SSE)
            routing {
                get("/health") { call.respondText("ok") }
                route("/mcp") {
                    get("/docs") { call.respondText("docs") }
                    mcpStreamableHttp { testServer() }
                }
            }
        }

        val healthResponse = client.get("/health")
        healthResponse.shouldHaveStatus(HttpStatusCode.OK)
        healthResponse.bodyAsText() shouldBe "ok"

        val docsResponse = client.get("/mcp/docs")
        docsResponse.shouldHaveStatus(HttpStatusCode.OK)
        docsResponse.bodyAsText() shouldBe "docs"

        client.assertStreamableHttpEndpointsAt("/mcp")
    }
}
