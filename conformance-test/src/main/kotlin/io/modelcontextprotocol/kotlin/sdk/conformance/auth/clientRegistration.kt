package io.modelcontextprotocol.kotlin.sdk.conformance.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class ClientCredentials(val clientId: String, val clientSecret: String?)

/**
 * Resolve client credentials per spec priority:
 * 1. Pre-registered (from MCP_CONFORMANCE_CONTEXT)
 * 2. CIMD (client_id_metadata_document_supported)
 * 3. Dynamic registration (registration_endpoint)
 * 4. Error
 */
internal suspend fun resolveClientCredentials(httpClient: HttpClient, asMetadata: JsonObject): ClientCredentials {
    // 1. Pre-registered
    val contextJson = System.getenv("MCP_CONFORMANCE_CONTEXT")
    if (contextJson != null) {
        val ctx = json.parseToJsonElement(contextJson).jsonObject
        val clientId = ctx["client_id"]?.jsonPrimitive?.content
        if (clientId != null) {
            val clientSecret = ctx["client_secret"]?.jsonPrimitive?.content
            return ClientCredentials(clientId, clientSecret)
        }
    }

    // 2. CIMD
    val cimdSupported = asMetadata["client_id_metadata_document_supported"]
        ?.jsonPrimitive?.content?.toBoolean() ?: false
    if (cimdSupported) {
        return ClientCredentials(CIMD_CLIENT_METADATA_URL, null)
    }

    // 3. Dynamic registration
    val registrationEndpoint = asMetadata["registration_endpoint"]?.jsonPrimitive?.content
    if (registrationEndpoint != null) {
        return dynamicClientRegistration(httpClient, registrationEndpoint)
    }

    error("No way to register client: no pre-registered credentials, CIMD not supported, and no registration_endpoint")
}

private suspend fun dynamicClientRegistration(
    httpClient: HttpClient,
    registrationEndpoint: String,
): ClientCredentials {
    val regBody = buildJsonObject {
        put("client_name", "test-auth-client")
        put("redirect_uris", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(CALLBACK_URL)) })
        put("grant_types", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("authorization_code")) })
        put("response_types", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("code")) })
        put("token_endpoint_auth_method", "client_secret_post")
    }

    val response = httpClient.post(registrationEndpoint) {
        contentType(ContentType.Application.Json)
        setBody(regBody.toString())
    }
    val regJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
    val clientId = regJson["client_id"]?.jsonPrimitive?.content ?: error("No client_id in registration response")
    val clientSecret = regJson["client_secret"]?.jsonPrimitive?.content
    return ClientCredentials(clientId, clientSecret)
}
