package io.modelcontextprotocol.kotlin.sdk.conformance.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

// SEP-990 Enterprise Managed OAuth: Cross-App Access complete flow
internal suspend fun runCrossAppAccess(serverUrl: String) {
    val ctx = conformanceContext()
    val clientId = ctx.requiredString("client_id")
    val clientSecret = ctx.requiredString("client_secret")
    val idpIdToken = ctx.requiredString("idp_id_token")
    val idpTokenEndpoint = ctx.requiredString("idp_token_endpoint")

    val httpClient = HttpClient(CIO) {
        install(SSE)
        followRedirects = false
    }

    httpClient.use { client ->
        // Discover PRM + AS metadata
        val resourceMeta = discoverResourceMetadata(client, serverUrl)
        val resourceUrl = resourceMeta["resource"]?.jsonPrimitive?.content
            ?: error("No resource in resource metadata")
        val authServer = resourceMeta["authorization_servers"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?: error("No authorization_servers in resource metadata")
        val asMeta = fetchOAuthMetadata(client, authServer)
        val tokenEndpoint = asMeta["token_endpoint"]?.jsonPrimitive?.content
            ?: error("No token_endpoint in AS metadata")

        // RFC 8693 Token Exchange at IDP: exchange ID token for ID-JAG
        val idpResponse = client.submitForm(
            url = idpTokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                append("subject_token", idpIdToken)
                append("subject_token_type", "urn:ietf:params:oauth:token-type:id_token")
                append("requested_token_type", "urn:ietf:params:oauth:token-type:id-jag")
                append("audience", authServer)
                append("resource", resourceUrl)
            },
        )
        val idJag = extractAccessToken(idpResponse)

        // RFC 7523 JWT Bearer Grant at AS with Basic auth
        val basicAuth = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
        val asResponse = client.submitForm(
            url = tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", idJag)
            },
        ) {
            header(HttpHeaders.Authorization, "Basic $basicAuth")
        }
        val accessToken = extractAccessToken(asResponse)

        // Use access token for MCP requests
        withBearerToken(accessToken) { authedClient ->
            val transport = StreamableHttpClientTransport(authedClient, serverUrl)
            val mcpClient = Client(
                clientInfo = Implementation("conformance-cross-app-access", "1.0.0"),
                options = ClientOptions(capabilities = ClientCapabilities()),
            )
            mcpClient.connect(transport)
            mcpClient.listTools()
            mcpClient.close()
        }
    }
}
