package io.modelcontextprotocol.kotlin.sdk.conformance.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import kotlin.text.ifEmpty

internal val json = Json { ignoreUnknownKeys = true }

internal const val CIMD_CLIENT_METADATA_URL = "https://conformance-test.local/client-metadata.json"
internal const val CALLBACK_URL = "http://localhost:3000/callback"

internal fun extractOrigin(url: String): String {
    val uri = URI(url)
    return "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
}

internal suspend fun discoverResourceMetadata(httpClient: HttpClient, serverUrl: String): JsonObject {
    val origin = extractOrigin(serverUrl)
    val path = URI(serverUrl).path.ifEmpty { "/" }

    // Try RFC 9728 format first: /.well-known/oauth-protected-resource/<path>
    val wellKnownUrl = "$origin/.well-known/oauth-protected-resource$path"
    val response = httpClient.get(wellKnownUrl)
    if (response.status.isSuccess()) {
        return json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    // Fallback: try root
    val fallbackUrl = "$origin/.well-known/oauth-protected-resource"
    val fallbackResponse = httpClient.get(fallbackUrl)
    return json.parseToJsonElement(fallbackResponse.bodyAsText()).jsonObject
}

internal suspend fun fetchOAuthMetadata(httpClient: HttpClient, authServerUrl: String): JsonObject {
    val origin = extractOrigin(authServerUrl)
    val path = URI(authServerUrl).path.ifEmpty { "/" }

    // RFC 8414 §3: /.well-known/oauth-authorization-server/<path>
    val oauthUrl = "$origin/.well-known/oauth-authorization-server$path"
    val oauthResponse = httpClient.get(oauthUrl)
    if (oauthResponse.status.isSuccess()) {
        return json.parseToJsonElement(oauthResponse.bodyAsText()).jsonObject
    }

    // OIDC Discovery with path insertion: /.well-known/openid-configuration/<path>
    val oidcPathUrl = "$origin/.well-known/openid-configuration$path"
    val oidcPathResponse = httpClient.get(oidcPathUrl)
    if (oidcPathResponse.status.isSuccess()) {
        return json.parseToJsonElement(oidcPathResponse.bodyAsText()).jsonObject
    }

    // Fallback: OpenID Connect discovery (issuer + /.well-known/openid-configuration)
    val oidcUrl = "$authServerUrl/.well-known/openid-configuration"
    val oidcResponse = httpClient.get(oidcUrl)
    if (oidcResponse.status.isSuccess()) {
        return json.parseToJsonElement(oidcResponse.bodyAsText()).jsonObject
    }

    error(
        "Failed to fetch OAuth metadata from $oauthUrl (${oauthResponse.status}) and $oidcUrl (${oidcResponse.status})",
    )
}

internal suspend fun discoverTokenEndpoint(httpClient: HttpClient, serverUrl: String): String {
    val resourceMetadata = discoverResourceMetadata(httpClient, serverUrl)
    val authServer = resourceMetadata["authorization_servers"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
        ?: error("No authorization_servers in resource metadata")

    val oauthMetadata = fetchOAuthMetadata(httpClient, authServer)
    return oauthMetadata["token_endpoint"]?.jsonPrimitive?.content
        ?: error("No token_endpoint")
}

internal suspend fun extractAccessToken(tokenResponse: HttpResponse): String {
    val tokenJson = json.parseToJsonElement(tokenResponse.bodyAsText()).jsonObject
    return tokenJson["access_token"]?.jsonPrimitive?.content
        ?: error("No access_token in token response")
}

internal suspend fun <T> withBearerToken(accessToken: String, block: suspend (HttpClient) -> T): T {
    val client = HttpClient(CIO) {
        install(SSE)
    }
    client.plugin(HttpSend).intercept { request ->
        request.headers.remove(HttpHeaders.Authorization)
        request.headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
        execute(request)
    }
    return client.use { block(it) }
}
