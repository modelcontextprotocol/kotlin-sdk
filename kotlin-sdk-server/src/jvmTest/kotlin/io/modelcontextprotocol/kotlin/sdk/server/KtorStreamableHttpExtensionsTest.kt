package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
 * on missing SSE plugin, sibling routes unaffected). Full transport-level behaviour
 * (session lifecycle, JSON-RPC handling) is covered in [StreamableHttpServerTransportTest].
 */
class KtorStreamableHttpRouteExtensionsTest : AbstractKtorExtensionsTest() {

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
        exception.message shouldContain "SSE"
        exception.message shouldContain "install"
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

// ──────────────────────────────────────────────────────────────────────────────
// Route.mcpStatelessStreamableHttp tests
// ──────────────────────────────────────────────────────────────────────────────

class KtorStatelessStreamableHttpRouteExtensionsTest : AbstractKtorExtensionsTest() {

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
        exception.message shouldContain "SSE"
        exception.message shouldContain "install"
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
}

// ──────────────────────────────────────────────────────────────────────────────
// Application.mcpStreamableHttp and Application.mcpStatelessStreamableHttp tests
// ──────────────────────────────────────────────────────────────────────────────

class KtorStreamableHttpApplicationExtensionsTest : AbstractKtorExtensionsTest() {

    @Test
    fun `Application mcpStreamableHttp should install SSE and register endpoints at default path`() = testApplication {
        application {
            mcpStreamableHttp { testServer() }
        }

        client.assertStreamableHttpEndpointsAt("/mcp")
    }

    @Test
    fun `Application mcpStreamableHttp should register endpoints at a custom path`() = testApplication {
        application {
            mcpStreamableHttp(path = "/api/v1/mcp") { testServer() }
        }

        client.assertStreamableHttpEndpointsAt("/api/v1/mcp")

        // Default path is not registered
        client.get("/mcp").shouldHaveStatus(HttpStatusCode.NotFound)
    }

    @Test
    fun `Application mcpStreamableHttp should coexist with other routes`() = testApplication {
        application {
            mcpStreamableHttp { testServer() }
            routing {
                get("/health") { call.respondText("healthy") }
            }
        }

        val healthResponse = client.get("/health")
        healthResponse.shouldHaveStatus(HttpStatusCode.OK)
        healthResponse.bodyAsText() shouldBe "healthy"

        client.assertStreamableHttpEndpointsAt("/mcp")
    }

    @Test
    fun `Application mcpStatelessStreamableHttp should install SSE and register endpoints at default path`() =
        testApplication {
            application {
                mcpStatelessStreamableHttp { testServer() }
            }

            client.assertStatelessStreamableHttpEndpointsAt("/mcp")
        }

    @Test
    fun `Application mcpStatelessStreamableHttp should register endpoints at a custom path`() = testApplication {
        application {
            mcpStatelessStreamableHttp(path = "/api/v1/mcp") { testServer() }
        }

        client.assertStatelessStreamableHttpEndpointsAt("/api/v1/mcp")

        // Default path is not registered
        client.get("/mcp").shouldHaveStatus(HttpStatusCode.NotFound)
    }
}

/**
 * Asserts that stateful Streamable HTTP MCP endpoints are registered at [path]:
 * - GET opens an SSE connection (200 OK); session validation inside the SSE body cannot change
 *   the already-committed status, so the connection closes immediately without a session
 * - DELETE without a session ID returns 400 Bad Request
 * - POST is routed to the transport (returns 406 for a deliberately wrong Accept, confirming the route exists)
 *
 * Use [configureRequest] to add headers (e.g. `basicAuth(...)`) to every request.
 */
private suspend fun HttpClient.assertStreamableHttpEndpointsAt(
    path: String,
    configureRequest: HttpRequestBuilder.() -> Unit = {},
) {
    // GET starts an SSE handshake — 200 is committed before the body runs
    get(path) { configureRequest() }.shouldHaveStatus(HttpStatusCode.OK)

    // DELETE without session ID is rejected by the route handler
    delete(path) { configureRequest() }.shouldHaveStatus(HttpStatusCode.BadRequest)

    // POST reaches the transport: a wrong Accept header triggers 406, not 404
    post(path) {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Accept, ContentType.Text.Plain.toString())
        configureRequest()
    }.shouldHaveStatus(HttpStatusCode.NotAcceptable)
}

/**
 * Asserts that stateless Streamable HTTP MCP endpoints are registered at [path]:
 * - GET returns 405 Method Not Allowed (explicitly rejected by the stateless routing layer)
 * - DELETE returns 405 Method Not Allowed (same)
 * - POST is routed to the transport (returns 406 for a deliberately wrong Accept, confirming the route exists)
 *
 * Use [configureRequest] to add headers (e.g. `basicAuth(...)`) to every request.
 */
private suspend fun HttpClient.assertStatelessStreamableHttpEndpointsAt(
    path: String,
    configureRequest: HttpRequestBuilder.() -> Unit = {},
) {
    get(path) { configureRequest() }.shouldHaveStatus(HttpStatusCode.MethodNotAllowed)
    delete(path) { configureRequest() }.shouldHaveStatus(HttpStatusCode.MethodNotAllowed)

    post(path) {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Accept, ContentType.Text.Plain.toString())
        configureRequest()
    }.shouldHaveStatus(HttpStatusCode.NotAcceptable)
}
