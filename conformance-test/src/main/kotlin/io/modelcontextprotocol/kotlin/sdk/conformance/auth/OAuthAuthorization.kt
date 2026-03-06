package io.modelcontextprotocol.kotlin.sdk.conformance.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import kotlin.uuid.ExperimentalUuidApi

// OAuth Authorization Code scenarios (shared handler)
@OptIn(ExperimentalUuidApi::class)
internal suspend fun runAuthClient(serverUrl: String) {
    val httpClient = HttpClient(CIO) {
        install(SSE)
        followRedirects = false
    }

    var accessToken: String? = null

    // Install interceptor that handles 401 by performing OAuth flow
    httpClient.plugin(HttpSend).intercept { request ->
        // Add existing token if available
        if (accessToken != null) {
            request.headers.remove(HttpHeaders.Authorization)
            request.headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        val response = execute(request)

        if (response.response.status == HttpStatusCode.Unauthorized) {
            // Parse WWW-Authenticate header
            val wwwAuth = response.response.headers[HttpHeaders.WWWAuthenticate] ?: ""
            val resourceMetadataUrl = extractParam(wwwAuth, "resource_metadata")
            val scope = extractParam(wwwAuth, "scope")

            // Discover OAuth metadata
            val (metadata, resourceUrl) = discoverOAuthMetadata(httpClient, serverUrl, resourceMetadataUrl)
            val authEndpoint = metadata["authorization_endpoint"]?.jsonPrimitive?.content
                ?: error("No authorization_endpoint in metadata")
            val tokenEndpoint = metadata["token_endpoint"]?.jsonPrimitive?.content
                ?: error("No token_endpoint in metadata")
            val registrationEndpoint = metadata["registration_endpoint"]?.jsonPrimitive?.content

            // Determine token endpoint auth method
            val tokenEndpointAuthMethods = metadata["token_endpoint_auth_methods_supported"]
                ?.jsonArray?.map { it.jsonPrimitive.content }
                ?: listOf("client_secret_post")
            val tokenAuthMethod = tokenEndpointAuthMethods.firstOrNull() ?: "client_secret_post"

            // Check for CIMD support
            val cimdSupported = metadata["client_id_metadata_document_supported"]
                ?.jsonPrimitive?.content?.toBoolean() ?: false

            var clientId: String
            var clientSecret: String? = null

            if (cimdSupported) {
                // Use client metadata URL as client_id
                clientId = CIMD_CLIENT_METADATA_URL
            } else if (registrationEndpoint != null) {
                // Dynamic client registration
                val regResult = dynamicClientRegistration(httpClient, registrationEndpoint)
                clientId = regResult.first
                clientSecret = regResult.second
            } else {
                // Pre-registration: use credentials from context
                val contextJson = System.getenv("MCP_CONFORMANCE_CONTEXT")
                if (contextJson != null) {
                    val ctx = json.parseToJsonElement(contextJson).jsonObject
                    clientId = ctx["client_id"]?.jsonPrimitive?.content
                        ?: error("No client_id in MCP_CONFORMANCE_CONTEXT")
                    clientSecret = ctx["client_secret"]?.jsonPrimitive?.content
                } else {
                    error("No way to register client: no registration_endpoint, CIMD not supported, and no context")
                }
            }

            // PKCE
            val codeVerifier = generateCodeVerifier()
            val codeChallenge = generateCodeChallenge(codeVerifier)

            // Build authorization URL
            val authUrl = buildAuthorizationUrl(
                authEndpoint,
                clientId,
                CALLBACK_URL,
                codeChallenge,
                scope,
                resourceUrl,
            )

            // Follow the authorization redirect to get auth code
            val authCode = followAuthorizationRedirect(httpClient, authUrl)

            // Exchange code for tokens
            accessToken = exchangeCodeForTokens(
                httpClient,
                tokenEndpoint,
                authCode,
                clientId,
                clientSecret,
                CALLBACK_URL,
                codeVerifier,
                tokenAuthMethod,
                resourceUrl,
            )

            // Retry the original request with the token
            request.headers.remove(HttpHeaders.Authorization)
            request.headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
            execute(request)
        } else {
            response
        }
    }

    httpClient.use { httpClient ->
        val transport = StreamableHttpClientTransport(httpClient, serverUrl)
        val client = Client(
            clientInfo = Implementation("test-auth-client", "1.0.0"),
            options = ClientOptions(capabilities = ClientCapabilities()),
        )
        client.connect(transport)
        client.listTools()
        client.callTool(CallToolRequest(CallToolRequestParams(name = "test-tool")))
        client.close()
    }
}

private fun extractParam(wwwAuth: String, param: String): String? {
    val regex = Regex("""$param="([^"]+)"""")
    return regex.find(wwwAuth)?.groupValues?.get(1)
}

private suspend fun discoverOAuthMetadata(
    httpClient: HttpClient,
    serverUrl: String,
    resourceMetadataUrl: String?,
): Pair<JsonObject, String?> {
    // First get resource metadata
    val resourceMeta = if (resourceMetadataUrl != null) {
        val resp = httpClient.get(resourceMetadataUrl)
        json.parseToJsonElement(resp.bodyAsText()).jsonObject
    } else {
        discoverResourceMetadata(httpClient, serverUrl)
    }

    val resourceUrl = resourceMeta["resource"]?.jsonPrimitive?.content
    val authServer = resourceMeta["authorization_servers"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content

    val oauthMeta = if (authServer != null) {
        fetchOAuthMetadata(httpClient, authServer)
    } else {
        // Fallback: try well-known on server URL origin
        val origin = URI(serverUrl).let { "${it.scheme}://${it.host}${if (it.port > 0) ":${it.port}" else ""}" }
        fetchOAuthMetadata(httpClient, origin)
    }
    return oauthMeta to resourceUrl
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun dynamicClientRegistration(
    httpClient: HttpClient,
    registrationEndpoint: String,
): Pair<String, String?> {
    val regBody = buildString {
        append("""{"client_name":"test-auth-client","redirect_uris":["$CALLBACK_URL"],""")
        append(""""grant_types":["authorization_code"],"response_types":["code"],""")
        append(""""token_endpoint_auth_method":"client_secret_post"}""")
    }

    val response = httpClient.post(registrationEndpoint) {
        contentType(ContentType.Application.Json)
        setBody(regBody)
    }
    val regJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
    val clientId = regJson["client_id"]?.jsonPrimitive?.content ?: error("No client_id in registration response")
    val clientSecret = regJson["client_secret"]?.jsonPrimitive?.content
    return clientId to clientSecret
}

@OptIn(ExperimentalUuidApi::class)
private fun generateCodeVerifier(): String {
    val bytes = ByteArray(32)
    java.security.SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun generateCodeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

private fun buildAuthorizationUrl(
    authEndpoint: String,
    clientId: String,
    redirectUri: String,
    codeChallenge: String,
    scope: String?,
    resource: String?,
): String {
    val params = buildString {
        append("response_type=code")
        append("&client_id=${URLEncoder.encode(clientId, "UTF-8")}")
        append("&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}")
        append("&code_challenge=${URLEncoder.encode(codeChallenge, "UTF-8")}")
        append("&code_challenge_method=S256")
        if (scope != null) {
            append("&scope=${URLEncoder.encode(scope, "UTF-8")}")
        }
        if (resource != null) {
            append("&resource=${URLEncoder.encode(resource, "UTF-8")}")
        }
    }
    return if (authEndpoint.contains("?")) "$authEndpoint&$params" else "$authEndpoint?$params"
}

private suspend fun followAuthorizationRedirect(httpClient: HttpClient, authUrl: String): String {
    val response = httpClient.get(authUrl)

    // If we got a redirect, extract code from Location header
    if (response.status == HttpStatusCode.Found ||
        response.status == HttpStatusCode.MovedPermanently ||
        response.status == HttpStatusCode.TemporaryRedirect ||
        response.status == HttpStatusCode.SeeOther
    ) {
        val location = response.headers[HttpHeaders.Location]
            ?: error("No Location header in redirect response")
        val uri = URI(location)
        val queryParams = uri.query?.split("&")?.associate {
            val (k, v) = it.split("=", limit = 2)
            k to java.net.URLDecoder.decode(v, "UTF-8")
        } ?: emptyMap()
        return queryParams["code"] ?: error("No code in redirect URL: $location")
    }

    error("Expected redirect from auth endpoint, got ${response.status}")
}

private suspend fun exchangeCodeForTokens(
    httpClient: HttpClient,
    tokenEndpoint: String,
    code: String,
    clientId: String,
    clientSecret: String?,
    redirectUri: String,
    codeVerifier: String,
    tokenAuthMethod: String,
    resource: String?,
): String {
    val response: HttpResponse = when (tokenAuthMethod) {
        "client_secret_basic" -> {
            val basicAuth = Base64.getEncoder()
                .encodeToString("$clientId:${clientSecret ?: ""}".toByteArray())
            httpClient.submitForm(
                url = tokenEndpoint,
                formParameters = Parameters.build {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", redirectUri)
                    append("code_verifier", codeVerifier)
                    if (resource != null) {
                        append("resource", resource)
                    }
                },
            ) {
                header(HttpHeaders.Authorization, "Basic $basicAuth")
            }
        }

        "none" -> {
            httpClient.submitForm(
                url = tokenEndpoint,
                formParameters = Parameters.build {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("client_id", clientId)
                    append("redirect_uri", redirectUri)
                    append("code_verifier", codeVerifier)
                    if (resource != null) {
                        append("resource", resource)
                    }
                },
            )
        }

        else -> {
            // client_secret_post (default)
            httpClient.submitForm(
                url = tokenEndpoint,
                formParameters = Parameters.build {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("client_id", clientId)
                    if (clientSecret != null) {
                        append("client_secret", clientSecret)
                    }
                    append("redirect_uri", redirectUri)
                    append("code_verifier", codeVerifier)
                    if (resource != null) {
                        append("resource", resource)
                    }
                },
            )
        }
    }

    val tokenJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
    return tokenJson["access_token"]?.jsonPrimitive?.content
        ?: error("No access_token in token response: ${response.bodyAsText()}")
}
