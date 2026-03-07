package io.modelcontextprotocol.kotlin.sdk.conformance.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

internal suspend fun runAuthClient(serverUrl: String) {
    val httpClient = HttpClient(CIO) {
        install(SSE)
        followRedirects = false
    }

    var accessToken: String? = null
    var authAttempts = 0
    // Cache discovery and credentials across retries
    var cachedDiscovery: DiscoveryResult? = null
    var cachedCredentials: ClientCredentials? = null

    httpClient.plugin(HttpSend).intercept { request ->
        // Add existing token if available
        if (accessToken != null) {
            request.headers.remove(HttpHeaders.Authorization)
            request.headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        val response = execute(request)
        val status = response.response.status

        // Determine if we need to (re-)authorize
        val needsAuth = status == HttpStatusCode.Unauthorized
        val wwwAuth = response.response.headers[HttpHeaders.WWWAuthenticate] ?: ""
        val stepUpScope = if (status == HttpStatusCode.Forbidden) parseStepUpScope(wwwAuth) else null
        val needsStepUp = stepUpScope != null

        if ((needsAuth || needsStepUp) && authAttempts < 3) {
            authAttempts++

            // Discover metadata (cache across retries)
            if (cachedDiscovery == null) {
                val resourceMetadataUrl = extractParam(wwwAuth, "resource_metadata")
                cachedDiscovery = discoverOAuthMetadata(httpClient, serverUrl, resourceMetadataUrl)
            }
            val discovery = cachedDiscovery
            val metadata = discovery.asMetadata

            val authEndpoint = metadata["authorization_endpoint"]?.jsonPrimitive?.content
                ?: error("No authorization_endpoint in metadata")
            val tokenEndpoint = metadata["token_endpoint"]?.jsonPrimitive?.content
                ?: error("No token_endpoint in metadata")

            val tokenEndpointAuthMethods = metadata["token_endpoint_auth_methods_supported"]
                ?.jsonArray?.map { it.jsonPrimitive.content }
                ?: listOf("client_secret_post")
            val tokenAuthMethod = tokenEndpointAuthMethods.firstOrNull() ?: "client_secret_post"

            // Verify PKCE support
            verifyPkceSupport(metadata)

            // Resolve client credentials (cache across retries)
            if (cachedCredentials == null) {
                cachedCredentials = resolveClientCredentials(httpClient, metadata)
            }
            val creds = cachedCredentials

            // Determine scope
            val scope = if (needsStepUp) {
                stepUpScope
            } else {
                val wwwAuthScope = extractParam(wwwAuth, "scope")
                selectScope(wwwAuthScope, discovery.scopesSupported)
            }

            // PKCE
            val codeVerifier = generateCodeVerifier()
            val codeChallenge = generateCodeChallenge(codeVerifier)

            // CSRF state parameter
            val state = UUID.randomUUID().toString()

            // Build authorization URL
            val authUrl = buildAuthorizationUrl(
                authEndpoint,
                creds.clientId,
                CALLBACK_URL,
                codeChallenge,
                scope,
                discovery.resourceUrl,
                state,
            )

            // Follow the authorization redirect to get auth code
            val authCode = followAuthorizationRedirect(httpClient, authUrl, CALLBACK_URL, state)

            // Exchange code for tokens
            accessToken = exchangeCodeForTokens(
                httpClient,
                tokenEndpoint,
                authCode,
                creds.clientId,
                creds.clientSecret,
                CALLBACK_URL,
                codeVerifier,
                tokenAuthMethod,
                discovery.resourceUrl,
            )

            // Retry the original request with the token
            request.headers.remove(HttpHeaders.Authorization)
            request.headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
            execute(request)
        } else {
            response
        }
    }

    httpClient.use { client ->
        val transport = StreamableHttpClientTransport(client, serverUrl)
        val mcpClient = Client(
            clientInfo = Implementation("test-auth-client", "1.0.0"),
            options = ClientOptions(capabilities = ClientCapabilities()),
        )
        mcpClient.connect(transport)
        mcpClient.listTools()
        mcpClient.callTool(CallToolRequest(CallToolRequestParams(name = "test-tool")))
        mcpClient.close()
    }
}
