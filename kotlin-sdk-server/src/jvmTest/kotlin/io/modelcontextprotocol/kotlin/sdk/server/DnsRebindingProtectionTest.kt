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
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test

class DnsRebindingProtectionTest {

    companion object {
        @JvmStatic
        fun invalidHostCases(): List<Arguments> = listOf(
            Arguments.of("", "Invalid Host header: (malformed or missing)"),
            Arguments.of("evil.com", "Invalid Host: evil.com"),
        )

        @JvmStatic
        fun extractHostnameAcceptCases(): List<Arguments> = listOf(
            Arguments.of("localhost", "localhost"),
            Arguments.of("localhost:3000", "localhost"),
            Arguments.of("127.0.0.1:8080", "127.0.0.1"),
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
            "[::1", // malformed IPv6
            "[]", // empty brackets
            "[]:3000", // empty brackets + port
            " localhost", // leading whitespace
            "localhost ", // trailing whitespace
            "localhost\t:80", // embedded tab
            "localhost:abc", // non-numeric port
            "localhost:-1", // negative port
            "[::1]:abc", // non-numeric IPv6 port
            ":80", // leading colon
        )

        @JvmStatic
        fun extractOriginHostAcceptCases(): List<Arguments> = listOf(
            Arguments.of("http://example.com", "example.com"),
            Arguments.of("http://example.com:8080", "example.com"),
            Arguments.of("https://Example.COM", "Example.COM"),
            Arguments.of("https://example.com", "example.com"),
        )

        @JvmStatic
        fun extractOriginHostRejectCases(): List<String> = listOf(
            "example.com", // no scheme
            "not-a-url", // unparseable
            "", // empty
        )
    }

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

    @ParameterizedTest
    @ValueSource(strings = ["localhost", "127.0.0.1", "[::1]", "localhost:3000", "[::1]:8080"])
    fun `plugin accepts valid Host header`(hostHeader: String) = testWithPlugin {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, hostHeader)
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @ParameterizedTest
    @MethodSource("invalidHostCases")
    fun `plugin rejects invalid Host header`(hostHeader: String, expectedBody: String) = testWithPlugin {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, hostHeader)
        }
        response.shouldHaveStatus(HttpStatusCode.Forbidden)
        response.bodyAsText() shouldContain expectedBody
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
        response.bodyAsText() shouldContain "Invalid Origin host: evil.com"
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
    fun `plugin allows Origin with different port but same hostname`() = testWithPlugin(
        config = { allowedOrigins = listOf("http://localhost:3000") },
    ) {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Origin, "http://localhost:9999")
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @Test
    fun `plugin allows Origin with different scheme but same hostname`() = testWithPlugin(
        config = { allowedOrigins = listOf("http://localhost:3000") },
    ) {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Origin, "https://localhost:3000")
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.bodyAsText() shouldBe "ok"
    }

    @Test
    fun `plugin rejects unparseable Origin header`() = testWithPlugin(
        config = { allowedOrigins = listOf("http://localhost:3000") },
    ) {
        val response = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Origin, "not-a-url")
        }
        response.shouldHaveStatus(HttpStatusCode.Forbidden)
        response.bodyAsText() shouldContain "Invalid Origin header: (unparseable)"
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
        val localhostResponse = client.post("/mcp") {
            header(HttpHeaders.Host, "localhost")
        }
        localhostResponse.shouldHaveStatus(HttpStatusCode.Forbidden)
        localhostResponse.bodyAsText() shouldContain "Invalid Host: localhost"

        val otherResponse = client.post("/mcp") {
            header(HttpHeaders.Host, "myapp.com")
        }
        otherResponse.shouldHaveStatus(HttpStatusCode.Forbidden)
        otherResponse.bodyAsText() shouldContain "Invalid Host: myapp.com"
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
        // Not 403 — the request reaches the handler (may get 400 for missing sessionId, etc.)
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

    // -- extractOriginHost unit tests --

    @ParameterizedTest
    @MethodSource("extractOriginHostAcceptCases")
    fun `extractOriginHost extracts hostname`(input: String, expected: String) {
        extractOriginHost(input) shouldBe expected
    }

    @ParameterizedTest
    @MethodSource("extractOriginHostRejectCases")
    fun `extractOriginHost returns null for invalid origin`(input: String) {
        extractOriginHost(input) shouldBe null
    }

    private fun testServer(): Server = Server(
        serverInfo = Implementation(name = "test-server", version = "1.0.0"),
        options = ServerOptions(capabilities = ServerCapabilities()),
    )
}
