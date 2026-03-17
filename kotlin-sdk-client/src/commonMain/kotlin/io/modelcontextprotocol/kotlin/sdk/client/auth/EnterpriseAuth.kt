package io.modelcontextprotocol.kotlin.sdk.client.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.util.encodeBase64
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}

/**
 * Layer 2 utility object for the Enterprise Managed Authorization (SEP-990) flow.
 *
 * Provides standalone `suspend` functions for each discrete step of the two-step enterprise
 * auth protocol:
 *
 * 1. **Step 1 — JAG request:** Exchange an enterprise OIDC ID Token for a JWT
 *    Authorization Grant (ID-JAG) at the enterprise IdP via RFC 8693 token exchange.
 *    Use [requestJwtAuthorizationGrant] or [discoverAndRequestJwtAuthorizationGrant].
 *
 * 2. **Step 2 — access token exchange:** Exchange the ID-JAG for an OAuth 2.0 access
 *    token at the MCP authorization server via RFC 7523 JWT Bearer grant.
 *    Use [exchangeJwtBearerGrant].
 *
 * For a higher-level, stateful integration that handles both steps and caches the
 * resulting access token, use [EnterpriseAuthProvider] instead.
 *
 * All functions require a Ktor [HttpClient] to be provided by the caller. They do not
 * manage the lifecycle of that client.
 *
 * @see EnterpriseAuthProvider
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8414">RFC 8414 — Authorization Server Metadata</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 — Token Exchange</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523">RFC 7523 — JWT Bearer Grant</a>
 */
public object EnterpriseAuth {

    /**
     * Token type URI for OIDC ID tokens, used as the `subject_token_type` in the
     * RFC 8693 token exchange request.
     */
    public const val TOKEN_TYPE_ID_TOKEN: String = "urn:ietf:params:oauth:token-type:id_token"

    /**
     * Token type URI for JWT Authorization Grants (ID-JAG), used as the
     * `requested_token_type` in the token exchange request and validated as the
     * `issued_token_type` in the response.
     */
    public const val TOKEN_TYPE_ID_JAG: String = "urn:ietf:params:oauth:token-type:id-jag"

    /**
     * Grant type URI for RFC 8693 token exchange requests.
     */
    public const val GRANT_TYPE_TOKEN_EXCHANGE: String = "urn:ietf:params:oauth:grant-type:token-exchange"

    /**
     * Grant type URI for RFC 7523 JWT Bearer grant requests.
     */
    public const val GRANT_TYPE_JWT_BEARER: String = "urn:ietf:params:oauth:grant-type:jwt-bearer"

    private const val WELL_KNOWN_OAUTH: String = "/.well-known/oauth-authorization-server"
    private const val WELL_KNOWN_OPENID: String = "/.well-known/openid-configuration"

    /** URL-encodes [key] and [value] and joins them with `=`, using `+` for spaces. */
    private fun encodeParam(key: String, value: String): String =
        key.encodeURLParameter().replace("%20", "+") + "=" + value.encodeURLParameter().replace("%20", "+")

    // -----------------------------------------------------------------------
    // Authorization server discovery (RFC 8414)
    // -----------------------------------------------------------------------

    /**
     * Discovers the OAuth 2.0 authorization server metadata for the given base URL using
     * RFC 8414.
     *
     * First attempts to retrieve metadata from `{url}/.well-known/oauth-authorization-server`.
     * If that fails (non-200 response or network error), falls back to
     * `{url}/.well-known/openid-configuration`.
     *
     * @param url The base URL of the authorization or resource server (trailing slash is stripped).
     * @param httpClient The HTTP client to use for the discovery request.
     * @return The parsed [AuthServerMetadata].
     * @throws EnterpriseAuthException if both endpoints fail.
     */
    public suspend fun discoverAuthServerMetadata(url: String, httpClient: HttpClient): AuthServerMetadata {
        val baseUrl = url.trimEnd('/')
        val oauthUrl = "$baseUrl$WELL_KNOWN_OAUTH"
        val openIdUrl = "$baseUrl$WELL_KNOWN_OPENID"
        logger.debug { "Discovering authorization server metadata for $baseUrl" }
        return try {
            fetchAuthServerMetadata(oauthUrl, httpClient)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.debug { "OAuth discovery failed ($oauthUrl), falling back to OpenID configuration: ${e.message}" }
            fetchAuthServerMetadata(openIdUrl, httpClient)
        }
    }

    private suspend fun fetchAuthServerMetadata(url: String, httpClient: HttpClient): AuthServerMetadata {
        val response = httpClient.get(url) {
            headers { append(HttpHeaders.Accept, "application/json") }
        }
        if (response.status != HttpStatusCode.OK) {
            throw EnterpriseAuthException(
                "Failed to discover authorization server metadata from $url: HTTP ${response.status.value}",
            )
        }
        return try {
            val body = response.bodyAsText()
            val metadata = McpJson.decodeFromString<AuthServerMetadata>(body)
            logger.debug {
                "Discovered authorization server metadata from $url: " +
                    "issuer=${metadata.issuer}, tokenEndpoint=${metadata.tokenEndpoint}"
            }
            metadata
        } catch (e: EnterpriseAuthException) {
            throw e
        } catch (e: Exception) {
            throw EnterpriseAuthException("Failed to parse authorization server metadata from $url", e)
        }
    }

    // -----------------------------------------------------------------------
    // Step 1 — JAG request (RFC 8693 token exchange)
    // -----------------------------------------------------------------------

    /**
     * Requests a JWT Authorization Grant (ID-JAG) by performing an RFC 8693 token exchange
     * at the specified token endpoint.
     *
     * Exchanges the enterprise OIDC ID Token for an ID-JAG that can subsequently be
     * presented to the MCP authorization server via [exchangeJwtBearerGrant].
     *
     * Validates that the response `issued_token_type` equals [TOKEN_TYPE_ID_JAG].
     *
     * @param options Request parameters including the IdP token endpoint, ID Token, and
     *   client credentials.
     * @param httpClient The HTTP client to use.
     * @return The JAG string (the `access_token` value from the exchange response).
     * @throws EnterpriseAuthException on HTTP error, unexpected token types, or parse failure.
     */
    public suspend fun requestJwtAuthorizationGrant(
        options: RequestJwtAuthGrantOptions,
        httpClient: HttpClient,
    ): String {
        val params = buildList {
            add("grant_type" to GRANT_TYPE_TOKEN_EXCHANGE)
            add("subject_token" to options.idToken)
            add("subject_token_type" to TOKEN_TYPE_ID_TOKEN)
            add("requested_token_type" to TOKEN_TYPE_ID_JAG)
            add("client_id" to options.clientId)
            options.clientSecret?.let { add("client_secret" to it) }
            options.audience?.let { add("audience" to it) }
            options.resource?.let { add("resource" to it) }
            options.scope?.let { add("scope" to it) }
        }
        val body = params.joinToString("&") { (k, v) -> encodeParam(k, v) }

        logger.debug { "Requesting JAG token exchange at ${options.tokenEndpoint}" }

        val response = httpClient.post(options.tokenEndpoint) {
            contentType(ContentType.Application.FormUrlEncoded)
            headers { append(HttpHeaders.Accept, "application/json") }
            setBody(body)
        }

        if (response.status != HttpStatusCode.OK) {
            throw EnterpriseAuthException(
                "JAG token exchange failed: HTTP ${response.status.value} - ${response.bodyAsText()}",
            )
        }

        return try {
            val tokenResponse = McpJson.decodeFromString<JagTokenExchangeResponse>(response.bodyAsText())

            validateJAGTokenExchangeResponse(tokenResponse)

            logger.debug { "JAG token exchange successful" }
            tokenResponse.accessToken!!
        } catch (e: EnterpriseAuthException) {
            throw e
        } catch (e: Exception) {
            throw EnterpriseAuthException("Failed to parse JAG token exchange response", e)
        }
    }

    private fun validateJAGTokenExchangeResponse(tokenResponse: JagTokenExchangeResponse) {
        if (!TOKEN_TYPE_ID_JAG.equals(tokenResponse.issuedTokenType, ignoreCase = true)) {
            throw EnterpriseAuthException(
                "Unexpected issued_token_type in JAG response: " +
                    "${tokenResponse.issuedTokenType} (expected $TOKEN_TYPE_ID_JAG)",
            )
        }
        // token_type is informational per RFC 8693 §2.2.1; not strictly validated
        // because some conformant IdPs omit or vary the field.
        if (tokenResponse.accessToken.isNullOrBlank()) {
            throw EnterpriseAuthException("JAG token exchange response is missing access_token")
        }
    }

    /**
     * Discovers the enterprise IdP's token endpoint via RFC 8414, then requests a JAG via
     * RFC 8693 token exchange.
     *
     * If [DiscoverAndRequestJwtAuthGrantOptions.idpTokenEndpoint] is set, the discovery
     * step is skipped and the provided endpoint is used directly.
     *
     * @param options Request parameters including the IdP base URL (for discovery), ID Token,
     *   and client credentials.
     * @param httpClient The HTTP client to use.
     * @return The JAG string.
     * @throws EnterpriseAuthException on discovery or exchange failure.
     */
    public suspend fun discoverAndRequestJwtAuthorizationGrant(
        options: DiscoverAndRequestJwtAuthGrantOptions,
        httpClient: HttpClient,
    ): String {
        val tokenEndpoint = if (options.idpTokenEndpoint != null) {
            // Caller has already performed RFC 8414 discovery (or knows the endpoint ahead of time);
            // skip the discovery round-trip.
            options.idpTokenEndpoint
        } else {
            val metadata = discoverAuthServerMetadata(options.idpUrl, httpClient)
            metadata.tokenEndpoint
                ?: throw EnterpriseAuthException(
                    "No token_endpoint in IdP metadata at ${options.idpUrl}. " +
                        "Ensure the IdP supports RFC 8414.",
                )
        }

        return requestJwtAuthorizationGrant(
            RequestJwtAuthGrantOptions(
                tokenEndpoint = tokenEndpoint,
                idToken = options.idToken,
                clientId = options.clientId,
                clientSecret = options.clientSecret,
                audience = options.audience,
                resource = options.resource,
                scope = options.scope,
            ),
            httpClient,
        )
    }

    // -----------------------------------------------------------------------
    // Step 2 — JWT Bearer grant exchange (RFC 7523)
    // -----------------------------------------------------------------------

    /**
     * Exchanges a JWT Authorization Grant (ID-JAG) for an OAuth 2.0 access token at the
     * MCP authorization server's token endpoint using RFC 7523.
     *
     * The returned [JwtBearerAccessTokenResponse] includes the access token and, if the
     * server provided an `expires_in` value, an absolute [JwtBearerAccessTokenResponse.expiresAt]
     * timestamp computed from [kotlin.time.TimeSource.Monotonic].
     *
     * @param options Request parameters including the MCP auth server token endpoint, JAG
     *   assertion, and client credentials.
     * @param httpClient The HTTP client to use.
     * @return The [JwtBearerAccessTokenResponse].
     * @throws EnterpriseAuthException on HTTP error, missing `access_token`, or parse failure.
     */
    public suspend fun exchangeJwtBearerGrant(
        options: ExchangeJwtBearerGrantOptions,
        httpClient: HttpClient,
    ): JwtBearerAccessTokenResponse {
        val params = buildList {
            add("grant_type" to GRANT_TYPE_JWT_BEARER)
            add("assertion" to options.assertion)
            options.scope?.let { add("scope" to it) }
        }
        val body = params.joinToString("&") { (k, v) -> encodeParam(k, v) }

        // Client credentials are sent using client_secret_basic (RFC 6749 §2.3.1):
        // clientId and clientSecret are Base64-encoded and sent in the Authorization: Basic header.
        val credentials = "${options.clientId}:${options.clientSecret ?: ""}"
        val basicAuth = credentials.encodeToByteArray().encodeBase64()

        logger.debug { "Exchanging JWT bearer grant at ${options.tokenEndpoint}" }

        val response = httpClient.post(options.tokenEndpoint) {
            contentType(ContentType.Application.FormUrlEncoded)
            headers {
                append(HttpHeaders.Accept, "application/json")
                append(HttpHeaders.Authorization, "Basic $basicAuth")
            }
            setBody(body)
        }

        if (response.status != HttpStatusCode.OK) {
            throw EnterpriseAuthException(
                "JWT bearer grant exchange failed: HTTP ${response.status.value} - ${response.bodyAsText()}",
            )
        }

        return try {
            val tokenResponse = McpJson.decodeFromString<JwtBearerAccessTokenResponse>(response.bodyAsText())

            if (tokenResponse.accessToken.isNullOrBlank()) {
                throw EnterpriseAuthException("JWT bearer grant exchange response is missing access_token")
            }

            // RFC 7523 is a stateless grant — no refresh token is expected or used.
            // If the AS returns one, we intentionally ignore it: using it would bypass
            // re-validation of the user's identity with the IdP and undermine
            // session / revocation policies.

            // Compute absolute expiry from relative expires_in using a monotonic clock
            tokenResponse.expiresIn?.let { expiresIn ->
                tokenResponse.expiresAt = TimeSource.Monotonic.markNow() + expiresIn.seconds
            }

            logger.debug { "JWT bearer grant exchange successful; expires_in=${tokenResponse.expiresIn}" }
            tokenResponse
        } catch (e: EnterpriseAuthException) {
            throw e
        } catch (e: Exception) {
            throw EnterpriseAuthException("Failed to parse JWT bearer grant exchange response", e)
        }
    }
}
