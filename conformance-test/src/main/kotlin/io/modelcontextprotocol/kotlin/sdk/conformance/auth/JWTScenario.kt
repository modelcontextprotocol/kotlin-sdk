package io.modelcontextprotocol.kotlin.sdk.conformance.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Client Credentials JWT scenario
internal suspend fun runClientCredentialsJwt(serverUrl: String) {
    val contextJson = System.getenv("MCP_CONFORMANCE_CONTEXT")
        ?: error("MCP_CONFORMANCE_CONTEXT not set")
    val ctx = json.parseToJsonElement(contextJson).jsonObject
    val clientId = ctx["client_id"]?.jsonPrimitive?.content ?: error("Missing client_id")
    val privateKeyPem = ctx["private_key_pem"]?.jsonPrimitive?.content ?: error("Missing private_key_pem")
    val signingAlgorithm = ctx["signing_algorithm"]?.jsonPrimitive?.content ?: "ES256"

    val httpClient = HttpClient(CIO) {
        install(SSE)
        followRedirects = false
    }

    httpClient.use { httpClient ->
        val tokenEndpoint = discoverTokenEndpoint(httpClient, serverUrl)

        // Create JWT client assertion
        val assertion = createJwtAssertion(clientId, tokenEndpoint, privateKeyPem, signingAlgorithm)

        // Exchange for token
        val tokenResponse = httpClient.submitForm(
            url = tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
                append("client_id", clientId)
                append("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                append("client_assertion", assertion)
            },
        )
        val accessToken = extractAccessToken(tokenResponse)

        withBearerToken(accessToken) { authedClient ->
            val transport = StreamableHttpClientTransport(authedClient, serverUrl)
            val client = Client(
                clientInfo = Implementation("conformance-client-credentials-jwt", "1.0.0"),
                options = ClientOptions(capabilities = ClientCapabilities()),
            )
            client.connect(transport)
            client.listTools()
            client.close()
        }
    }
}

// JWT Assertion
@OptIn(ExperimentalUuidApi::class)
private fun createJwtAssertion(
    clientId: String,
    tokenEndpoint: String,
    privateKeyPem: String,
    algorithm: String,
): String {
    val header = buildString {
        val alg = when (algorithm) {
            "ES256" -> "ES256"
            "RS256" -> "RS256"
            else -> algorithm
        }
        append("""{"alg":"$alg","typ":"JWT"}""")
    }

    val now = System.currentTimeMillis() / 1000
    val payload = buildString {
        append("""{"iss":"$clientId","sub":"$clientId","aud":"$tokenEndpoint",""")
        append(""""iat":$now,"exp":${now + 300},"jti":"${Uuid.random()}"}""")
    }

    val headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())
    val payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
    val signingInput = "$headerB64.$payloadB64"

    val signature = signJwt(signingInput, privateKeyPem, algorithm)
    return "$signingInput.$signature"
}

private fun signJwt(input: String, privateKeyPem: String, algorithm: String): String {
    val pemBody = privateKeyPem
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("-----BEGIN EC PRIVATE KEY-----", "")
        .replace("-----END EC PRIVATE KEY-----", "")
        .replace("-----BEGIN RSA PRIVATE KEY-----", "")
        .replace("-----END RSA PRIVATE KEY-----", "")
        .replace("\n", "")
        .replace("\r", "")
        .trim()

    val keyBytes = Base64.getDecoder().decode(pemBody)
    val keySpec = PKCS8EncodedKeySpec(keyBytes)

    val (keyAlgorithm, signatureAlgorithm) = when (algorithm) {
        "ES256" -> "EC" to "SHA256withECDSA"
        "RS256" -> "RSA" to "SHA256withRSA"
        else -> error("Unsupported signing algorithm: $algorithm")
    }

    val keyFactory = KeyFactory.getInstance(keyAlgorithm)
    val privateKey = keyFactory.generatePrivate(keySpec)

    val sig = Signature.getInstance(signatureAlgorithm)
    sig.initSign(privateKey)
    sig.update(input.toByteArray())
    val rawSignature = sig.sign()

    // For EC, convert DER to raw r||s format for JWS
    val signatureBytes = if (keyAlgorithm == "EC") {
        derToRawEcSignature(rawSignature)
    } else {
        rawSignature
    }

    return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)
}

private fun derToRawEcSignature(der: ByteArray): ByteArray {
    // DER format: 0x30 len 0x02 rLen r 0x02 sLen s
    var offset = 2 // skip SEQUENCE tag and length
    if (der[1].toInt() and 0x80 != 0) {
        offset += (der[1].toInt() and 0x7f)
    }

    // Read r
    check(der[offset] == 0x02.toByte()) { "Expected INTEGER tag for r" }
    offset++
    val rLen = der[offset].toInt() and 0xff
    offset++
    val r = der.copyOfRange(offset, offset + rLen)
    offset += rLen

    // Read s
    check(der[offset] == 0x02.toByte()) { "Expected INTEGER tag for s" }
    offset++
    val sLen = der[offset].toInt() and 0xff
    offset++
    val s = der.copyOfRange(offset, offset + sLen)

    // Each component should be 32 bytes for P-256
    val componentLen = 32
    val result = ByteArray(componentLen * 2)

    // Copy r (may need padding or trimming of leading zero)
    val rStart = if (r.size > componentLen) r.size - componentLen else 0
    val rDest = if (r.size < componentLen) componentLen - r.size else 0
    r.copyInto(result, rDest, rStart, r.size)

    // Copy s
    val sStart = if (s.size > componentLen) s.size - componentLen else 0
    val sDest = componentLen + if (s.size < componentLen) componentLen - s.size else 0
    s.copyInto(result, sDest, sStart, s.size)

    return result
}
