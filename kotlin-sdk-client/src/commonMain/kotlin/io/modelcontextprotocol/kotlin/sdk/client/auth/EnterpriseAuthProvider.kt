package io.modelcontextprotocol.kotlin.sdk.client.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.DEFAULT_PORT
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.util.AttributeKey
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Layer 3 implementation of Enterprise Managed Authorization (SEP-990).
 *
 * Integrates with any Ktor [HttpClient] as an [HttpClientPlugin]. When installed, it
 * intercepts every outgoing HTTP request via Ktor's [HttpSend] mechanism and:
 *
 * 1. Checks an in-memory access token cache.
 * 2. If the cache is empty or the token expires within a 30-second buffer, performs the
 *    full enterprise auth flow:
 *    a. Discovers the MCP authorization server metadata via RFC 8414.
 *    b. Invokes the [EnterpriseAuthProviderOptions.assertionCallback] to obtain a JWT
 *       Authorization Grant (ID-JAG) from the enterprise IdP.
 *    c. Exchanges the JAG for an OAuth 2.0 access token via RFC 7523.
 *    d. Caches the resulting token.
 * 3. Adds an `Authorization: Bearer {token}` header to the outgoing request.
 *
 * ## Usage as Ktor plugin (recommended)
 *
 * ```kotlin
 * val httpClient = HttpClient(CIO) {
 *     install(SSE)
 *     install(EnterpriseAuthProvider) {
 *         clientId = "my-mcp-client"
 *         assertionCallback = { ctx ->
 *             EnterpriseAuth.requestJwtAuthorizationGrant(
 *                 RequestJwtAuthGrantOptions(
 *                     tokenEndpoint = "https://idp.example.com/token",
 *                     idToken = myIdTokenSupplier(),
 *                     clientId = "my-idp-client",
 *                     audience = ctx.authorizationServerUrl,
 *                     resource = ctx.resourceUrl,
 *                 ),
 *                 authHttpClient,
 *             )
 *         }
 *     }
 * }
 *
 * val transport = StreamableHttpClientTransport(client = httpClient, url = serverUrl)
 * ```
 *
 * ## Cache invalidation
 *
 * ```kotlin
 * val provider = httpClient.plugin(EnterpriseAuthProvider)
 * provider.invalidateCache() // force full re-fetch on next request
 * ```
 *
 * ## Direct construction
 *
 * ```kotlin
 * val provider = EnterpriseAuthProvider(
 *     options = EnterpriseAuthProviderOptions(
 *         clientId = "my-client",
 *         assertionCallback = { ctx -> /* obtain JAG */ },
 *     ),
 *     authHttpClient = HttpClient(CIO),
 * )
 * ```
 *
 * @see EnterpriseAuth
 * @see EnterpriseAuthProviderOptions
 */
public class EnterpriseAuthProvider(
    private val options: EnterpriseAuthProviderOptions,
    private val authHttpClient: HttpClient = HttpClient(),
) {
    private val mutex = Mutex()

    private val cachedTokenRef = atomic<JwtBearerAccessTokenResponse?>(null)

    /**
     * Invalidates the cached access token, forcing the next request to perform a full
     * enterprise auth flow.
     *
     * Useful after receiving a `401 Unauthorized` response from the MCP server.
     */
    public fun invalidateCache() {
        logger.debug { "Invalidating cached enterprise auth token" }
        cachedTokenRef.value = null
    }

    internal suspend fun getAccessToken(requestUrl: Url): String {
        // Fast path: read without lock — avoids lock contention on the hot path
        val cached = cachedTokenRef.value
        if (cached != null && !isExpiredOrNearlyExpired(cached)) {
            logger.debug { "Using cached enterprise auth token" }
            return requireNotNull(cached.accessToken) { "Cached token has null accessToken" }
        }
        // Slow path: acquire lock, re-check, then fetch if still stale
        return mutex.withLock {
            val recheckCached = cachedTokenRef.value
            if (recheckCached != null && !isExpiredOrNearlyExpired(recheckCached)) {
                logger.debug { "Using cached enterprise auth token (after lock)" }
                return@withLock requireNotNull(recheckCached.accessToken) {
                    "Cached token has null accessToken"
                }
            }
            logger.debug { "Cached enterprise auth token absent or near-expiry; fetching new token" }
            val newToken = fetchNewToken(requestUrl)
            cachedTokenRef.value = newToken
            logger.debug {
                "Cached new enterprise auth token; expires_in=${newToken.expiresIn?.let { "${it}s" } ?: "unknown"}"
            }
            requireNotNull(newToken.accessToken) { "Fetched token has null accessToken" }
        }
    }

    private fun isExpiredOrNearlyExpired(token: JwtBearerAccessTokenResponse): Boolean {
        val expiresAt = token.expiresAt ?: return false
        return (expiresAt - EXPIRY_BUFFER).hasPassedNow()
    }

    private suspend fun fetchNewToken(requestUrl: Url): JwtBearerAccessTokenResponse {
        val resourceBaseUrl = deriveBaseUrl(requestUrl)
        logger.debug { "Discovering MCP authorization server for resource $resourceBaseUrl" }

        val metadata = EnterpriseAuth.discoverAuthServerMetadata(resourceBaseUrl, authHttpClient)
        val tokenEndpoint = metadata.tokenEndpoint
            ?: throw EnterpriseAuthException(
                "No token_endpoint in authorization server metadata for $resourceBaseUrl. " +
                    "Ensure the MCP server supports RFC 8414.",
            )

        // Prefer the issuer URI from metadata; fall back to the resource base URL
        val authServerUrl = if (!metadata.issuer.isNullOrBlank()) metadata.issuer else resourceBaseUrl

        val assertionContext = EnterpriseAuthAssertionContext(
            resourceUrl = resourceBaseUrl,
            authorizationServerUrl = authServerUrl,
        )
        logger.debug {
            "Invoking assertion callback for resourceUrl=$resourceBaseUrl, authServerUrl=$authServerUrl"
        }

        val assertion = options.assertionCallback(assertionContext)

        return EnterpriseAuth.exchangeJwtBearerGrant(
            ExchangeJwtBearerGrantOptions(
                tokenEndpoint = tokenEndpoint,
                assertion = assertion,
                clientId = options.clientId,
                clientSecret = options.clientSecret,
                scope = options.scope,
            ),
            authHttpClient,
        )
    }

    /**
     * Companion object that implements [HttpClientPlugin], allowing [EnterpriseAuthProvider]
     * to be installed directly on an [HttpClient] via `install(EnterpriseAuthProvider) { ... }`.
     */
    public companion object Plugin : HttpClientPlugin<Config, EnterpriseAuthProvider> {

        /** Proactive refresh buffer: re-fetch token this far before actual expiry. */
        private val EXPIRY_BUFFER = 30.seconds

        override val key: AttributeKey<EnterpriseAuthProvider> =
            AttributeKey("EnterpriseAuthProvider")

        override fun prepare(block: Config.() -> Unit): EnterpriseAuthProvider {
            val config = Config().apply(block)
            val options = EnterpriseAuthProviderOptions(
                clientId = requireNotNull(config.clientId) { "clientId must not be null" },
                assertionCallback = requireNotNull(config.assertionCallback) {
                    "assertionCallback must not be null"
                },
                clientSecret = config.clientSecret,
                scope = config.scope,
            )
            return EnterpriseAuthProvider(
                options = options,
                authHttpClient = config.authHttpClient ?: HttpClient(),
            )
        }

        override fun install(plugin: EnterpriseAuthProvider, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { request ->
                val token = plugin.getAccessToken(request.url.build())
                request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                execute(request)
            }
        }
    }

    /**
     * DSL configuration class for `install(EnterpriseAuthProvider) { ... }`.
     */
    public class Config {
        /** The OAuth 2.0 client ID registered at the MCP authorization server. Required. */
        public var clientId: String? = null

        /** The OAuth 2.0 client secret. Optional for public clients. */
        public var clientSecret: String? = null

        /** The `scope` parameter for the JWT bearer grant exchange. Optional. */
        public var scope: String? = null

        /**
         * Callback that obtains a JWT Authorization Grant (ID-JAG) for the given context.
         *
         * Receives an [EnterpriseAuthAssertionContext] describing the MCP resource and its
         * authorization server, and must return the assertion string (e.g., the result of
         * [EnterpriseAuth.requestJwtAuthorizationGrant]). Required.
         */
        public var assertionCallback: (suspend (EnterpriseAuthAssertionContext) -> String)? = null

        /**
         * HTTP client used for auth discovery and token exchange requests (separate from
         * the main MCP client). If not provided, a new [HttpClient] is created using
         * engine auto-detection.
         */
        public var authHttpClient: HttpClient? = null
    }
}

/**
 * Derives the scheme + host + port (if non-default) from the given [Url], stripping
 * any path, query, or fragment. This is the URL against which RFC 8414 discovery is performed.
 */
private fun deriveBaseUrl(url: Url): String = buildString {
    append(url.protocol.name)
    append("://")
    append(url.host)
    val port = url.port
    if (port != url.protocol.defaultPort) {
        append(':')
        append(port)
    }
}
