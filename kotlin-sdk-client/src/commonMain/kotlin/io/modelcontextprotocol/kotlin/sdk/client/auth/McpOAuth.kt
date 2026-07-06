package io.modelcontextprotocol.kotlin.sdk.client.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * OAuth 2.0 Protected Resource Metadata for an MCP server.
 *
 * @property resource Canonical resource URI advertised by the protected MCP server.
 * @property authorizationServers Authorization server issuer URLs advertised by the resource server.
 * @property scopesSupported Scopes advertised by the protected resource metadata document.
 * @property raw Complete metadata document for fields not modeled by this SDK version.
 */
public data class OAuthProtectedResourceMetadata(
    public val resource: String? = null,
    public val authorizationServers: List<String> = emptyList(),
    public val scopesSupported: List<String>? = null,
    public val raw: JsonObject,
)

/**
 * OAuth Authorization Server Metadata discovered for an MCP authorization flow.
 *
 * @property issuer Authorization server issuer URL.
 * @property authorizationEndpoint OAuth authorization endpoint.
 * @property tokenEndpoint OAuth token endpoint.
 * @property registrationEndpoint Dynamic client registration endpoint, if supported.
 * @property tokenEndpointAuthMethodsSupported Supported token endpoint authentication methods.
 * @property codeChallengeMethodsSupported Supported PKCE challenge methods.
 * @property clientIdMetadataDocumentSupported Whether Client ID Metadata Documents are advertised.
 * @property raw Complete metadata document for fields not modeled by this SDK version.
 */
public data class OAuthAuthorizationServerMetadata(
    public val issuer: String? = null,
    public val authorizationEndpoint: String? = null,
    public val tokenEndpoint: String? = null,
    public val registrationEndpoint: String? = null,
    public val tokenEndpointAuthMethodsSupported: List<String>? = null,
    public val codeChallengeMethodsSupported: List<String>? = null,
    public val clientIdMetadataDocumentSupported: Boolean? = null,
    public val raw: JsonObject,
)

/**
 * Result of discovering MCP protected resource metadata and its authorization server metadata.
 */
public data class McpOAuthDiscoveryResult(
    public val resourceMetadata: OAuthProtectedResourceMetadata,
    public val authorizationServerMetadata: OAuthAuthorizationServerMetadata,
)

/**
 * Error raised when MCP OAuth discovery or parsing fails.
 */
public class McpOAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A parsed `WWW-Authenticate` challenge.
 *
 * @property scheme Authentication scheme, for example `Bearer`.
 * @property parameters Challenge parameters keyed by lower-case parameter name.
 */
public data class WwwAuthenticateChallenge(
    public val scheme: String,
    public val parameters: Map<String, String>,
) {
    /**
     * Returns a challenge parameter by name, using case-insensitive lookup.
     */
    public operator fun get(name: String): String? = parameters[name.lowercase()]
}

/**
 * Returns the canonical origin for [url], excluding the default port.
 */
public fun mcpOAuthOrigin(url: String): String {
    val parsed = Url(url)
    val port = if (parsed.port == parsed.protocol.defaultPort) "" else ":${parsed.port}"
    return "${parsed.protocol.name}://${parsed.host}$port"
}

/**
 * Returns protected resource metadata discovery URLs for [serverUrl] in MCP-specified priority order.
 */
public fun mcpProtectedResourceMetadataUrls(serverUrl: String): List<String> {
    val parsed = Url(serverUrl)
    val origin = mcpOAuthOrigin(serverUrl)
    val path = parsed.encodedPath.ifBlank { "/" }
    val pathSpecific = "$origin/.well-known/oauth-protected-resource${if (path == "/") "" else path}"
    val root = "$origin/.well-known/oauth-protected-resource"
    return listOf(pathSpecific, root).distinct()
}

/**
 * Returns OAuth Authorization Server Metadata discovery URLs for [authorizationServerUrl]
 * in MCP-specified priority order.
 */
public fun mcpAuthorizationServerMetadataUrls(authorizationServerUrl: String): List<String> {
    val parsed = Url(authorizationServerUrl)
    val origin = mcpOAuthOrigin(authorizationServerUrl)
    val path = parsed.encodedPath.ifBlank { "/" }
    val hasPath = path != "/"
    return buildList {
        add("$origin/.well-known/oauth-authorization-server${if (hasPath) path else ""}")
        add("$origin/.well-known/openid-configuration${if (hasPath) path else ""}")
        if (hasPath) {
            add("$origin$path/.well-known/openid-configuration")
        }
    }.distinct()
}

/**
 * Fetches OAuth 2.0 Protected Resource Metadata for [serverUrl].
 *
 * If [resourceMetadataUrl] was supplied in a `WWW-Authenticate` challenge, it is tried first
 * and no well-known fallback is attempted on failure.
 */
public suspend fun discoverMcpProtectedResourceMetadata(
    httpClient: HttpClient,
    serverUrl: String,
    resourceMetadataUrl: String? = null,
): OAuthProtectedResourceMetadata {
    val urls = resourceMetadataUrl?.let { listOf(it) } ?: mcpProtectedResourceMetadataUrls(serverUrl)
    val raw = fetchFirstJsonObject(httpClient, urls, "protected resource metadata")
    return raw.toOAuthProtectedResourceMetadata()
}

/**
 * Fetches OAuth Authorization Server Metadata for [authorizationServerUrl].
 */
public suspend fun discoverMcpAuthorizationServerMetadata(
    httpClient: HttpClient,
    authorizationServerUrl: String,
): OAuthAuthorizationServerMetadata {
    val raw = fetchFirstJsonObject(
        httpClient = httpClient,
        urls = mcpAuthorizationServerMetadataUrls(authorizationServerUrl),
        description = "authorization server metadata",
    )
    return raw.toOAuthAuthorizationServerMetadata()
}

/**
 * Discovers protected resource metadata and authorization server metadata for [serverUrl].
 */
public suspend fun discoverMcpOAuthMetadata(
    httpClient: HttpClient,
    serverUrl: String,
    resourceMetadataUrl: String? = null,
): McpOAuthDiscoveryResult {
    val resourceMetadata = discoverMcpProtectedResourceMetadata(httpClient, serverUrl, resourceMetadataUrl)
    val authorizationServer = resourceMetadata.authorizationServers.firstOrNull()
        ?: throw McpOAuthException("Protected resource metadata does not advertise authorization_servers")
    val authorizationServerMetadata = discoverMcpAuthorizationServerMetadata(httpClient, authorizationServer)
    return McpOAuthDiscoveryResult(resourceMetadata, authorizationServerMetadata)
}

/**
 * Parses one or more `WWW-Authenticate` challenges.
 */
public fun parseWwwAuthenticate(header: String): List<WwwAuthenticateChallenge> {
    val challenges = mutableListOf<WwwAuthenticateChallenge>()
    var index = 0
    while (index < header.length) {
        while (index < header.length && (header[index].isWhitespace() || header[index] == ',')) index++
        val schemeStart = index
        while (index < header.length && !header[index].isWhitespace() && header[index] != ',') index++
        if (schemeStart == index) break

        val scheme = header.substring(schemeStart, index)
        while (index < header.length && header[index].isWhitespace()) index++

        val paramsStart = index
        var inQuotes = false
        while (index < header.length) {
            val c = header[index]
            if (c == '"') inQuotes = !inQuotes
            if (!inQuotes && c == ',' && looksLikeChallengeStart(header, index + 1)) break
            index++
        }

        val params = parseChallengeParameters(header.substring(paramsStart, index))
        challenges += WwwAuthenticateChallenge(scheme = scheme, parameters = params)
        if (index < header.length && header[index] == ',') index++
    }
    return challenges
}

/**
 * Extracts a parameter from the first matching `WWW-Authenticate` challenge.
 */
public fun wwwAuthenticateParameter(header: String?, parameter: String, scheme: String = "Bearer"): String? {
    if (header == null) return null
    return parseWwwAuthenticate(header)
        .firstOrNull { it.scheme.equals(scheme, ignoreCase = true) }
        ?.get(parameter)
}

/**
 * Selects scopes for an MCP OAuth authorization request.
 *
 * The `scope` value from `WWW-Authenticate` is authoritative when present. If absent,
 * all `scopes_supported` values from Protected Resource Metadata are joined with spaces.
 * If neither is present, `null` is returned so callers can omit the `scope` parameter.
 */
public fun selectMcpOAuthScope(wwwAuthenticateScope: String?, scopesSupported: List<String>?): String? =
    wwwAuthenticateScope ?: scopesSupported?.takeIf { it.isNotEmpty() }?.joinToString(" ")

/**
 * Returns the requested step-up scope from an insufficient-scope challenge, if present.
 */
public fun mcpOAuthStepUpScope(wwwAuthenticate: String?): String? {
    val error = wwwAuthenticateParameter(wwwAuthenticate, "error")
    if (error != "insufficient_scope") return null
    return wwwAuthenticateParameter(wwwAuthenticate, "scope")
}

/**
 * Returns a request builder that applies a bearer token to every outgoing HTTP request.
 */
public fun mcpBearerAuth(accessToken: String): HttpRequestBuilder.() -> Unit = {
    headers.remove(HttpHeaders.Authorization)
    headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
}

private suspend fun fetchFirstJsonObject(
    httpClient: HttpClient,
    urls: List<String>,
    description: String,
): JsonObject {
    val failures = mutableListOf<String>()
    for (url in urls) {
        val response = httpClient.get(url)
        if (response.status.isSuccess()) {
            return try {
                McpJson.parseToJsonElement(response.bodyAsText()).jsonObject
            } catch (e: Exception) {
                throw McpOAuthException("Failed to parse $description from $url", e)
            }
        }
        failures += "$url (${response.status})"
    }
    throw McpOAuthException("Failed to fetch $description: ${failures.joinToString()}")
}

private fun JsonObject.toOAuthProtectedResourceMetadata(): OAuthProtectedResourceMetadata =
    OAuthProtectedResourceMetadata(
        resource = stringOrNull("resource"),
        authorizationServers = stringListOrNull("authorization_servers").orEmpty(),
        scopesSupported = stringListOrNull("scopes_supported"),
        raw = this,
    )

private fun JsonObject.toOAuthAuthorizationServerMetadata(): OAuthAuthorizationServerMetadata =
    OAuthAuthorizationServerMetadata(
        issuer = stringOrNull("issuer"),
        authorizationEndpoint = stringOrNull("authorization_endpoint"),
        tokenEndpoint = stringOrNull("token_endpoint"),
        registrationEndpoint = stringOrNull("registration_endpoint"),
        tokenEndpointAuthMethodsSupported = stringListOrNull("token_endpoint_auth_methods_supported"),
        codeChallengeMethodsSupported = stringListOrNull("code_challenge_methods_supported"),
        clientIdMetadataDocumentSupported = booleanOrNull("client_id_metadata_document_supported"),
        raw = this,
    )

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.content

private fun JsonObject.booleanOrNull(key: String): Boolean? =
    this[key]?.jsonPrimitive?.booleanOrNull

private fun JsonObject.stringListOrNull(key: String): List<String>? =
    this[key]?.jsonArray?.map { it.jsonPrimitive.content }

private fun looksLikeChallengeStart(value: String, startIndex: Int): Boolean {
    var index = startIndex
    while (index < value.length && value[index].isWhitespace()) index++
    if (index >= value.length) return false
    while (index < value.length && !value[index].isWhitespace() && value[index] != ',') {
        if (value[index] == '=') return false
        index++
    }
    while (index < value.length && value[index].isWhitespace()) index++
    return index >= value.length || value[index] != '='
}

private fun parseChallengeParameters(value: String): Map<String, String> {
    val params = mutableMapOf<String, String>()
    var index = 0
    while (index < value.length) {
        while (index < value.length && (value[index].isWhitespace() || value[index] == ',')) index++
        val keyStart = index
        while (index < value.length && value[index] != '=' && value[index] != ',') index++
        if (index >= value.length || value[index] != '=') break
        val key = value.substring(keyStart, index).trim().lowercase()
        index++

        val parsedValue = if (index < value.length && value[index] == '"') {
            index++
            val result = StringBuilder()
            while (index < value.length) {
                val c = value[index++]
                when {
                    c == '\\' && index < value.length -> result.append(value[index++])
                    c == '"' -> break
                    else -> result.append(c)
                }
            }
            result.toString()
        } else {
            val valueStart = index
            while (index < value.length && value[index] != ',') index++
            value.substring(valueStart, index).trim()
        }
        if (key.isNotEmpty()) params[key] = parsedValue
        while (index < value.length && value[index] != ',') index++
        if (index < value.length && value[index] == ',') index++
    }
    return params
}
