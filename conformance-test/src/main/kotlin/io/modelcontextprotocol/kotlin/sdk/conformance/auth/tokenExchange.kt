package io.modelcontextprotocol.kotlin.sdk.conformance.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

internal fun buildAuthorizationUrl(
    authEndpoint: String,
    clientId: String,
    redirectUri: String,
    codeChallenge: String,
    scope: String?,
    resource: String?,
    state: String,
): String {
    val params = buildString {
        append("response_type=code")
        append("&client_id=${URLEncoder.encode(clientId, "UTF-8")}")
        append("&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}")
        append("&code_challenge=${URLEncoder.encode(codeChallenge, "UTF-8")}")
        append("&code_challenge_method=S256")
        append("&state=${URLEncoder.encode(state, "UTF-8")}")
        if (scope != null) {
            append("&scope=${URLEncoder.encode(scope, "UTF-8")}")
        }
        if (resource != null) {
            append("&resource=${URLEncoder.encode(resource, "UTF-8")}")
        }
    }
    return if (authEndpoint.contains("?")) "$authEndpoint&$params" else "$authEndpoint?$params"
}

internal suspend fun followAuthorizationRedirect(
    httpClient: HttpClient,
    authUrl: String,
    expectedCallbackUrl: String,
    expectedState: String,
): String {
    val response = httpClient.get(authUrl)

    if (response.status == HttpStatusCode.Found ||
        response.status == HttpStatusCode.MovedPermanently ||
        response.status == HttpStatusCode.TemporaryRedirect ||
        response.status == HttpStatusCode.SeeOther
    ) {
        val location = response.headers[HttpHeaders.Location]
            ?: error("No Location header in redirect response")

        require(location.startsWith(expectedCallbackUrl)) {
            "Redirect location does not match expected callback URL"
        }

        val uri = URI(location)
        val queryParams = uri.query?.split("&")?.mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8") else null
        }?.toMap() ?: emptyMap()

        val returnedState = queryParams["state"]
        require(returnedState == expectedState) {
            "State parameter mismatch in authorization redirect"
        }

        return queryParams["code"] ?: error("No authorization code in redirect response")
    }

    error("Expected redirect from auth endpoint, got ${response.status}")
}

internal suspend fun exchangeCodeForTokens(
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
    val response = when (tokenAuthMethod) {
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

    // Check HTTP status
    if (!response.status.isSuccess()) {
        val body = response.bodyAsText()
        val errorDetail = try {
            val obj = json.parseToJsonElement(body).jsonObject
            val err = obj["error"]?.jsonPrimitive?.content ?: "unknown"
            val desc = obj["error_description"]?.jsonPrimitive?.content
            if (desc != null) "$err: $desc" else err
        } catch (_: Exception) {
            body
        }
        error("Token exchange failed (${response.status}): $errorDetail")
    }

    val tokenJson = json.parseToJsonElement(response.bodyAsText()).jsonObject

    // Check for error field in response body (some servers return 200 with error)
    val errorField = tokenJson["error"]?.jsonPrimitive?.content
    if (errorField != null) {
        val desc = tokenJson["error_description"]?.jsonPrimitive?.content
        error("Token exchange error: $errorField${if (desc != null) " - $desc" else ""}")
    }

    return tokenJson["access_token"]?.jsonPrimitive?.content
        ?: error("No access_token in token response")
}
