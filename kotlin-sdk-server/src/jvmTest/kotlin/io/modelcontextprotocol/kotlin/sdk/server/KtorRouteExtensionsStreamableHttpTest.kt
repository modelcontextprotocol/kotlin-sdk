package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
 * Integration tests for Ktor Route.mcpStreamableHttp() / Route.mcpStatelessStreamableHttp()
 * extensions.
 *
 * These extensions let callers register the Streamable HTTP transport inside an existing
 * routing block — for example, nested under `authenticate(...) { ... }` so the Ktor auth
 * plugin gates every request. `Application.mcpStreamableHttp()` installs its own routing
 * tree and therefore cannot be nested that way.
 *
 * These tests focus on route *mounting* / *nesting* behaviour; the transport-level
 * semantics are already covered by StreamableHttpServerTransportTest et al.
 */
class KtorRouteExtensionsStreamableHttpTest : AbstractKtorExtensionsTest() {

    private fun initializeBody(id: Int = 1): String =
        """{"jsonrpc":"2.0","id":$id,"method":"initialize","params":""" +
            """{"protocolVersion":"2025-06-18","capabilities":{},""" +
            """"clientInfo":{"name":"t","version":"1"}}}"""

    private fun HttpRequestBuilder.addStreamableHeaders() {
        header(
            HttpHeaders.Accept,
            listOf(ContentType.Application.Json, ContentType.Text.EventStream)
                .joinToString(", ") { it.toString() },
        )
        contentType(ContentType.Application.Json)
    }

    /**
     * Route.mcpStreamableHttp() registers endpoints on the subpath and does not
     * disturb sibling routes.
     */
    @Test
    fun `Route mcpStreamableHttp should register endpoints at the given subpath`() = testApplication {
        application {
            installMcpContentNegotiation()
            install(SSE)

            routing {
                get("/") { call.respondText("root") }

                route("/api") {
                    get("/hello") { call.respondText("hello") }
                    mcpStreamableHttp(path = "/mcp", enableDnsRebindingProtection = false) {
                        testServer()
                    }
                }
            }
        }

        // Sibling routes are unaffected.
        client.get("/").apply {
            shouldHaveStatus(HttpStatusCode.OK)
            bodyAsText() shouldBe "root"
        }
        client.get("/api/hello").apply {
            shouldHaveStatus(HttpStatusCode.OK)
            bodyAsText() shouldBe "hello"
        }

        val response = client.post("/api/mcp") {
            addStreamableHeaders()
            setBody(initializeBody())
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        val sessionId = response.headers[MCP_SESSION_ID_HEADER]
        checkNotNull(sessionId) { "Mcp-Session-Id header not returned on initialize response" }
    }

    /**
     * Route.mcpStreamableHttp() honours full nesting when placed several routes deep.
     */
    @Test
    fun `Route mcpStreamableHttp should register endpoints at the full nested path`() = testApplication {
        application {
            installMcpContentNegotiation()
            install(SSE)

            routing {
                route("/v1") {
                    route("/services") {
                        mcpStreamableHttp(path = "/mcp", enableDnsRebindingProtection = false) {
                            testServer()
                        }
                    }
                }
            }
        }

        val response = client.post("/v1/services/mcp") {
            addStreamableHeaders()
            setBody(initializeBody())
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        checkNotNull(response.headers[MCP_SESSION_ID_HEADER]) {
            "Mcp-Session-Id header not returned on initialize response"
        }
    }

    /**
     * Route.mcpStatelessStreamableHttp() also mounts under a nested route and answers POST
     * with JSON (no session id); GET returns 405 Method Not Allowed.
     */
    @Test
    fun `Route mcpStatelessStreamableHttp should register at the given subpath`() = testApplication {
        application {
            installMcpContentNegotiation()

            routing {
                route("/api") {
                    mcpStatelessStreamableHttp(
                        path = "/mcp",
                        enableDnsRebindingProtection = false,
                    ) { testServer() }
                }
            }
        }

        val postResponse = client.post("/api/mcp") {
            addStreamableHeaders()
            setBody(initializeBody())
        }
        postResponse.shouldHaveStatus(HttpStatusCode.OK)

        client.get("/api/mcp").shouldHaveStatus(HttpStatusCode.MethodNotAllowed)
    }

    /**
     * Route.mcpStreamableHttp() surfaces missing-plugin failures instead of silently
     * accepting requests when SSE has not been installed.
     */
    @Test
    fun `Route mcpStreamableHttp should fail if SSE plugin is not installed`() {
        assertFailsWith<Throwable> {
            testApplication {
                application {
                    // Intentionally omit install(SSE)
                    routing {
                        mcpStreamableHttp(enableDnsRebindingProtection = false) { testServer() }
                    }
                }
                client.get("/mcp")
            }
        }
    }
}
