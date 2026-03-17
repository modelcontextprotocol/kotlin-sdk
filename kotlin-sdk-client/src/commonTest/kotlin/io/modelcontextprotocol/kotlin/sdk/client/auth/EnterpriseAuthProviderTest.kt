package io.modelcontextprotocol.kotlin.sdk.client.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [EnterpriseAuthProvider] — the Ktor plugin that handles the full enterprise
 * auth flow, caching, and header injection.
 */
class EnterpriseAuthProviderTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private val jsonContentTypeHeader = headersOf(HttpHeaders.ContentType, "application/json")

    private fun MockRequestHandleScope.jsonOk(body: String) =
        respond(body, HttpStatusCode.OK, jsonContentTypeHeader)

    private fun MockRequestHandleScope.serverError() =
        respond("Internal Server Error", HttpStatusCode.InternalServerError)

    /** Builds a standard mock auth engine (discovery + token exchange) for provider tests. */
    private fun buildMockAuthEngine(
        tokenEndpointCallTracker: MutableList<Int> = mutableListOf(),
        accessTokenValue: String = "mcp-access-token",
    ) = MockEngine { request ->
        when (request.url.encodedPath) {
            "/.well-known/oauth-authorization-server" ->
                jsonOk("""{"issuer":"https://auth.example.com","token_endpoint":"https://auth.example.com/token"}""")
            "/token" -> {
                tokenEndpointCallTracker.add(tokenEndpointCallTracker.size + 1)
                jsonOk("""{"access_token":"$accessTokenValue","token_type":"Bearer","expires_in":3600}""")
            }
            else -> error("Unexpected auth request: ${request.url}")
        }
    }

    // -----------------------------------------------------------------------
    // Plugin integration — header injection and token caching
    // -----------------------------------------------------------------------

    @Test
    fun `provider injects Authorization Bearer header into request`() = runTest {
        val capturedAuth = mutableListOf<String>()
        val mcpClient = HttpClient(MockEngine { request ->
            capturedAuth += request.headers[HttpHeaders.Authorization] ?: ""
            respond("OK", HttpStatusCode.OK)
        }) {
            install(EnterpriseAuthProvider) {
                clientId = "test-client"
                assertionCallback = { _ -> "test-jag" }
                authHttpClient = HttpClient(buildMockAuthEngine())
            }
        }

        mcpClient.get("http://mcp.example.com/messages")

        capturedAuth shouldBe listOf("Bearer mcp-access-token")
    }

    @Test
    fun `provider caches token across multiple requests`() = runTest {
        val tokenEndpointCalls = mutableListOf<Int>()
        val mcpClient = HttpClient(MockEngine { respond("OK", HttpStatusCode.OK) }) {
            install(EnterpriseAuthProvider) {
                clientId = "client"
                assertionCallback = { _ -> "jag" }
                authHttpClient = HttpClient(buildMockAuthEngine(tokenEndpointCalls))
            }
        }

        mcpClient.get("http://mcp.example.com/messages")
        mcpClient.get("http://mcp.example.com/messages")
        mcpClient.get("http://mcp.example.com/messages")

        tokenEndpointCalls.size shouldBe 1
    }

    @Test
    fun `invalidateCache forces re-fetch on next request`() = runTest {
        var tokenCounter = 0
        val capturedAuth = mutableListOf<String>()

        val authEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/.well-known/oauth-authorization-server" ->
                    jsonOk("""{"token_endpoint":"https://auth.example.com/token"}""")
                "/token" -> {
                    tokenCounter++
                    jsonOk("""{"access_token":"token-$tokenCounter","token_type":"Bearer","expires_in":3600}""")
                }
                else -> error("Unexpected: ${request.url}")
            }
        }

        val mcpClient = HttpClient(MockEngine { request ->
            capturedAuth += request.headers[HttpHeaders.Authorization] ?: ""
            respond("OK", HttpStatusCode.OK)
        }) {
            install(EnterpriseAuthProvider) {
                clientId = "client"
                assertionCallback = { _ -> "jag" }
                authHttpClient = HttpClient(authEngine)
            }
        }

        mcpClient.get("http://mcp.example.com/messages") // fetches token-1

        val provider = mcpClient.plugin(EnterpriseAuthProvider)
        provider.invalidateCache()

        mcpClient.get("http://mcp.example.com/messages") // fetches token-2

        tokenCounter shouldBe 2
        capturedAuth[0] shouldBe "Bearer token-1"
        capturedAuth[1] shouldBe "Bearer token-2"
    }

    // -----------------------------------------------------------------------
    // Plugin integration — error propagation
    // -----------------------------------------------------------------------

    @Test
    fun `provider discovery failure propagates as EnterpriseAuthException`() = runTest {
        val authEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/.well-known/oauth-authorization-server" -> serverError()
                "/.well-known/openid-configuration" -> serverError()
                else -> error("Unexpected: ${request.url}")
            }
        }
        val mcpClient = HttpClient(MockEngine { respond("OK", HttpStatusCode.OK) }) {
            install(EnterpriseAuthProvider) {
                clientId = "client"
                assertionCallback = { _ -> "jag" }
                authHttpClient = HttpClient(authEngine)
            }
        }

        shouldThrow<EnterpriseAuthException> {
            mcpClient.get("http://mcp.example.com/messages")
        }
    }

    @Test
    fun `provider assertion callback error propagates`() = runTest {
        val authEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/.well-known/oauth-authorization-server" ->
                    jsonOk("""{"token_endpoint":"https://auth.example.com/token"}""")
                else -> error("Unexpected: ${request.url}")
            }
        }
        val mcpClient = HttpClient(MockEngine { respond("OK", HttpStatusCode.OK) }) {
            install(EnterpriseAuthProvider) {
                clientId = "client"
                assertionCallback = { _ -> throw RuntimeException("callback failed") }
                authHttpClient = HttpClient(authEngine)
            }
        }

        shouldThrow<RuntimeException> {
            mcpClient.get("http://mcp.example.com/messages")
        }.message shouldBe "callback failed"
    }

    // -----------------------------------------------------------------------
    // Plugin integration — assertion context
    // -----------------------------------------------------------------------

    @Test
    fun `provider passes correct resourceUrl and authorizationServerUrl to callback`() = runTest {
        var capturedContext: EnterpriseAuthAssertionContext? = null

        val authEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/.well-known/oauth-authorization-server" ->
                    jsonOk("""{"issuer":"https://auth.example.com","token_endpoint":"https://auth.example.com/token"}""")
                "/token" ->
                    jsonOk("""{"access_token":"token","token_type":"Bearer","expires_in":3600}""")
                else -> error("Unexpected: ${request.url}")
            }
        }
        val mcpClient = HttpClient(MockEngine { respond("OK", HttpStatusCode.OK) }) {
            install(EnterpriseAuthProvider) {
                clientId = "client"
                assertionCallback = { ctx ->
                    capturedContext = ctx
                    "jag"
                }
                authHttpClient = HttpClient(authEngine)
            }
        }

        mcpClient.get("http://mcp.example.com/messages")

        capturedContext shouldNotBe null
        capturedContext!!.resourceUrl shouldBe "http://mcp.example.com"
        capturedContext!!.authorizationServerUrl shouldBe "https://auth.example.com"
    }

    // -----------------------------------------------------------------------
    // EnterpriseAuthProvider.prepare — options validation
    // -----------------------------------------------------------------------

    @Test
    fun `prepare throws when clientId is null`() {
        shouldThrow<IllegalArgumentException> {
            EnterpriseAuthProvider.prepare {
                assertionCallback = { _ -> "jag" }
                authHttpClient = HttpClient(MockEngine { respond("OK", HttpStatusCode.OK) })
                // clientId not set
            }
        }.message shouldContain "clientId"
    }

    @Test
    fun `prepare throws when assertionCallback is null`() {
        shouldThrow<IllegalArgumentException> {
            EnterpriseAuthProvider.prepare {
                clientId = "my-client"
                authHttpClient = HttpClient(MockEngine { respond("OK", HttpStatusCode.OK) })
                // assertionCallback not set
            }
        }.message shouldContain "assertionCallback"
    }

    @Test
    fun `provider throws when assertionCallback returns blank string`() = runTest {
        val authEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/.well-known/oauth-authorization-server" ->
                    jsonOk("""{"token_endpoint":"https://auth.example.com/token"}""")
                else -> error("Unexpected: ${request.url}")
            }
        }
        val mcpClient = HttpClient(MockEngine { respond("OK", HttpStatusCode.OK) }) {
            install(EnterpriseAuthProvider) {
                clientId = "client"
                assertionCallback = { _ -> "   " } // blank
                authHttpClient = HttpClient(authEngine)
            }
        }
        shouldThrow<IllegalArgumentException> {
            mcpClient.get("http://mcp.example.com/messages")
        }.message shouldContain "blank"
    }

    @Test
    fun `provider token without expiresIn is cached indefinitely`() = runTest {
        // When the server omits expires_in the token has no expiry and is kept
        // in cache indefinitely (until explicitly invalidated).
        val tokenEndpointCalls = mutableListOf<Int>()
        val authEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/.well-known/oauth-authorization-server" ->
                    jsonOk("""{"token_endpoint":"https://auth.example.com/token"}""")
                "/token" -> {
                    tokenEndpointCalls.add(tokenEndpointCalls.size + 1)
                    jsonOk("""{"access_token":"no-expiry-token","token_type":"Bearer"}""")
                }
                else -> error("Unexpected: ${request.url}")
            }
        }
        val mcpClient = HttpClient(MockEngine { respond("OK", HttpStatusCode.OK) }) {
            install(EnterpriseAuthProvider) {
                clientId = "client"
                assertionCallback = { _ -> "jag" }
                authHttpClient = HttpClient(authEngine)
            }
        }

        mcpClient.get("http://mcp.example.com/messages")
        mcpClient.get("http://mcp.example.com/messages")
        mcpClient.get("http://mcp.example.com/messages")

        tokenEndpointCalls.size shouldBe 1
    }

    @Test
    fun `provider respects custom expiryBuffer`() = runTest {
        val tokenEndpointCalls = mutableListOf<Int>()
        val authEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/.well-known/oauth-authorization-server" ->
                    jsonOk("""{"token_endpoint":"https://auth.example.com/token"}""")
                "/token" -> {
                    tokenEndpointCalls.add(tokenEndpointCalls.size + 1)
                    // expires_in=1 with a 0s buffer — token should be cached until truly expired
                    jsonOk("""{"access_token":"token","token_type":"Bearer","expires_in":1}""")
                }
                else -> error("Unexpected: ${request.url}")
            }
        }
        val mcpClient = HttpClient(MockEngine { respond("OK", HttpStatusCode.OK) }) {
            install(EnterpriseAuthProvider) {
                clientId = "client"
                assertionCallback = { _ -> "jag" }
                authHttpClient = HttpClient(authEngine)
                expiryBuffer = 0.seconds // no buffer — token is valid until it actually expires
            }
        }
        // Both requests should reuse the cached token (it hasn't actually expired yet)
        mcpClient.get("http://mcp.example.com/messages")
        mcpClient.get("http://mcp.example.com/messages")

        tokenEndpointCalls.size shouldBe 1
    }

    // -----------------------------------------------------------------------
    // Full end-to-end via plugin — header propagated correctly
    // -----------------------------------------------------------------------

    @Test
    fun `full flow via plugin - Authorization header set and token is reused`() = runTest {
        val authEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/.well-known/oauth-authorization-server" ->
                    jsonOk("""{"token_endpoint":"https://idp.example.com/token"}""")
                "/token" ->
                    jsonOk("""{"access_token":"final-access-token","token_type":"Bearer","expires_in":3600}""")
                else -> error("Unexpected: ${request.url}")
            }
        }

        val capturedAuthHeaders = mutableListOf<String>()
        val mcpEngine = MockEngine { request ->
            capturedAuthHeaders += request.headers[HttpHeaders.Authorization] ?: ""
            respond("OK", HttpStatusCode.OK)
        }

        val mcpClient = HttpClient(mcpEngine) {
            install(EnterpriseAuthProvider) {
                clientId = "mcp-client"
                assertionCallback = { _ -> "enterprise-jag" }
                authHttpClient = HttpClient(authEngine)
            }
        }

        val response1 = mcpClient.get("http://mcp.example.com/sse")
        val response2 = mcpClient.get("http://mcp.example.com/sse")

        response1.bodyAsText() shouldBe "OK"
        response2.bodyAsText() shouldBe "OK"
        capturedAuthHeaders.all { it == "Bearer final-access-token" }.shouldBeTrue()
    }
}
