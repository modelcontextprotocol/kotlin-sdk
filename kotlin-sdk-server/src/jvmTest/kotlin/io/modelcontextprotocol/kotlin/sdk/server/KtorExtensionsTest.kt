package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.ktor.client.shouldHaveContentType
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test

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

    @Test
    fun `Application mcp should install SSE and register endpoints at the root`() = testApplication {
        application {
            mcp(enableDnsRebindingProtection = false) { testServer() }
        }

        client.assertMcpEndpointsAt("/")
    }

    @Test
    fun `Application mcp should reuse an installed SSE plugin`() = testApplication {
        application {
            install(SSE)
            mcp(enableDnsRebindingProtection = false) { testServer() }
        }

        client.assertMcpEndpointsAt("/")
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `Application mcpStreamableHttp should work with or without an installed SSE plugin`(sseInstalled: Boolean) =
        testApplication {
            application {
                if (sseInstalled) install(SSE)
                mcpStreamableHttp(enableDnsRebindingProtection = false) { testServer() }
            }

            client.assertStreamableMcpEndpointAt("/mcp")
        }

    @Test
    fun `Application mcpStreamableHttp should register multiple endpoints`() = testApplication {
        application {
            mcpStreamableHttp("/first", enableDnsRebindingProtection = false) { testServer() }
            mcpStreamableHttp("/second", enableDnsRebindingProtection = false) { testServer() }
        }

        client.assertStreamableMcpEndpointAt("/first")
        client.assertStreamableMcpEndpointAt("/second")
    }

    private suspend fun HttpClient.assertStreamableMcpEndpointAt(path: String) {
        val response = post(path) {
            streamableHeaders()
            setBody(McpJson.encodeToString(JSONRPCMessage.serializer(), initializeRequest()))
        }
        response.shouldHaveStatus(HttpStatusCode.OK)
        response.shouldHaveContentType(ContentType.Application.Json)
        val sessionId = response.headers[MCP_SESSION_ID_HEADER].shouldNotBeNull()
        try {
            val result = McpJson.decodeFromString<JSONRPCResponse>(response.bodyAsText()).result
            result.shouldBeInstanceOf<InitializeResult>().serverInfo.name shouldBe "test-server"
        } finally {
            delete(path) {
                header(MCP_SESSION_ID_HEADER, sessionId)
            }.shouldHaveStatus(HttpStatusCode.OK)
        }
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

            post("$path?sessionId=$sessionId") {
                contentType(ContentType.Application.Json)
                setBody("""{"jsonrpc":"2.0","id":1,"method":"ping"}""")
            }.shouldHaveStatus(HttpStatusCode.Accepted)
        }

        post(path).shouldHaveStatus(HttpStatusCode.BadRequest)
    }
}
