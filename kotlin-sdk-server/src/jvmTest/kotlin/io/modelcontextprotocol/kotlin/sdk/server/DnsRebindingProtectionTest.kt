package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlin.test.Test

class DnsRebindingProtectionTest {

    private fun testWithPlugin(
        config: DnsRebindingProtectionConfig.() -> Unit = {},
        test: suspend ApplicationTestBuilder.() -> Unit,
    ): Unit = testApplication {
        application {
            install(SSE)
            routing {
                route("/mcp") {
                    install(DnsRebindingProtection, config)
                    post { call.respondText("ok") }
                }
            }
        }
        test()
    }

    @Test
    fun `plugin rejects request with missing Host header`() = testWithPlugin {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "")
        }
        response.shouldHaveStatus(HttpStatusCode.Forbidden)
        response.bodyAsText() shouldContain "Invalid Host header"
    }

    @Test
    fun `plugin allows localhost Host header`() = testWithPlugin {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @Test
    fun `plugin allows 127_0_0_1 Host header`() = testWithPlugin {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "127.0.0.1")
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @Test
    fun `plugin allows IPv6 localhost Host header`() = testWithPlugin {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "[::1]")
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @Test
    fun `plugin strips port from Host header`() = testWithPlugin {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost:3000")
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @Test
    fun `plugin strips port from IPv6 Host header`() = testWithPlugin {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "[::1]:8080")
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @Test
    fun `plugin rejects non-localhost Host header`() = testWithPlugin {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "evil.com")
        }
        response.shouldHaveStatus(HttpStatusCode.Forbidden)
        response.bodyAsText() shouldContain "Invalid Host header"
    }

    @Test
    fun `plugin allows request without Origin header`() = testWithPlugin(
        config = { allowedOrigins = listOf("http://localhost:3000") },
    ) {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @Test
    fun `plugin rejects disallowed Origin`() = testWithPlugin(
        config = { allowedOrigins = listOf("http://localhost:3000") },
    ) {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Origin, "http://evil.com")
        }
        response.shouldHaveStatus(HttpStatusCode.Forbidden)
        response.bodyAsText() shouldContain "Invalid Origin header"
    }

    @Test
    fun `plugin allows matching Origin`() = testWithPlugin(
        config = { allowedOrigins = listOf("http://localhost:3000") },
    ) {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Origin, "http://localhost:3000")
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @Test
    fun `plugin with custom allowedHosts accepts matching host`() = testWithPlugin(
        config = { allowedHosts = listOf("myapp.com") },
    ) {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "myapp.com:443")
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @Test
    fun `plugin with custom allowedHosts rejects non-matching host`() = testWithPlugin(
        config = { allowedHosts = listOf("myapp.com") },
    ) {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
        }
        response.shouldHaveStatus(HttpStatusCode.Forbidden)
    }

    @Test
    fun `host validation is case insensitive`() = testWithPlugin(
        config = { allowedHosts = listOf("MyApp.COM") },
    ) {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "myapp.com")
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @Test
    fun `Route mcp with DNS protection enabled rejects non-localhost`() = testApplication {
        application {
            install(SSE)
            routing {
                mcp { testServer() }
            }
        }

        val response = client.post("/") {
            header(HttpHeaders.Host, "evil.com")
            contentType(ContentType.Application.Json)
        }
        response.shouldHaveStatus(HttpStatusCode.Forbidden)
    }

    @Test
    fun `Route mcp with DNS protection disabled allows any host`() = testApplication {
        application {
            install(SSE)
            routing {
                mcp(enableDnsRebindingProtection = false) { testServer() }
            }
        }

        val response = client.post("/") {
            header(HttpHeaders.Host, "evil.com")
            contentType(ContentType.Application.Json)
        }
        // Not 403 — the request reaches the handler (may get 400 for missing sessionId, etc.)
        response.shouldHaveStatus(HttpStatusCode.BadRequest)
    }

    // -- extractHostname unit tests --

    @Test
    fun `extractHostname strips port from hostname`() {
        extractHostname("localhost:3000") shouldBe "localhost"
    }

    @Test
    fun `extractHostname returns hostname without port unchanged`() {
        extractHostname("localhost") shouldBe "localhost"
    }

    @Test
    fun `extractHostname handles IPv4 with port`() {
        extractHostname("127.0.0.1:8080") shouldBe "127.0.0.1"
    }

    @Test
    fun `extractHostname handles IPv6 with port`() {
        extractHostname("[::1]:3000") shouldBe "[::1]"
    }

    @Test
    fun `extractHostname handles IPv6 without port`() {
        extractHostname("[::1]") shouldBe "[::1]"
    }

    @Test
    fun `extractHostname returns null for empty string`() {
        extractHostname("") shouldBe null
    }

    private fun testServer(): Server = Server(
        serverInfo = Implementation(name = "test-server", version = "1.0.0"),
        options = ServerOptions(capabilities = ServerCapabilities()),
    )
}
