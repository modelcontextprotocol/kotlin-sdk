package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class StreamableHttpHeartbeatTest {
    private val path = "/mcp"

    @Test
    fun `GET SSE stream applies configured heartbeat`() = testApplication {
        val heartbeatConfigured = AtomicBoolean(false)

        application {
            mcpStreamableHttp(
                path = path,
                sseHeartbeatConfig = {
                    heartbeatConfigured.set(true)
                    period = 50.milliseconds
                    event = ServerSentEvent(comments = "mcp-heartbeat")
                },
            ) {
                testServer()
            }
        }

        val client = createTestClient()
        val sessionId = initializeSession(client)

        client.prepareGet(path) {
            addSseHeaders(sessionId)
        }.execute { response ->
            response.status shouldBe HttpStatusCode.OK
            response.headers[MCP_SESSION_ID_HEADER] shouldBe sessionId
            assertNotNull(response.bodyAsChannel().readUTF8Line())
            heartbeatConfigured.get() shouldBe true
        }
    }

    @Test
    fun `GET SSE stream does not send heartbeat by default`() = testApplication {
        application {
            mcpStreamableHttp(path = path) {
                testServer()
            }
        }

        val client = createTestClient()
        val sessionId = initializeSession(client)

        client.prepareGet(path) {
            addSseHeaders(sessionId)
        }.execute { response ->
            response.status shouldBe HttpStatusCode.OK
            response.headers[MCP_SESSION_ID_HEADER] shouldBe sessionId

            val heartbeatLine = response.bodyAsChannel().readLineMatching(": heartbeat", timeoutMillis = 150)

            heartbeatLine shouldBe null
        }
    }

    private fun testServer(): Server = Server(
        Implementation("test-server", "1.0.0"),
        ServerOptions(capabilities = ServerCapabilities()),
    )

    private suspend fun initializeSession(client: HttpClient): String {
        val response = client.post(path) {
            header(HttpHeaders.Host, "localhost")
            addStreamableHeaders()
            setBody(buildInitializeRequestPayload())
        }

        response.status shouldBe HttpStatusCode.OK
        return assertNotNull(response.headers[MCP_SESSION_ID_HEADER])
    }

    private suspend fun ByteReadChannel.readLineMatching(expectedLine: String, timeoutMillis: Long = 2_000): String? =
        withTimeoutOrNull(timeoutMillis.milliseconds) {
            var line = readUTF8Line()
            while (line != null) {
                if (line == expectedLine) return@withTimeoutOrNull line
                line = readUTF8Line()
            }
            null
        }

    private fun HttpRequestBuilder.addStreamableHeaders() {
        header(
            HttpHeaders.Accept,
            listOf(ContentType.Application.Json, ContentType.Text.EventStream).joinToString(", ") {
                it.toString()
            },
        )
        contentType(ContentType.Application.Json)
    }

    private fun HttpRequestBuilder.addSseHeaders(sessionId: String) {
        header(HttpHeaders.Host, "localhost")
        header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
        header(MCP_SESSION_ID_HEADER, sessionId)
        header("mcp-protocol-version", LATEST_PROTOCOL_VERSION)
    }

    private fun buildInitializeRequestPayload(): JSONRPCRequest = InitializeRequest(
        InitializeRequestParams(
            protocolVersion = LATEST_PROTOCOL_VERSION,
            capabilities = ClientCapabilities(),
            clientInfo = Implementation(name = "test-client", version = "1.0.0"),
        ),
    ).toJSON()

    private fun ApplicationTestBuilder.createTestClient(): HttpClient = createClient {
        install(ClientContentNegotiation) {
            json(McpJson)
        }
    }
}
