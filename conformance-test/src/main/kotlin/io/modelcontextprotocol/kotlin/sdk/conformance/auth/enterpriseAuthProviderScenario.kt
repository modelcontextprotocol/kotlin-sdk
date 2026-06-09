package io.modelcontextprotocol.kotlin.sdk.conformance.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.auth.DiscoverAndRequestJwtAuthGrantOptions
import io.modelcontextprotocol.kotlin.sdk.client.auth.EnterpriseAuth
import io.modelcontextprotocol.kotlin.sdk.client.auth.EnterpriseAuthProvider
import io.modelcontextprotocol.kotlin.sdk.client.auth.RequestJwtAuthGrantOptions
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation

/**
 * SEP-990 cross-app access flow exercised through [EnterpriseAuthProvider] as a Ktor plugin.
 *
 * Reads `client_id`, `client_secret`, `idp_id_token`, and `idp_token_endpoint` from
 * the conformance context. Installs [EnterpriseAuthProvider] on the MCP HTTP client so
 * that the plugin transparently handles:
 * - MCP authorization server discovery via RFC 8414
 * - JAG retrieval via [EnterpriseAuth.requestJwtAuthorizationGrant] (RFC 8693)
 * - JWT bearer grant exchange via [EnterpriseAuth.exchangeJwtBearerGrant] (RFC 7523)
 * - Access token caching and proactive refresh
 *
 * Exercises: [EnterpriseAuthProvider], [RequestJwtAuthGrantOptions],
 * [EnterpriseAuth.requestJwtAuthorizationGrant], [EnterpriseAuth.exchangeJwtBearerGrant].
 */
internal suspend fun runCrossAppAccessViaEnterpriseAuthProvider(serverUrl: String) {
    val ctx = conformanceContext()
    val clientId = ctx.requiredString("client_id")
    val clientSecret = ctx.requiredString("client_secret")
    val idpIdToken = ctx.requiredString("idp_id_token")
    val idpTokenEndpoint = ctx.requiredString("idp_token_endpoint")

    val authHttpClient = HttpClient(CIO) {
        install(SSE)
        followRedirects = false
    }

    val mcpHttpClient = HttpClient(CIO) {
        install(SSE)
        followRedirects = false
        install(EnterpriseAuthProvider) {
            this.clientId = clientId
            this.clientSecret = clientSecret
            this.authHttpClient = authHttpClient
            assertionCallback = { assertionCtx ->
                // Step 1 (RFC 8693): exchange the enterprise OIDC ID Token for a
                // JWT Authorization Grant (ID-JAG) at the enterprise IdP.
                EnterpriseAuth.requestJwtAuthorizationGrant(
                    RequestJwtAuthGrantOptions(
                        tokenEndpoint = idpTokenEndpoint,
                        idToken = idpIdToken,
                        clientId = clientId,
                        clientSecret = clientSecret,
                        audience = assertionCtx.authorizationServerUrl,
                        resource = assertionCtx.resourceUrl,
                    ),
                    authHttpClient,
                )
                // Step 2 (RFC 7523): EnterpriseAuthProvider handles the JWT bearer
                // grant exchange internally via EnterpriseAuth.exchangeJwtBearerGrant.
            }
        }
    }

    mcpHttpClient.use { client ->
        val transport = StreamableHttpClientTransport(client, serverUrl)
        val mcpClient = Client(
            clientInfo = Implementation("conformance-enterprise-auth-provider", "1.0.0"),
            options = ClientOptions(capabilities = ClientCapabilities()),
        )
        mcpClient.connect(transport)
        mcpClient.listTools()
        mcpClient.close()
    }
}

/**
 * SEP-990 cross-app access flow that exercises
 * [EnterpriseAuth.discoverAndRequestJwtAuthorizationGrant] inside the
 * [EnterpriseAuthProvider] assertion callback.
 *
 * The `idp_token_endpoint` from the conformance context is supplied as
 * [DiscoverAndRequestJwtAuthGrantOptions.idpTokenEndpoint], which skips the RFC 8414
 * discovery round-trip while still exercising the combined discover-and-request code path.
 *
 * Exercises: [EnterpriseAuth.discoverAndRequestJwtAuthorizationGrant],
 * [DiscoverAndRequestJwtAuthGrantOptions].
 */
internal suspend fun runCrossAppAccessViaDiscoverAndRequest(serverUrl: String) {
    val ctx = conformanceContext()
    val clientId = ctx.requiredString("client_id")
    val clientSecret = ctx.requiredString("client_secret")
    val idpIdToken = ctx.requiredString("idp_id_token")
    val idpTokenEndpoint = ctx.requiredString("idp_token_endpoint")

    val authHttpClient = HttpClient(CIO) {
        install(SSE)
        followRedirects = false
    }

    val mcpHttpClient = HttpClient(CIO) {
        install(SSE)
        followRedirects = false
        install(EnterpriseAuthProvider) {
            this.clientId = clientId
            this.clientSecret = clientSecret
            this.authHttpClient = authHttpClient
            assertionCallback = { assertionCtx ->
                // discoverAndRequestJwtAuthorizationGrant is called with idpTokenEndpoint
                // set explicitly so that RFC 8414 discovery is skipped; idpUrl is still
                // required by the type but unused when idpTokenEndpoint is non-null.
                EnterpriseAuth.discoverAndRequestJwtAuthorizationGrant(
                    DiscoverAndRequestJwtAuthGrantOptions(
                        idpUrl = extractOrigin(idpTokenEndpoint),
                        idpTokenEndpoint = idpTokenEndpoint,
                        idToken = idpIdToken,
                        clientId = clientId,
                        clientSecret = clientSecret,
                        audience = assertionCtx.authorizationServerUrl,
                        resource = assertionCtx.resourceUrl,
                    ),
                    authHttpClient,
                )
            }
        }
    }

    mcpHttpClient.use { client ->
        val transport = StreamableHttpClientTransport(client, serverUrl)
        val mcpClient = Client(
            clientInfo = Implementation("conformance-discover-and-request", "1.0.0"),
            options = ClientOptions(capabilities = ClientCapabilities()),
        )
        mcpClient.connect(transport)
        mcpClient.listTools()
        mcpClient.close()
    }
}
