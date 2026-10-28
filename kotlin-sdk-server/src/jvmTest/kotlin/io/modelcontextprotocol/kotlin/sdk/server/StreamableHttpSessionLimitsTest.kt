package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Verifies that a stateful Streamable HTTP endpoint bounds the sessions it keeps: idle sessions expire,
 * at most `maxSessions` are open at once, every way a session ends gives its slot back, and a client cannot
 * keep a session from expiring by reusing a request id.
 */
class StreamableHttpSessionLimitsTest {

    private val idleTimeout = 200.milliseconds

    @Test
    fun `abandoned session is closed once idle for sessionIdleTimeout`() = testApplication {
        val server = testServer()
        application { mcpStreamableHttp(sessionIdleTimeout = idleTimeout) { server } }

        val sessionId = client.initialize()

        eventually(5.seconds) {
            server.sessions.shouldBeEmpty()
        }
        client.ping(sessionId).status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `requests keep a session alive`() = testApplication {
        val server = testServer()
        application { mcpStreamableHttp(sessionIdleTimeout = 400.milliseconds) { server } }

        val sessionId = client.initialize()
        // Well past the timeout plus two expiry checks, so a session that requests did not keep alive expires.
        repeat(13) {
            delay(100.milliseconds)
            client.ping(sessionId).status shouldBe HttpStatusCode.OK
        }
        server.sessions shouldHaveSize 1
    }

    @Test
    fun `session beyond maxSessions is rejected with 503 until one closes`() = testApplication {
        application { mcpStreamableHttp(maxSessions = 2) { testServer() } }

        val first = client.initialize()
        client.initialize()

        val rejected = client.post("/mcp") {
            localhostStreamableHeaders()
            setBody(initializeRequestBody())
        }
        rejected.status shouldBe HttpStatusCode.ServiceUnavailable
        rejected.headers[MCP_SESSION_ID_HEADER] shouldBe null
        rejected.decodeError().error.code shouldBe RPCError.ErrorCode.INTERNAL_ERROR

        client.delete("/mcp") {
            header(HttpHeaders.Host, "localhost")
            header(MCP_SESSION_ID_HEADER, first)
        }.status shouldBe HttpStatusCode.OK

        client.initialize()
    }

    @Test
    fun `a session the server closes frees its slot and its id answers 404`() = testApplication {
        val server = testServer()
        application { mcpStreamableHttp(maxSessions = 1) { server } }

        val sessionId = client.initialize()
        server.sessions.values.single().close()

        client.ping(sessionId).status shouldBe HttpStatusCode.NotFound
        client.initialize()
    }

    @Test
    fun `a failing server factory gives its session slot back`() = testApplication {
        val factoryCalls = AtomicInteger()
        application {
            mcpStreamableHttp(maxSessions = 1) {
                check(factoryCalls.getAndIncrement() > 0) { "The first server fails to start" }
                testServer()
            }
        }

        client.post("/mcp") {
            localhostStreamableHeaders()
            setBody(initializeRequestBody())
        }.status shouldBe HttpStatusCode.InternalServerError
        client.initialize()
    }

    @Test
    fun `requests that never initialize leave no session behind on a shared server`() = testApplication {
        val server = testServer()
        application { mcpStreamableHttp(maxSessions = 1) { server } }

        repeat(3) {
            client.post("/mcp") {
                localhostStreamableHeaders()
                setBody("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")
            }.status shouldBe HttpStatusCode.BadRequest
        }

        eventually(5.seconds) {
            server.sessions.shouldBeEmpty()
        }
        // The rejected requests gave their slot back.
        client.initialize()
        server.sessions shouldHaveSize 1
    }

    @Test
    fun `a request id still in flight cannot be reused`() = testApplication {
        val toolStarted = CompletableDeferred<Unit>()
        val releaseTool = CompletableDeferred<Unit>()
        application {
            mcpStreamableHttp {
                Server(
                    Implementation("test-server", "1.0"),
                    ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools())),
                ) {
                    addTool(name = "wait", description = "Answers once the test releases it") {
                        toolStarted.complete(Unit)
                        releaseTool.await()
                        CallToolResult(content = listOf(TextContent("released")))
                    }
                }
            }
        }
        val sessionId = client.initialize()
        client.sendInitialized(sessionId)

        coroutineScope {
            val inFlight = async {
                client.post("/mcp") {
                    localhostStreamableHeaders()
                    header(MCP_SESSION_ID_HEADER, sessionId)
                    setBody(
                        """{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"wait","arguments":{}}}""",
                    )
                }
            }
            toolStarted.await()

            // Accepting the reused id would route the answer for id 7 to this POST and leave the first one
            // waiting forever, holding its session open.
            val reused = client.post("/mcp") {
                localhostStreamableHeaders()
                header(MCP_SESSION_ID_HEADER, sessionId)
                setBody("""{"jsonrpc":"2.0","id":7,"method":"ping"}""")
            }
            reused.status shouldBe HttpStatusCode.BadRequest
            reused.decodeError().error.code shouldBe RPCError.ErrorCode.INVALID_REQUEST

            releaseTool.complete(Unit)
            val answered = withTimeout(5.seconds) { inFlight.await() }
            answered.status shouldBe HttpStatusCode.OK
            answered.bodyAsText() shouldContain "released"
        }
    }

    @Test
    fun `a batch that repeats a request id is refused`() = testApplication {
        application { mcpStreamableHttp { testServer() } }
        val sessionId = client.initialize()

        val response = client.post("/mcp") {
            localhostStreamableHeaders()
            header(MCP_SESSION_ID_HEADER, sessionId)
            setBody("""[{"jsonrpc":"2.0","id":9,"method":"ping"},{"jsonrpc":"2.0","id":9,"method":"ping"}]""")
        }

        response.status shouldBe HttpStatusCode.BadRequest
        response.decodeError().error.code shouldBe RPCError.ErrorCode.INVALID_REQUEST
    }

    @Test
    fun `session limits must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                application { mcpStreamableHttp(maxSessions = 0) { testServer() } }
                client.get("/mcp")
            }
        }
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                application { mcpStreamableHttp(sessionIdleTimeout = Duration.ZERO) { testServer() } }
                client.get("/mcp")
            }
        }
    }

    private suspend fun HttpClient.initialize(): String {
        val response = post("/mcp") {
            localhostStreamableHeaders()
            setBody(initializeRequestBody())
        }
        response.status shouldBe HttpStatusCode.OK
        return response.headers[MCP_SESSION_ID_HEADER].shouldNotBeNull()
    }

    private suspend fun HttpClient.sendInitialized(sessionId: String) {
        post("/mcp") {
            localhostStreamableHeaders()
            header(MCP_SESSION_ID_HEADER, sessionId)
            setBody("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        }.status shouldBe HttpStatusCode.Accepted
    }

    private suspend fun HttpClient.ping(sessionId: String): HttpResponse = post("/mcp") {
        localhostStreamableHeaders()
        header(MCP_SESSION_ID_HEADER, sessionId)
        setBody("""{"jsonrpc":"2.0","id":2,"method":"ping"}""")
    }

    /** [streamableHeaders] plus the `Host` that the endpoint's DNS rebinding protection admits. */
    private fun HttpRequestBuilder.localhostStreamableHeaders() {
        header(HttpHeaders.Host, "localhost")
        streamableHeaders()
    }

    private fun initializeRequestBody(): String =
        McpJson.encodeToString(JSONRPCMessage.serializer(), initializeRequest())

    private suspend fun HttpResponse.decodeError(): JSONRPCError = McpJson.decodeFromString(bodyAsText())
}
