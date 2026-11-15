package io.modelcontextprotocol.sample.client

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/**
 * A short-lived HTTP server on 127.0.0.1 that receives the authorization redirect.
 *
 * Native apps use a loopback redirect URI (RFC 8252, section 7.3), so the authorization code never
 * leaves the user's machine.
 */
class LoopbackCallbackServer(port: Int = 0) : AutoCloseable {
    private val result = CompletableDeferred<Map<String, String>>()

    private val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
        routing {
            get("/callback") {
                val params = call.request.queryParameters.entries().associate { it.key to it.value.first() }
                result.complete(params)
                val error = params["error"]
                if (error == null) {
                    val page = PAGE.format("Signed in", "You can close this window and return to the terminal.")
                    call.respondText(page, ContentType.Text.Html)
                } else {
                    val page = PAGE.format("Sign-in failed", error)
                    call.respondText(page, ContentType.Text.Html, HttpStatusCode.BadRequest)
                }
            }
        }
    }.start(wait = false)

    /** The redirect URI to register and send to the authorization endpoint. */
    val redirectUri: String = runBlocking {
        "http://127.0.0.1:${server.engine.resolvedConnectors().first().port}/callback"
    }

    /** Waits for the redirect, checks [expectedState] and returns the authorization code. */
    suspend fun awaitCode(expectedState: String, timeout: Duration): String {
        val params = withTimeout(timeout) { result.await() }
        params["error"]?.let { error ->
            error("Authorization failed: $error ${params["error_description"].orEmpty()}".trim())
        }
        check(params["state"] == expectedState) { "Authorization response has an unexpected state" }
        return params["code"] ?: error("Authorization response has no code")
    }

    override fun close() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
    }

    private companion object {
        const val PAGE = "<!doctype html><html><body><h1>%s</h1><p>%s</p></body></html>"
    }
}
