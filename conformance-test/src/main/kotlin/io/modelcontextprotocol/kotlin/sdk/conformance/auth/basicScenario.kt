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
import java.util.Base64

// Client Credentials Basic scenario
internal suspend fun runClientCredentialsBasic(serverUrl: String) {
    val ctx = conformanceContext()
    val clientId = ctx.requiredString("client_id")
    val clientSecret = ctx.requiredString("client_secret")

    val httpClient = HttpClient(CIO) {
        install(SSE)
        followRedirects = false
    }

    httpClient.use { client ->
        val tokenEndpoint = discoverTokenEndpoint(client, serverUrl)

        // Exchange credentials for token using Basic auth
        val basicAuth = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
        val tokenResponse = client.submitForm(
            url = tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
            },
        ) {
            header(HttpHeaders.Authorization, "Basic $basicAuth")
        }
        val accessToken = extractAccessToken(tokenResponse)

        withBearerToken(accessToken) { authedClient ->
            val transport = StreamableHttpClientTransport(authedClient, serverUrl)
            val client = Client(
                clientInfo = Implementation("conformance-client-credentials-basic", "1.0.0"),
                options = ClientOptions(capabilities = ClientCapabilities()),
            )
            client.connect(transport)
            client.listTools()
            client.close()
        }
    }
}
