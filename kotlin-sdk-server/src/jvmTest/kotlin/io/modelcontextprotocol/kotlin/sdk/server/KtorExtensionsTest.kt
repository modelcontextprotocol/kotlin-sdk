package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.ktor.client.shouldHaveContentType
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class KtorExtensionsTest {

    @Test
    fun `Route mcp should throw at registration time if SSE plugin is not installed`() {
        val exception = shouldThrow<IllegalStateException> {
            testApplication {
                application {
                    routing {
                        mcp { testServer() }
                    }
                }
                client.get("/")
            }
        }
        exception.message shouldContain "install(SSE)"
    }

    @Test
    fun `Route mcp should register SSE and POST endpoints at the given subpath`() = testApplication {
        application {
            install(SSE)
            routing {
                route("/api/mcp") {
                    mcp(enableDnsRebindingProtection = false) { testServer() }
                }
            }
        }

        client.assertMcpEndpointsAt("/api/mcp")
    }

    @Test
    fun `Route mcp with path parameter should register endpoints at the resolved path`() = testApplication {
        application {
            install(SSE)
            routing {
                route("/api") {
                    mcp("/mcp-endpoint", enableDnsRebindingProtection = false) { testServer() }
                }
            }
        }

        client.assertMcpEndpointsAt("/api/mcp-endpoint")
        client.post("/api").shouldHaveStatus(HttpStatusCode.NotFound)
    }

    /**
     * Verifies that a closed session stops receiving POSTs and is not retained until the
     * [Server] itself closes, even while its SSE connection is still open.
     */
    @Test
    fun `Route mcp stops routing POSTs to a session once it closes`() = testApplication {
        val server = testServer()
        application {
            install(SSE)
            routing {
                mcp(enableDnsRebindingProtection = false) { server }
            }
        }

        client.prepareGet("/").execute { response ->
            val sessionId = response.readSseSessionId().shouldNotBeNull()
            client.postPing("/", sessionId).shouldHaveStatus(HttpStatusCode.Accepted)
            // The session registers with the Server just after the endpoint event goes out.
            eventually(5.seconds) { server.sessions shouldHaveSize 1 }

            server.sessions.values.single().close()

            client.postPing("/", sessionId).shouldHaveStatus(HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `Application mcp should install SSE and register endpoints at the root`() = testApplication {
        application {
            mcp(enableDnsRebindingProtection = false) { testServer() }
        }

        client.assertMcpEndpointsAt("/")
    }

    /**
     * GET at [path] opens an SSE stream announcing a session, a POST for that session is accepted,
     * and a POST without a session is rejected.
     */
    private suspend fun HttpClient.assertMcpEndpointsAt(path: String) {
        prepareGet(path).execute { response ->
            response.shouldHaveStatus(HttpStatusCode.OK)
            response.shouldHaveContentType(ContentType.Text.EventStream)
            val sessionId = response.readSseSessionId().shouldNotBeNull()

            postPing(path, sessionId).shouldHaveStatus(HttpStatusCode.Accepted)
        }

        post(path).shouldHaveStatus(HttpStatusCode.BadRequest)
    }

    /** POSTs a JSON-RPC ping to the session [sessionId] of the SSE endpoint at [path]. */
    private suspend fun HttpClient.postPing(path: String, sessionId: String): HttpResponse =
        post("$path?sessionId=$sessionId") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","id":1,"method":"ping"}""")
        }
}
