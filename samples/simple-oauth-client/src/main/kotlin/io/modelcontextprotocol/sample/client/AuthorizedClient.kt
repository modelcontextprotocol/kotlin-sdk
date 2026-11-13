package io.modelcontextprotocol.sample.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.bearerAuth
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

/**
 * Creates an [HttpClient] for talking to a protected MCP server at [serverUrl].
 *
 * Every request carries the current access token. When the server answers `401 Unauthorized`,
 * the client runs [oauthFlow], stores the new token and retries the request once.
 */
fun authorizedHttpClient(serverUrl: String, oauthFlow: OAuthFlow): HttpClient {
    var accessToken: String? = null

    val client = HttpClient(CIO) {
        install(SSE)
    }
    client.plugin(HttpSend).intercept { request ->
        accessToken?.let { request.bearerAuth(it) }
        val call = execute(request)
        if (call.response.status != HttpStatusCode.Unauthorized) return@intercept call

        val token = oauthFlow.authorize(serverUrl, call.response.headers[HttpHeaders.WWWAuthenticate])
        accessToken = token
        request.headers.remove(HttpHeaders.Authorization)
        request.bearerAuth(token)
        execute(request)
    }
    return client
}
