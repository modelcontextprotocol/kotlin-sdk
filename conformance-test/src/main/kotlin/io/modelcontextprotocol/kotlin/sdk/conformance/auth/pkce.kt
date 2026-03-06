package io.modelcontextprotocol.kotlin.sdk.conformance.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.Base64

internal fun generateCodeVerifier(): String {
    val bytes = ByteArray(32)
    java.security.SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

internal fun generateCodeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

/**
 * Verify that the AS metadata advertises S256 in code_challenge_methods_supported.
 * Abort if PKCE S256 is not supported.
 */
internal fun verifyPkceSupport(asMetadata: JsonObject) {
    val methods = asMetadata["code_challenge_methods_supported"]
        ?.jsonArray?.map { it.jsonPrimitive.content }
    require(methods != null && "S256" in methods) {
        "Authorization server does not support PKCE S256 (code_challenge_methods_supported: $methods)"
    }
}
