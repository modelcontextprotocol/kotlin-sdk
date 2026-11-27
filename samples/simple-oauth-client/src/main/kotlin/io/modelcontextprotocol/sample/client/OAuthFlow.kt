package io.modelcontextprotocol.sample.client

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val json = Json { ignoreUnknownKeys = true }

/**
 * Client credentials obtained from the authorization server.
 *
 * A [clientSecret] is only present when the server issued one during dynamic registration.
 */
data class ClientCredentials(val clientId: String, val clientSecret: String? = null)

/**
 * Runs the MCP authorization flow (OAuth 2.1 authorization code grant with PKCE) for a protected MCP server.
 *
 * The steps follow the MCP authorization specification:
 * 1. Discover the protected resource metadata (RFC 9728), using the `resource_metadata` hint from the
 *    server's `WWW-Authenticate` header when present.
 * 2. Discover the authorization server metadata (RFC 8414, with OpenID Connect discovery as a fallback).
 * 3. Register the client dynamically (RFC 7591), unless [preRegistered] credentials are given.
 * 4. Send the user to the authorization endpoint in the browser and receive the code on a loopback
 *    redirect URI.
 * 5. Exchange the code for an access token, binding it to the MCP server with the `resource` parameter (RFC 8707).
 *
 * @param httpClient client used for discovery, registration and token requests. It must not carry
 *        the interceptor that triggers this flow, to avoid recursion.
 * @param openBrowser opens the authorization URL for the user.
 * @param preRegistered client credentials registered with the authorization server ahead of time.
 * @param callbackPort port for the loopback redirect URI; `0` picks a free port.
 * @param timeout how long to wait for the user to finish signing in.
 */
class OAuthFlow(
    private val httpClient: HttpClient,
    private val openBrowser: (String) -> Unit,
    private val preRegistered: ClientCredentials? = null,
    private val callbackPort: Int = 0,
    private val timeout: Duration = 5.minutes,
) {

    /**
     * Authorizes against the MCP server at [serverUrl] and returns an access token.
     *
     * @param wwwAuthenticate the `WWW-Authenticate` header of the `401` response, if any.
     */
    suspend fun authorize(serverUrl: String, wwwAuthenticate: String?): String {
        val resourceMetadata = fetchResourceMetadata(serverUrl, wwwAuthenticate?.authParam("resource_metadata"))
        val resource = resourceMetadata["resource"]?.jsonPrimitive?.content ?: serverUrl
        require(serverUrl.trimEnd('/').startsWith(resource.trimEnd('/'))) {
            "Protected resource metadata is for '$resource', not '$serverUrl'"
        }

        val issuer = resourceMetadata["authorization_servers"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?: origin(serverUrl)
        val authServer = fetchAuthorizationServerMetadata(issuer)
        val authorizationEndpoint = authServer.string("authorization_endpoint")
            ?: error("Authorization server metadata has no authorization_endpoint")
        val tokenEndpoint = authServer.string("token_endpoint")
            ?: error("Authorization server metadata has no token_endpoint")
        val challengeMethods = authServer["code_challenge_methods_supported"]
            ?.jsonArray
            ?.map { it.jsonPrimitive.content }
        require(challengeMethods != null && "S256" in challengeMethods) {
            "Authorization server does not support PKCE with S256"
        }

        // Prefer the scope the server asked for, then everything the resource supports.
        val scope = wwwAuthenticate?.authParam("scope")
            ?: resourceMetadata["scopes_supported"]?.jsonArray?.joinToString(" ") { it.jsonPrimitive.content }

        LoopbackCallbackServer(callbackPort).use { callback ->
            val redirectUri = callback.redirectUri
            val credentials = preRegistered
                ?: registerClient(
                    authServer.string("registration_endpoint")
                        ?: error("Authorization server has no registration_endpoint; pass pre-registered credentials"),
                    redirectUri,
                    scope,
                )

            val verifier = randomUrlSafeString()
            val state = randomUrlSafeString()
            val authorizationUrl = URLBuilder(authorizationEndpoint).apply {
                parameters.append("response_type", "code")
                parameters.append("client_id", credentials.clientId)
                parameters.append("redirect_uri", redirectUri)
                parameters.append("code_challenge", codeChallenge(verifier))
                parameters.append("code_challenge_method", "S256")
                parameters.append("state", state)
                parameters.append("resource", resource)
                if (scope != null) parameters.append("scope", scope)
            }.buildString()

            openBrowser(authorizationUrl)
            val code = callback.awaitCode(expectedState = state, timeout = timeout)
            return exchangeCode(tokenEndpoint, code, verifier, redirectUri, resource, credentials)
        }
    }

    private suspend fun fetchResourceMetadata(serverUrl: String, hintedUrl: String?): JsonObject {
        val path = URI(serverUrl).path.trimEnd('/')
        val candidates = listOfNotNull(
            hintedUrl,
            "${origin(serverUrl)}/.well-known/oauth-protected-resource$path",
            "${origin(serverUrl)}/.well-known/oauth-protected-resource",
        ).distinct()
        return firstJson(candidates) ?: error("No protected resource metadata found at $candidates")
    }

    private suspend fun fetchAuthorizationServerMetadata(issuer: String): JsonObject {
        val path = URI(issuer).path.trimEnd('/')
        val origin = origin(issuer)
        val candidates = listOf(
            "$origin/.well-known/oauth-authorization-server$path",
            "$origin/.well-known/openid-configuration$path",
            "${issuer.trimEnd('/')}/.well-known/openid-configuration",
        ).distinct()
        return firstJson(candidates) ?: error("No authorization server metadata found at $candidates")
    }

    private suspend fun firstJson(urls: List<String>): JsonObject? {
        for (url in urls) {
            val response = httpClient.get(url)
            if (response.status.isSuccess()) return response.jsonObject()
        }
        return null
    }

    private suspend fun registerClient(endpoint: String, redirectUri: String, scope: String?): ClientCredentials {
        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("client_name", "Kotlin MCP SDK OAuth sample")
                    putJsonArray("redirect_uris") { add(redirectUri) }
                    putJsonArray("grant_types") {
                        add("authorization_code")
                        add("refresh_token")
                    }
                    putJsonArray("response_types") { add("code") }
                    // A command-line app can't keep a secret, so it registers as a public client.
                    put("token_endpoint_auth_method", "none")
                    if (scope != null) put("scope", scope)
                }.toString(),
            )
        }
        check(response.status.isSuccess()) { "Client registration failed: ${response.status} ${response.bodyAsText()}" }
        val body = response.jsonObject()
        return ClientCredentials(
            clientId = body.string("client_id") ?: error("Registration response has no client_id"),
            clientSecret = body.string("client_secret"),
        )
    }

    private suspend fun exchangeCode(
        tokenEndpoint: String,
        code: String,
        verifier: String,
        redirectUri: String,
        resource: String,
        credentials: ClientCredentials,
    ): String {
        val response = httpClient.submitForm(
            url = tokenEndpoint,
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("code_verifier", verifier)
                append("redirect_uri", redirectUri)
                append("resource", resource)
                append("client_id", credentials.clientId)
                credentials.clientSecret?.let { append("client_secret", it) }
            },
        )
        check(response.status.isSuccess()) { "Token request failed: ${response.status} ${response.bodyAsText()}" }
        return response.jsonObject().string("access_token") ?: error("Token response has no access_token")
    }
}

internal fun codeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

private fun randomUrlSafeString(): String {
    val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun origin(url: String): String {
    val uri = URI(url)
    return "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
}

/** Reads a parameter such as `resource_metadata="..."` from a `WWW-Authenticate: Bearer ...` header. */
internal fun String.authParam(name: String): String? =
    Regex("""(?:^|[\s,])$name="?([^",]*)"?""", RegexOption.IGNORE_CASE).find(this)?.groupValues?.get(1)

private suspend fun HttpResponse.jsonObject(): JsonObject = json.parseToJsonElement(bodyAsText()).jsonObject

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.content
