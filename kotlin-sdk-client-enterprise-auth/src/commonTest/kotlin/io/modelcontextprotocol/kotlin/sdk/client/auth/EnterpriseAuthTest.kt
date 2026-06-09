package io.modelcontextprotocol.kotlin.sdk.client.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Tests for [EnterpriseAuth] — discovery, JAG request, and token exchange utilities.
 */
class EnterpriseAuthTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private val jsonContentTypeHeader = headersOf(HttpHeaders.ContentType, "application/json")

    private fun MockRequestHandleScope.jsonOk(body: String) =
        respond(body, HttpStatusCode.OK, jsonContentTypeHeader)

    private fun MockRequestHandleScope.serverError() =
        respond("Internal Server Error", HttpStatusCode.InternalServerError)

    /** Builds a mock [HttpClient] that dispatches by URL path. */
    private fun mockHttpClient(
        vararg handlers: Pair<String, MockRequestHandleScope.() -> HttpResponseData>,
    ): HttpClient {
        val handlerMap = handlers.toMap()
        return HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val handler = handlerMap[path] ?: error("Unexpected request to path: $path")
            handler()
        })
    }

    private fun requestBodyText(request: io.ktor.client.request.HttpRequestData): String =
        (request.body as TextContent).text

    // -----------------------------------------------------------------------
    // discoverAuthServerMetadata — success paths
    // -----------------------------------------------------------------------

    @Test
    fun `discoverAuthServerMetadata success via oauth well-known`() = runTest {
        val httpClient = mockHttpClient(
            "/.well-known/oauth-authorization-server" to {
                jsonOk(
                    """{"issuer":"https://auth.example.com","token_endpoint":"https://auth.example.com/token","authorization_endpoint":"https://auth.example.com/authorize"}""",
                )
            },
        )
        val metadata = EnterpriseAuth.discoverAuthServerMetadata("https://auth.example.com", httpClient)
        metadata.issuer shouldBe "https://auth.example.com"
        metadata.tokenEndpoint shouldBe "https://auth.example.com/token"
        metadata.authorizationEndpoint shouldBe "https://auth.example.com/authorize"
    }

    @Test
    fun `discoverAuthServerMetadata falls back to openid-configuration on 404`() = runTest {
        val httpClient = mockHttpClient(
            "/.well-known/oauth-authorization-server" to { respond("", HttpStatusCode.NotFound) },
            "/.well-known/openid-configuration" to {
                jsonOk("""{"issuer":"https://idp.example.com","token_endpoint":"https://idp.example.com/token"}""")
            },
        )
        val metadata = EnterpriseAuth.discoverAuthServerMetadata("https://idp.example.com", httpClient)
        metadata.tokenEndpoint shouldBe "https://idp.example.com/token"
    }

    @Test
    fun `discoverAuthServerMetadata falls back to openid-configuration on 500`() = runTest {
        val httpClient = mockHttpClient(
            "/.well-known/oauth-authorization-server" to { serverError() },
            "/.well-known/openid-configuration" to {
                jsonOk("""{"issuer":"https://idp.example.com","token_endpoint":"https://idp.example.com/token"}""")
            },
        )
        val metadata = EnterpriseAuth.discoverAuthServerMetadata("https://idp.example.com", httpClient)
        metadata.tokenEndpoint shouldBe "https://idp.example.com/token"
    }

    @Test
    fun `discoverAuthServerMetadata both endpoints fail throws EnterpriseAuthException`() = runTest {
        val httpClient = mockHttpClient(
            "/.well-known/oauth-authorization-server" to { serverError() },
            "/.well-known/openid-configuration" to { serverError() },
        )
        val ex = shouldThrow<EnterpriseAuthException> {
            EnterpriseAuth.discoverAuthServerMetadata("https://auth.example.com", httpClient)
        }
        ex.message shouldContain "HTTP 500"
    }

    @Test
    fun `discoverAuthServerMetadata strips trailing slash from url`() = runTest {
        val httpClient = mockHttpClient(
            "/.well-known/oauth-authorization-server" to {
                jsonOk("""{"issuer":"https://auth.example.com","token_endpoint":"https://auth.example.com/token"}""")
            },
        )
        val metadata = EnterpriseAuth.discoverAuthServerMetadata("https://auth.example.com/", httpClient)
        metadata.issuer shouldBe "https://auth.example.com"
    }

    // -----------------------------------------------------------------------
    // requestJwtAuthorizationGrant — success and validation
    // -----------------------------------------------------------------------

    @Test
    fun `requestJwtAuthorizationGrant success`() = runTest {
        val httpClient = HttpClient(MockEngine { request ->
            val body = requestBodyText(request)
            body shouldContain "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange"
            body shouldContain "subject_token=my-id-token"
            body shouldContain "subject_token_type=urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Aid_token"
            body shouldContain "requested_token_type=urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Aid-jag"
            body shouldContain "client_id=my-client"
            jsonOk(
                """{"access_token":"my-jag-token","issued_token_type":"urn:ietf:params:oauth:token-type:id-jag","token_type":"N_A"}""",
            )
        })

        val jag = EnterpriseAuth.requestJwtAuthorizationGrant(
            RequestJwtAuthGrantOptions(
                tokenEndpoint = "https://idp.example.com/token",
                idToken = "my-id-token",
                clientId = "my-client",
            ),
            httpClient,
        )
        jag shouldBe "my-jag-token"
    }

    @Test
    fun `requestJwtAuthorizationGrant includes optional params in body`() = runTest {
        val httpClient = HttpClient(MockEngine { request ->
            val body = requestBodyText(request)
            body shouldContain "client_secret=s3cr3t"
            body shouldContain "audience=my-audience"
            body shouldContain "resource=https%3A%2F%2Fmcp.example.com"
            body shouldContain "scope=read+write"
            jsonOk(
                """{"access_token":"jag","issued_token_type":"urn:ietf:params:oauth:token-type:id-jag","token_type":"N_A"}""",
            )
        })

        EnterpriseAuth.requestJwtAuthorizationGrant(
            RequestJwtAuthGrantOptions(
                tokenEndpoint = "https://idp.example.com/token",
                idToken = "id-token",
                clientId = "client",
                clientSecret = "s3cr3t",
                audience = "my-audience",
                resource = "https://mcp.example.com",
                scope = "read write",
            ),
            httpClient,
        ) shouldBe "jag"
    }

    @Test
    fun `requestJwtAuthorizationGrant wrong issued_token_type throws`() = runTest {
        val httpClient = mockHttpClient(
            "/token" to {
                jsonOk("""{"access_token":"jag","issued_token_type":"urn:wrong:type","token_type":"N_A"}""")
            },
        )
        val ex = shouldThrow<EnterpriseAuthException> {
            EnterpriseAuth.requestJwtAuthorizationGrant(
                RequestJwtAuthGrantOptions("https://idp.example.com/token", "id", "client"),
                httpClient,
            )
        }
        ex.message shouldContain "issued_token_type"
        ex.message shouldContain "urn:wrong:type"
    }

    @Test
    fun `requestJwtAuthorizationGrant nonStandardTokenType succeeds`() = runTest {
        // token_type is informational per RFC 8693 §2.2.1; non-N_A values must not be
        // rejected so that conformant IdPs that omit or vary the field are accepted.
        val httpClient = mockHttpClient(
            "/token" to {
                jsonOk(
                    """{"access_token":"jag","issued_token_type":"urn:ietf:params:oauth:token-type:id-jag","token_type":"Bearer"}""",
                )
            },
        )
        EnterpriseAuth.requestJwtAuthorizationGrant(
            RequestJwtAuthGrantOptions("https://idp.example.com/token", "id", "client"),
            httpClient,
        ) shouldBe "jag"
    }

    @Test
    fun `requestJwtAuthorizationGrant missing access_token throws`() = runTest {
        val httpClient = mockHttpClient(
            "/token" to {
                jsonOk("""{"issued_token_type":"urn:ietf:params:oauth:token-type:id-jag","token_type":"N_A"}""")
            },
        )
        val ex = shouldThrow<EnterpriseAuthException> {
            EnterpriseAuth.requestJwtAuthorizationGrant(
                RequestJwtAuthGrantOptions("https://idp.example.com/token", "id", "client"),
                httpClient,
            )
        }
        ex.message shouldContain "access_token"
    }

    @Test
    fun `requestJwtAuthorizationGrant HTTP error throws`() = runTest {
        val httpClient = mockHttpClient("/token" to { serverError() })
        val ex = shouldThrow<EnterpriseAuthException> {
            EnterpriseAuth.requestJwtAuthorizationGrant(
                RequestJwtAuthGrantOptions("https://idp.example.com/token", "id", "client"),
                httpClient,
            )
        }
        ex.message shouldContain "HTTP 500"
    }

    // -----------------------------------------------------------------------
    // discoverAndRequestJwtAuthorizationGrant
    // -----------------------------------------------------------------------

    @Test
    fun `discoverAndRequestJwtAuthorizationGrant full discovery flow`() = runTest {
        val httpClient = mockHttpClient(
            "/.well-known/oauth-authorization-server" to {
                jsonOk("""{"token_endpoint":"https://idp.example.com/token"}""")
            },
            "/token" to {
                jsonOk(
                    """{"access_token":"the-jag","issued_token_type":"urn:ietf:params:oauth:token-type:id-jag","token_type":"N_A"}""",
                )
            },
        )
        val jag = EnterpriseAuth.discoverAndRequestJwtAuthorizationGrant(
            DiscoverAndRequestJwtAuthGrantOptions(
                idpUrl = "https://idp.example.com",
                idToken = "my-id-token",
                clientId = "my-client",
            ),
            httpClient,
        )
        jag shouldBe "the-jag"
    }

    @Test
    fun `discoverAndRequestJwtAuthorizationGrant skips discovery when idpTokenEndpoint provided`() = runTest {
        var discoveryCallCount = 0
        val httpClient = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.contains(".well-known")) discoveryCallCount++
            jsonOk(
                """{"access_token":"the-jag","issued_token_type":"urn:ietf:params:oauth:token-type:id-jag","token_type":"N_A"}""",
            )
        })

        EnterpriseAuth.discoverAndRequestJwtAuthorizationGrant(
            DiscoverAndRequestJwtAuthGrantOptions(
                idpUrl = "https://idp.example.com",
                idToken = "my-id-token",
                clientId = "my-client",
                idpTokenEndpoint = "https://idp.example.com/custom-token",
            ),
            httpClient,
        ) shouldBe "the-jag"

        discoveryCallCount shouldBe 0
    }

    @Test
    fun `discoverAndRequestJwtAuthorizationGrant no token_endpoint in metadata throws`() = runTest {
        val httpClient = mockHttpClient(
            "/.well-known/oauth-authorization-server" to {
                jsonOk("""{"issuer":"https://idp.example.com"}""") // no token_endpoint
            },
        )
        val ex = shouldThrow<EnterpriseAuthException> {
            EnterpriseAuth.discoverAndRequestJwtAuthorizationGrant(
                DiscoverAndRequestJwtAuthGrantOptions(
                    idpUrl = "https://idp.example.com",
                    idToken = "id",
                    clientId = "client",
                ),
                httpClient,
            )
        }
        ex.message shouldContain "token_endpoint"
    }

    // -----------------------------------------------------------------------
    // exchangeJwtBearerGrant
    // -----------------------------------------------------------------------

    @Test
    fun `exchangeJwtBearerGrant success with expiresAt computed`() = runTest {
        val httpClient = HttpClient(MockEngine { request ->
            val body = requestBodyText(request)
            body shouldContain "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"
            body shouldContain "assertion=my-jag"
            // client credentials must be in Basic Auth header, NOT in the request body
            body shouldNotContain "client_id"
            val authHeader = request.headers[HttpHeaders.Authorization]
            authHeader shouldNotBe null
            authHeader!! shouldContain "Basic "
            val decoded = authHeader.removePrefix("Basic ").decodeBase64Bytes().decodeToString()
            decoded shouldBe "my-client:"
            jsonOk(
                """{"access_token":"access-token-123","token_type":"Bearer","expires_in":3600}""",
            )
        })

        val markBefore = TimeSource.Monotonic.markNow()
        val tokenResponse = EnterpriseAuth.exchangeJwtBearerGrant(
            ExchangeJwtBearerGrantOptions(
                tokenEndpoint = "https://auth.example.com/token",
                assertion = "my-jag",
                clientId = "my-client",
            ),
            httpClient,
        )

        tokenResponse.accessToken shouldBe "access-token-123"
        tokenResponse.tokenType shouldBe "Bearer"
        tokenResponse.expiresIn shouldBe 3600
        tokenResponse.expiresAt shouldNotBe null
        tokenResponse.isExpired().shouldBeFalse()

        // expiresAt should be approximately 1 hour (3600s) from now; at minimum 59 minutes away
        val fiftyNineMinutesFromNow = markBefore + 59.minutes
        tokenResponse.expiresAt!!.minus(fiftyNineMinutesFromNow).isPositive().shouldBeTrue()
    }

    @Test
    fun `exchangeJwtBearerGrant missing access_token throws`() = runTest {
        val httpClient = mockHttpClient(
            "/token" to { jsonOk("""{"token_type":"Bearer","expires_in":3600}""") },
        )
        val ex = shouldThrow<EnterpriseAuthException> {
            EnterpriseAuth.exchangeJwtBearerGrant(
                ExchangeJwtBearerGrantOptions("https://auth.example.com/token", "jag", "client"),
                httpClient,
            )
        }
        ex.message shouldContain "access_token"
    }

    @Test
    fun `exchangeJwtBearerGrant HTTP error throws`() = runTest {
        val httpClient = mockHttpClient("/token" to { serverError() })
        val ex = shouldThrow<EnterpriseAuthException> {
            EnterpriseAuth.exchangeJwtBearerGrant(
                ExchangeJwtBearerGrantOptions("https://auth.example.com/token", "jag", "client"),
                httpClient,
            )
        }
        ex.message shouldContain "HTTP 500"
    }

    // -----------------------------------------------------------------------
    // toString() — sensitive fields must be redacted
    // -----------------------------------------------------------------------

    @Test
    fun `RequestJwtAuthGrantOptions toString redacts clientSecret and idToken`() {
        val opts = RequestJwtAuthGrantOptions(
            tokenEndpoint = "https://idp.example.com/token",
            idToken = "super-secret-id-token",
            clientId = "my-client",
            clientSecret = "super-secret",
        )
        val str = opts.toString()
        str shouldContain "tokenEndpoint=https://idp.example.com/token"
        str shouldContain "clientId=my-client"
        str shouldContain "idToken=<redacted>"
        str shouldContain "clientSecret=<redacted>"
        str shouldNotContain "super-secret-id-token"
        str shouldNotContain "super-secret"
    }

    @Test
    fun `RequestJwtAuthGrantOptions toString shows null for absent clientSecret`() {
        val opts = RequestJwtAuthGrantOptions(
            tokenEndpoint = "https://idp.example.com/token",
            idToken = "tok",
            clientId = "client",
        )
        opts.toString() shouldContain "clientSecret=null"
    }

    @Test
    fun `DiscoverAndRequestJwtAuthGrantOptions toString redacts clientSecret and idToken`() {
        val opts = DiscoverAndRequestJwtAuthGrantOptions(
            idpUrl = "https://idp.example.com",
            idToken = "super-secret-id-token",
            clientId = "my-client",
            clientSecret = "super-secret",
        )
        val str = opts.toString()
        str shouldContain "idpUrl=https://idp.example.com"
        str shouldContain "clientId=my-client"
        str shouldContain "idToken=<redacted>"
        str shouldContain "clientSecret=<redacted>"
        str shouldNotContain "super-secret-id-token"
        str shouldNotContain "super-secret"
    }

    @Test
    fun `ExchangeJwtBearerGrantOptions toString redacts assertion and clientSecret`() {
        val opts = ExchangeJwtBearerGrantOptions(
            tokenEndpoint = "https://auth.example.com/token",
            assertion = "secret-jag-token",
            clientId = "my-client",
            clientSecret = "super-secret",
        )
        val str = opts.toString()
        str shouldContain "tokenEndpoint=https://auth.example.com/token"
        str shouldContain "clientId=my-client"
        str shouldContain "assertion=<redacted>"
        str shouldContain "clientSecret=<redacted>"
        str shouldNotContain "secret-jag-token"
        str shouldNotContain "super-secret"
    }

    // -----------------------------------------------------------------------
    // Serialization coverage — all fields of the response models
    // -----------------------------------------------------------------------

    @Test
    fun `JagTokenExchangeResponse deserializes scope and expiresIn`() = runTest {
        val httpClient = mockHttpClient(
            "/token" to {
                jsonOk(
                    """{"access_token":"jag","issued_token_type":"urn:ietf:params:oauth:token-type:id-jag","token_type":"N_A","scope":"read write","expires_in":600}""",
                )
            },
        )
        // Run through requestJwtAuthorizationGrant so the response model is exercised
        EnterpriseAuth.requestJwtAuthorizationGrant(
            RequestJwtAuthGrantOptions(
                tokenEndpoint = "https://idp.example.com/token",
                idToken = "id",
                clientId = "client",
            ),
            httpClient,
        ) shouldBe "jag"
    }

    @Test
    fun `AuthServerMetadata deserializes jwksUri`() = runTest {
        val httpClient = mockHttpClient(
            "/.well-known/oauth-authorization-server" to {
                jsonOk(
                    """{"issuer":"https://auth.example.com","token_endpoint":"https://auth.example.com/token","authorization_endpoint":"https://auth.example.com/authorize","jwks_uri":"https://auth.example.com/jwks"}""",
                )
            },
        )
        val metadata = EnterpriseAuth.discoverAuthServerMetadata("https://auth.example.com", httpClient)
        metadata.jwksUri shouldBe "https://auth.example.com/jwks"
    }

    @Test
    fun `JwtBearerAccessTokenResponse deserializes scope and refreshToken`() = runTest {
        val httpClient = mockHttpClient(
            "/token" to {
                jsonOk(
                    """{"access_token":"at","token_type":"Bearer","expires_in":3600,"scope":"mcp:read","refresh_token":"rt-ignored"}""",
                )
            },
        )
        val response = EnterpriseAuth.exchangeJwtBearerGrant(
            ExchangeJwtBearerGrantOptions(
                tokenEndpoint = "https://auth.example.com/token",
                assertion = "jag",
                clientId = "client",
            ),
            httpClient,
        )
        response.scope shouldBe "mcp:read"
        response.refreshToken shouldBe "rt-ignored"
    }

    // -----------------------------------------------------------------------
    // JwtBearerAccessTokenResponse.isExpired
    // -----------------------------------------------------------------------

    @Test
    fun `isExpired returns false when expiresAt is null`() {
        val response = JwtBearerAccessTokenResponse(accessToken = "token")
        response.isExpired().shouldBeFalse()
    }

    @Test
    fun `isExpired returns false when expiresAt is in the future`() {
        val response = JwtBearerAccessTokenResponse(accessToken = "token")
        response.expiresAt = TimeSource.Monotonic.markNow() + 3600.seconds
        response.isExpired().shouldBeFalse()
    }

    @Test
    fun `isExpired returns true when expiresAt has passed`() {
        val response = JwtBearerAccessTokenResponse(accessToken = "token")
        response.expiresAt = TimeSource.Monotonic.markNow() - 1.seconds
        response.isExpired().shouldBeTrue()
    }
}
