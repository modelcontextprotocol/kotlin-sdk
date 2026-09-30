package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test

class DnsRebindingProtectionTest {

    companion object {
        /** Origin header (or none) sent against `allowedOrigins = ["http://localhost:3000"]`. */
        @JvmStatic
        fun originCases(): List<Arguments> = listOf(
            Arguments.of(null, HttpStatusCode.OK, "ok"),
            Arguments.of("http://localhost:9999", HttpStatusCode.OK, "ok"),
            Arguments.of("https://localhost:3000", HttpStatusCode.OK, "ok"),
            Arguments.of("http://evil.com", HttpStatusCode.Forbidden, "Invalid Origin host: evil.com"),
            Arguments.of("not-a-url", HttpStatusCode.Forbidden, "Invalid Origin header: (unparseable)"),
        )

        @JvmStatic
        fun extractHostnameAcceptCases(): List<Arguments> = listOf(
            Arguments.of("localhost", "localhost"),
            Arguments.of("localhost:3000", "localhost"),
            Arguments.of("[::1]", "[::1]"),
            Arguments.of("[::1]:3000", "[::1]"),
            Arguments.of("localhost:", "localhost"),
            Arguments.of("[::1]:", "[::1]"),
        )

        @JvmStatic
        fun extractHostnameRejectCases(): List<String> = listOf(
            "", // empty
            "evil.com@localhost", // userinfo
            "evil.com/path", // path
            "evil.com?q=1", // query
            "evil.com#frag", // fragment
            "[::1", // unterminated IPv6
            "[]", // empty brackets
            "[::1]x", // IPv6 followed by something other than a port
            "[::1]:abc", // non-numeric IPv6 port
            "localhost\t:80", // whitespace
            "localhost:abc", // non-numeric port
            ":80", // leading colon
        )
    }

    private fun testWithPlugin(
        config: DnsRebindingProtectionConfig.() -> Unit = {},
        test: suspend ApplicationTestBuilder.() -> Unit,
    ): Unit = testApplication {
        application {
            routing {
                route("/mcp") {
                    install(DnsRebindingProtection, config)
                    post { call.respondText("ok") }
                }
            }
        }
        test()
    }

    @ParameterizedTest
    @ValueSource(strings = ["localhost", "127.0.0.1", "[::1]"])
    fun `plugin accepts valid Host header`(hostHeader: String) = testWithPlugin {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, hostHeader)
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @Test
    fun `plugin does not echo raw malformed Host in rejection`() = testWithPlugin {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "evil.com@localhost")
        }
        response.shouldHaveStatus(HttpStatusCode.Forbidden)
        response.bodyAsText() shouldNotContain "evil.com@localhost"
        response.bodyAsText() shouldContain "malformed or missing"
    }

    @ParameterizedTest
    @MethodSource("originCases")
    fun `plugin validates Origin by hostname only`(
        origin: String?,
        expectedStatus: HttpStatusCode,
        expectedBody: String,
    ) = testWithPlugin(
        config = { allowedOrigins = listOf("http://localhost:3000") },
    ) {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            origin?.let { header(HttpHeaders.Origin, it) }
        }
        response.shouldHaveStatus(expectedStatus)
        response.bodyAsText() shouldContain expectedBody
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
    fun `origin validation is case insensitive`() = testWithPlugin(
        config = {
            allowedHosts = listOf("localhost")
            allowedOrigins = listOf("https://MyApp.COM")
        },
    ) {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Origin, "https://myapp.com")
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @Test
    fun `plugin with empty allowedHosts rejects all requests`() = testWithPlugin(
        config = { allowedHosts = emptyList() },
    ) {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
        }
        response.shouldHaveStatus(HttpStatusCode.Forbidden)
        response.bodyAsText() shouldContain "Invalid Host: localhost"
    }

    @Test
    fun `plugin fails to install when allowedHosts contains an invalid host`() {
        val ex = shouldThrow<IllegalStateException> {
            testWithPlugin(
                config = { allowedHosts = listOf("https://example.com") },
            ) {
                client.post("/mcp") { header(HttpHeaders.Host, "example.com") }
            }
        }
        ex.message shouldContain "https://example.com"
    }

    @Test
    fun `plugin fails to install when allowedOrigins contains an invalid origin`() {
        val ex = shouldThrow<IllegalStateException> {
            testWithPlugin(
                config = { allowedOrigins = listOf("not-a-url") },
            ) {
                client.post("/mcp") { header(HttpHeaders.Host, "localhost") }
            }
        }
        ex.message shouldContain "not-a-url"
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
        // Not 403 — the request reaches the handler, which rejects it for the missing sessionId.
        response.shouldHaveStatus(HttpStatusCode.BadRequest)
    }

    @Test
    fun `mcpStreamableHttp rejects non-localhost Host by default`() = testApplication {
        application {
            mcpStreamableHttp { testServer() }
        }

        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "evil.com")
            contentType(ContentType.Application.Json)
        }
        response.shouldHaveStatus(HttpStatusCode.Forbidden)
    }

    @Test
    fun `mcpStatelessStreamableHttp rejects non-localhost Host by default`() = testApplication {
        application {
            mcpStatelessStreamableHttp { testServer() }
        }

        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "evil.com")
            contentType(ContentType.Application.Json)
        }
        response.shouldHaveStatus(HttpStatusCode.Forbidden)
    }

    // -- default Origin validation (secure-by-default for localhost) --

    @Test
    fun `mcpStreamableHttp rejects hostile Origin by default`() = testApplication {
        application {
            mcpStreamableHttp { testServer() }
        }

        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Origin, "http://evil.com")
            contentType(ContentType.Application.Json)
        }
        response.shouldHaveStatus(HttpStatusCode.Forbidden)
        response.bodyAsText() shouldContain "Invalid Origin host: evil.com"
    }

    @Test
    fun `mcpStreamableHttp allows localhost Origin by default`() = testApplication {
        application {
            mcpStreamableHttp { testServer() }
        }

        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Origin, "http://localhost:5173")
            contentType(ContentType.Application.Json)
        }
        response.status shouldNotBe HttpStatusCode.Forbidden
    }

    @Test
    fun `mcpStreamableHttp with custom allowedHosts does not auto-validate Origin`() = testApplication {
        application {
            mcpStreamableHttp(allowedHosts = listOf("myapp.com")) { testServer() }
        }

        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "myapp.com")
            header(HttpHeaders.Origin, "http://evil.com")
            contentType(ContentType.Application.Json)
        }
        response.status shouldNotBe HttpStatusCode.Forbidden
    }

    // -- extractHostname unit tests --

    @ParameterizedTest
    @MethodSource("extractHostnameAcceptCases")
    fun `extractHostname accepts valid host`(input: String, expected: String) {
        extractHostname(input) shouldBe expected
    }

    @ParameterizedTest
    @MethodSource("extractHostnameRejectCases")
    fun `extractHostname rejects invalid host`(input: String) {
        extractHostname(input) shouldBe null
    }
}
