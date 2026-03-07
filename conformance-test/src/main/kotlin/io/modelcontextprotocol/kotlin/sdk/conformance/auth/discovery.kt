package io.modelcontextprotocol.kotlin.sdk.conformance.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class DiscoveryResult(
    val asMetadata: JsonObject,
    val resourceUrl: String?,
    val scopesSupported: List<String>?,
)

internal suspend fun discoverOAuthMetadata(
    httpClient: HttpClient,
    serverUrl: String,
    resourceMetadataUrl: String?,
): DiscoveryResult {
    // Get resource metadata
    val resourceMeta = if (resourceMetadataUrl != null) {
        val resp = httpClient.get(resourceMetadataUrl)
        if (!resp.status.isSuccess()) {
            error("Failed to fetch resource metadata from $resourceMetadataUrl: ${resp.status}")
        }
        json.parseToJsonElement(resp.bodyAsText()).jsonObject
    } else {
        discoverResourceMetadata(httpClient, serverUrl)
    }

    val resourceUrl = resourceMeta["resource"]?.jsonPrimitive?.content
    val scopesSupported = resourceMeta["scopes_supported"]
        ?.jsonArray?.map { it.jsonPrimitive.content }
    val authServer = resourceMeta["authorization_servers"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content

    val oauthMeta = if (authServer != null) {
        fetchOAuthMetadata(httpClient, authServer)
    } else {
        // Fallback: try well-known on server URL origin
        val origin = extractOrigin(serverUrl)
        fetchOAuthMetadata(httpClient, origin)
    }

    return DiscoveryResult(oauthMeta, resourceUrl, scopesSupported)
}
