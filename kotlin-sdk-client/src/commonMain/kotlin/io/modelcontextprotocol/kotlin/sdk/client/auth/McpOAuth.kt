package io.modelcontextprotocol.kotlin.sdk.client.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
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
public data class WwwAuthenticateChallenge(public val scheme: String, public val parameters: Map<String, String>) {
    /**
     * Returns a challenge parameter by name, using case-insensitive lookup.
     */
    public operator fun get(name: String): String? = parameters[name.lowercase()]
}

/**
 * PKCE material for an MCP OAuth authorization-code request.
 *
 * @property codeVerifier Secret verifier retained by the client until token exchange.
 * @property codeChallenge Public S256 challenge sent on the authorization request.
 * @property codeChallengeMethod PKCE challenge method. MCP uses `S256`.
 */
public data class McpOAuthPkce(
    public val codeVerifier: String,
    public val codeChallenge: String,
    public val codeChallengeMethod: String = "S256",
)

/**
 * Parameters for building an MCP OAuth authorization request URL.
 *
 * MCP clients must include the `resource` parameter when requesting tokens for
 * a protected MCP server.
 */
public data class McpOAuthAuthorizationRequest(
    public val authorizationEndpoint: String,
    public val clientId: String,
    public val redirectUri: String,
    public val codeChallenge: String,
    public val resource: String,
    public val scope: String? = null,
    public val state: String? = null,
)

/**
 * Returns the canonical origin for [url], excluding the default port.
 */
public fun mcpOAuthOrigin(url: String): String {
    val parsed = Url(url)
    val port = if (parsed.port == parsed.protocol.defaultPort) "" else ":${parsed.port}"
    return "${parsed.protocol.name}://${parsed.host}$port"
}

/**
 * Builds a PKCE code verifier from caller-supplied cryptographically secure random bytes.
 *
 * Pass 32 to 96 bytes to produce a verifier within the RFC 7636 length range.
 */
public fun mcpPkceCodeVerifier(randomBytes: ByteArray): String {
    require(randomBytes.size in 32..96) {
        "PKCE code verifier requires 32 to 96 random bytes"
    }
    return base64UrlNoPadding(randomBytes)
}

/**
 * Builds the PKCE S256 code challenge for [codeVerifier].
 */
public fun mcpPkceCodeChallengeS256(codeVerifier: String): String {
    requireValidPkceVerifier(codeVerifier)
    return base64UrlNoPadding(sha256(codeVerifier.encodeToByteArray()))
}

/**
 * Builds a complete PKCE S256 pair from caller-supplied cryptographically secure random bytes.
 */
public fun mcpPkceS256(randomBytes: ByteArray): McpOAuthPkce {
    val verifier = mcpPkceCodeVerifier(randomBytes)
    return McpOAuthPkce(
        codeVerifier = verifier,
        codeChallenge = mcpPkceCodeChallengeS256(verifier),
    )
}

/**
 * Ensures that authorization server metadata advertises PKCE S256 support.
 */
public fun requireMcpPkceS256Support(metadata: OAuthAuthorizationServerMetadata) {
    val methods = metadata.codeChallengeMethodsSupported
    if (methods == null || methods.none { it == "S256" }) {
        throw McpOAuthException(
            "Authorization server does not support PKCE S256 (code_challenge_methods_supported: $methods)",
        )
    }
}

/**
 * Builds an MCP OAuth authorization URL for an authorization-code request.
 */
public fun buildMcpOAuthAuthorizationUrl(request: McpOAuthAuthorizationRequest): String {
    val builder = URLBuilder(request.authorizationEndpoint)
    builder.parameters.append("response_type", "code")
    builder.parameters.append("client_id", request.clientId)
    builder.parameters.append("redirect_uri", request.redirectUri)
    builder.parameters.append("code_challenge", request.codeChallenge)
    builder.parameters.append("code_challenge_method", "S256")
    builder.parameters.append("resource", request.resource)
    request.state?.let { builder.parameters.append("state", it) }
    request.scope?.let { builder.parameters.append("scope", it) }
    return builder.buildString()
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

private suspend fun fetchFirstJsonObject(httpClient: HttpClient, urls: List<String>, description: String): JsonObject {
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

private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.content

private fun JsonObject.booleanOrNull(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

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

private fun requireValidPkceVerifier(codeVerifier: String) {
    require(codeVerifier.length in 43..128) {
        "PKCE code verifier must be 43 to 128 characters"
    }
    require(codeVerifier.all { it.isPkceVerifierChar() }) {
        "PKCE code verifier contains characters outside ALPHA / DIGIT / '-' / '.' / '_' / '~'"
    }
}

private fun Char.isPkceVerifierChar(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '-' || this == '.' || this == '_' || this == '~'

private fun base64UrlNoPadding(bytes: ByteArray): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val result = StringBuilder((bytes.size * 4 + 2) / 3)
    var index = 0
    while (index + 2 < bytes.size) {
        val block = ((bytes[index].toInt() and 0xff) shl 16) or
            ((bytes[index + 1].toInt() and 0xff) shl 8) or
            (bytes[index + 2].toInt() and 0xff)
        result.append(alphabet[(block ushr 18) and 0x3f])
        result.append(alphabet[(block ushr 12) and 0x3f])
        result.append(alphabet[(block ushr 6) and 0x3f])
        result.append(alphabet[block and 0x3f])
        index += 3
    }
    val remaining = bytes.size - index
    if (remaining == 1) {
        val block = (bytes[index].toInt() and 0xff) shl 16
        result.append(alphabet[(block ushr 18) and 0x3f])
        result.append(alphabet[(block ushr 12) and 0x3f])
    } else if (remaining == 2) {
        val block = ((bytes[index].toInt() and 0xff) shl 16) or
            ((bytes[index + 1].toInt() and 0xff) shl 8)
        result.append(alphabet[(block ushr 18) and 0x3f])
        result.append(alphabet[(block ushr 12) and 0x3f])
        result.append(alphabet[(block ushr 6) and 0x3f])
    }
    return result.toString()
}

private fun sha256(input: ByteArray): ByteArray {
    val bitLength = input.size.toLong() * 8
    val paddedLength = (((input.size + 9) + 63) / 64) * 64
    val padded = ByteArray(paddedLength)
    input.copyInto(padded)
    padded[input.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
    }

    var h0 = 0x6a09e667
    var h1 = 0xbb67ae85.toInt()
    var h2 = 0x3c6ef372
    var h3 = 0xa54ff53a.toInt()
    var h4 = 0x510e527f
    var h5 = 0x9b05688c.toInt()
    var h6 = 0x1f83d9ab
    var h7 = 0x5be0cd19
    val words = IntArray(64)

    for (chunkStart in padded.indices step 64) {
        for (i in 0 until 16) {
            val offset = chunkStart + i * 4
            words[i] = ((padded[offset].toInt() and 0xff) shl 24) or
                ((padded[offset + 1].toInt() and 0xff) shl 16) or
                ((padded[offset + 2].toInt() and 0xff) shl 8) or
                (padded[offset + 3].toInt() and 0xff)
        }
        for (i in 16 until 64) {
            val s0 = words[i - 15].rotateRight(7) xor words[i - 15].rotateRight(18) xor (words[i - 15] ushr 3)
            val s1 = words[i - 2].rotateRight(17) xor words[i - 2].rotateRight(19) xor (words[i - 2] ushr 10)
            words[i] = words[i - 16] + s0 + words[i - 7] + s1
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        var f = h5
        var g = h6
        var h = h7

        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + SHA256_K[i] + words[i]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj

            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        h5 += f
        h6 += g
        h7 += h
    }

    val digest = ByteArray(32)
    intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).forEachIndexed { index, value ->
        val offset = index * 4
        digest[offset] = (value ushr 24).toByte()
        digest[offset + 1] = (value ushr 16).toByte()
        digest[offset + 2] = (value ushr 8).toByte()
        digest[offset + 3] = value.toByte()
    }
    return digest
}

private fun Int.rotateRight(bitCount: Int): Int = (this ushr bitCount) or (this shl (32 - bitCount))

private val SHA256_K = intArrayOf(
    0x428a2f98,
    0x71374491,
    0xb5c0fbcf.toInt(),
    0xe9b5dba5.toInt(),
    0x3956c25b,
    0x59f111f1,
    0x923f82a4.toInt(),
    0xab1c5ed5.toInt(),
    0xd807aa98.toInt(),
    0x12835b01,
    0x243185be,
    0x550c7dc3,
    0x72be5d74,
    0x80deb1fe.toInt(),
    0x9bdc06a7.toInt(),
    0xc19bf174.toInt(),
    0xe49b69c1.toInt(),
    0xefbe4786.toInt(),
    0x0fc19dc6,
    0x240ca1cc,
    0x2de92c6f,
    0x4a7484aa,
    0x5cb0a9dc,
    0x76f988da,
    0x983e5152.toInt(),
    0xa831c66d.toInt(),
    0xb00327c8.toInt(),
    0xbf597fc7.toInt(),
    0xc6e00bf3.toInt(),
    0xd5a79147.toInt(),
    0x06ca6351,
    0x14292967,
    0x27b70a85,
    0x2e1b2138,
    0x4d2c6dfc,
    0x53380d13,
    0x650a7354,
    0x766a0abb,
    0x81c2c92e.toInt(),
    0x92722c85.toInt(),
    0xa2bfe8a1.toInt(),
    0xa81a664b.toInt(),
    0xc24b8b70.toInt(),
    0xc76c51a3.toInt(),
    0xd192e819.toInt(),
    0xd6990624.toInt(),
    0xf40e3585.toInt(),
    0x106aa070,
    0x19a4c116,
    0x1e376c08,
    0x2748774c,
    0x34b0bcb5,
    0x391c0cb3,
    0x4ed8aa4a,
    0x5b9cca4f,
    0x682e6ff3,
    0x748f82ee,
    0x78a5636f,
    0x84c87814.toInt(),
    0x8cc70208.toInt(),
    0x90befffa.toInt(),
    0xa4506ceb.toInt(),
    0xbef9a3f7.toInt(),
    0xc67178f2.toInt(),
)
