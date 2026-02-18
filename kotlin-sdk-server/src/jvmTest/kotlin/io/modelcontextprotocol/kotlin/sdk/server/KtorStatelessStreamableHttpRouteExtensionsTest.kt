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

class KtorStatelessStreamableHttpRouteExtensionsTest {

    @Test
    fun `Route mcpStatelessStreamableHttp should throw at registration time if SSE plugin is not installed`() {
        val exception = assertFailsWith<IllegalStateException> {
            testApplication {
                application {
                    routing {
                        mcpStatelessStreamableHttp { testServer() }
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
    fun `Route mcpStatelessStreamableHttp GET and DELETE should return 405 Method Not Allowed`() = testApplication {
        application {
            install(SSE)
            routing {
                route("/mcp") {
                    mcpStatelessStreamableHttp { testServer() }
                }
            }
        }

        client.assertStatelessStreamableHttpEndpointsAt("/mcp")
    }

    @Test
    fun `Route mcpStatelessStreamableHttp should register endpoints at the full nested path`() = testApplication {
        application {
            install(SSE)
            routing {
                route("/v1") {
                    route("/mcp") {
                        mcpStatelessStreamableHttp { testServer() }
                    }
                }
            }
        }

        client.assertStatelessStreamableHttpEndpointsAt("/v1/mcp")
    }

    @Test
    fun `Route mcpStatelessStreamableHttp with path should register endpoints at the resolved subpath`() =
        testApplication {
            application {
                install(SSE)
                routing {
                    route("/api") {
                        mcpStatelessStreamableHttp("/mcp") { testServer() }
                    }
                }
            }

            client.assertStatelessStreamableHttpEndpointsAt("/api/mcp")

            // The parent route /api is not an MCP endpoint
            client.post("/api").shouldHaveStatus(HttpStatusCode.NotFound)
        }

    @Test
    fun `Route mcpStatelessStreamableHttp should not interfere with sibling routes`() = testApplication {
        application {
            install(SSE)
            routing {
                get("/health") { call.respondText("ok") }
                route("/mcp") {
                    get("/docs") { call.respondText("docs") }
                    mcpStatelessStreamableHttp { testServer() }
                }
            }
        }

        val healthResponse = client.get("/health")
        healthResponse.shouldHaveStatus(HttpStatusCode.OK)
        healthResponse.bodyAsText() shouldBe "ok"

        val docsResponse = client.get("/mcp/docs")
        docsResponse.shouldHaveStatus(HttpStatusCode.OK)
        docsResponse.bodyAsText() shouldBe "docs"

        client.assertStatelessStreamableHttpEndpointsAt("/mcp")
    }
}
