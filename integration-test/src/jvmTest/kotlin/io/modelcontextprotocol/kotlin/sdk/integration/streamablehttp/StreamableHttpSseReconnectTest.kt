package io.modelcontextprotocol.kotlin.sdk.integration.streamablehttp

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import io.modelcontextprotocol.kotlin.test.utils.actualPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import io.ktor.client.engine.cio.CIO as ClientCIO

private const val SESSION_ID_HEADER = "mcp-session-id"
private const val PROTOCOL_VERSION_HEADER = "mcp-protocol-version"

/**
 * Integration tests for GET SSE stream reconnection using a real embedded CIO server.
 *
 * Verifies that the transport correctly evicts stale STANDALONE_SSE_STREAM_ID
 * entries when a client reconnects after a disconnect, rather than silently
 * rejecting the new stream.
 */
class StreamableHttpSseReconnectTest : AbstractStreamableHttpIntegrationTest() {

    /**
     * Verifies that after a GET SSE stream disconnects and the client
     * immediately reconnects, the server evicts the stale stream mapping
     * and allows the new stream to succeed.
     */
    @Test
    fun `GET SSE reconnect after disconnect should succeed`(): Unit = runBlocking(Dispatchers.IO) {
        var server: StreamableHttpTestServer? = null
        var httpClient: HttpClient? = null

        try {
            server = initTestServer("reconnect-test")
            val port = server.ktorServer.actualPort()
            val mcpUrl = "http://$URL:$port/mcp"

            httpClient = HttpClient(ClientCIO) { install(SSE) }

            // Step 1: Initialize session via POST
            val initResponse = httpClient.post(mcpUrl) {
                contentType(ContentType.Application.Json)
                header(
                    HttpHeaders.Accept,
                    "${ContentType.Application.Json}, ${ContentType.Text.EventStream}",
                )
                setBody(Json.encodeToString(buildInitPayload()))
            }
            initResponse.status shouldBe HttpStatusCode.OK
            val sessionId = assertNotNull(initResponse.headers[SESSION_ID_HEADER])

            // Step 2: Open GET SSE stream, consume the flush event, then disconnect
            httpClient.prepareGet(mcpUrl) {
                header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
                header(SESSION_ID_HEADER, sessionId)
                header(PROTOCOL_VERSION_HEADER, LATEST_PROTOCOL_VERSION)
            }.execute { response ->
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsChannel().readUTF8Line()
            }

            // Step 3: Immediately reconnect. The transport detects that the
            // previous stream's coroutine is no longer active and evicts the
            // stale mapping, allowing the new stream to succeed.
            httpClient.prepareGet(mcpUrl) {
                header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
                header(SESSION_ID_HEADER, sessionId)
                header(PROTOCOL_VERSION_HEADER, LATEST_PROTOCOL_VERSION)
            }.execute { response ->
                response.status shouldBe HttpStatusCode.OK
                response.headers[SESSION_ID_HEADER] shouldBe sessionId

                val channel = response.bodyAsChannel()
                val firstLine = channel.readUTF8Line()
                firstLine.shouldNotBeNull()
                channel.isClosedForRead shouldBe false
            }
        } finally {
            httpClient?.close()
            server?.ktorServer?.stopSuspend(1000, 2000)
        }
    }

    /**
     * Verifies that a second concurrent GET SSE stream on the same session
     * closes the old stream and takes over. The new stream should be live.
     */
    @Test
    fun `concurrent GET SSE stream closes old stream and takes over`(): Unit = runBlocking(Dispatchers.IO) {
        var server: StreamableHttpTestServer? = null
        var httpClient: HttpClient? = null

        try {
            server = initTestServer("takeover-test")
            val port = server.ktorServer.actualPort()
            val mcpUrl = "http://$URL:$port/mcp"

            httpClient = HttpClient(ClientCIO) { install(SSE) }

            // Step 1: Initialize session via POST
            val initResponse = httpClient.post(mcpUrl) {
                contentType(ContentType.Application.Json)
                header(
                    HttpHeaders.Accept,
                    "${ContentType.Application.Json}, ${ContentType.Text.EventStream}",
                )
                setBody(Json.encodeToString(buildInitPayload()))
            }
            initResponse.status shouldBe HttpStatusCode.OK
            val sessionId = assertNotNull(initResponse.headers[SESSION_ID_HEADER])

            // Step 2: Open first GET SSE stream and keep it open
            httpClient.prepareGet(mcpUrl) {
                header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
                header(SESSION_ID_HEADER, sessionId)
                header(PROTOCOL_VERSION_HEADER, LATEST_PROTOCOL_VERSION)
            }.execute { firstResponse ->
                firstResponse.status shouldBe HttpStatusCode.OK
                firstResponse.bodyAsChannel().readUTF8Line()

                // Step 3: Open a second GET — closes old stream, new one takes over
                httpClient.prepareGet(mcpUrl) {
                    header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
                    header(SESSION_ID_HEADER, sessionId)
                    header(PROTOCOL_VERSION_HEADER, LATEST_PROTOCOL_VERSION)
                }.execute { secondResponse ->
                    secondResponse.status shouldBe HttpStatusCode.OK
                    secondResponse.headers[SESSION_ID_HEADER] shouldBe sessionId

                    // New stream is alive
                    val secondChannel = secondResponse.bodyAsChannel()
                    val firstLine = secondChannel.readUTF8Line()
                    firstLine.shouldNotBeNull()
                    secondChannel.isClosedForRead shouldBe false
                }
            }
        } finally {
            httpClient?.close()
            server?.ktorServer?.stopSuspend(1000, 2000)
        }
    }

    private fun buildInitPayload(): JSONRPCRequest = InitializeRequest(
        InitializeRequestParams(
            protocolVersion = LATEST_PROTOCOL_VERSION,
            capabilities = ClientCapabilities(),
            clientInfo = Implementation(name = "reconnect-test-client", version = "1.0.0"),
        ),
    ).toJSON()
}
