package io.modelcontextprotocol.kotlin.sdk.integration.streamablehttp

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
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.ByteReadChannel
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import io.ktor.client.engine.cio.CIO as ClientCIO

private const val SESSION_ID_HEADER = "mcp-session-id"
private const val PROTOCOL_VERSION_HEADER = "mcp-protocol-version"
private const val DEFAULT_HEARTBEAT_LINE = ": heartbeat"
private const val CUSTOM_HEARTBEAT_LINE = ": mcp-heartbeat"

class StreamableHttpHeartbeatIntegrationTest : AbstractStreamableHttpIntegrationTest() {

    @Test
    fun `GET SSE stream emits configured heartbeat`(): Unit = runBlocking(Dispatchers.IO) {
        var server: StreamableHttpTestServer? = null
        var httpClient: HttpClient? = null

        try {
            server = initTestServer("heartbeat-test") {
                period = 50.milliseconds
                event = ServerSentEvent(comments = "mcp-heartbeat")
            }
            val mcpUrl = "http://$URL:${server.ktorServer.actualPort()}/mcp"
            httpClient = HttpClient(ClientCIO) { install(SSE) }
            val sessionId = initializeSession(httpClient, mcpUrl)

            httpClient.prepareGet(mcpUrl) {
                addSseHeaders(sessionId)
            }.execute { response ->
                response.status shouldBe HttpStatusCode.OK
                response.headers[SESSION_ID_HEADER] shouldBe sessionId

                response.bodyAsChannel().readLineMatching(CUSTOM_HEARTBEAT_LINE) shouldBe CUSTOM_HEARTBEAT_LINE
            }
        } finally {
            httpClient?.close()
            server?.ktorServer?.stopSuspend(1000, 2000)
        }
    }

    @Test
    fun `GET SSE stream does not emit heartbeat by default`(): Unit = runBlocking(Dispatchers.IO) {
        var server: StreamableHttpTestServer? = null
        var httpClient: HttpClient? = null

        try {
            server = initTestServer("no-heartbeat-test")
            val mcpUrl = "http://$URL:${server.ktorServer.actualPort()}/mcp"
            httpClient = HttpClient(ClientCIO) { install(SSE) }
            val sessionId = initializeSession(httpClient, mcpUrl)

            httpClient.prepareGet(mcpUrl) {
                addSseHeaders(sessionId)
            }.execute { response ->
                response.status shouldBe HttpStatusCode.OK
                response.headers[SESSION_ID_HEADER] shouldBe sessionId

                response.bodyAsChannel().readLineMatching(timeoutMillis = 150) { line ->
                    line.isHeartbeatSseLine()
                } shouldBe null
            }
        } finally {
            httpClient?.close()
            server?.ktorServer?.stopSuspend(1000, 2000)
        }
    }

    @Test
    fun `GET SSE stream emits configured heartbeat repeatedly`(): Unit = runBlocking(Dispatchers.IO) {
        var server: StreamableHttpTestServer? = null
        var httpClient: HttpClient? = null

        try {
            server = initTestServer("repeating-heartbeat-test") {
                period = 50.milliseconds
                event = ServerSentEvent(comments = "mcp-heartbeat")
            }
            val mcpUrl = "http://$URL:${server.ktorServer.actualPort()}/mcp"
            httpClient = HttpClient(ClientCIO) { install(SSE) }
            val sessionId = initializeSession(httpClient, mcpUrl)

            httpClient.prepareGet(mcpUrl) {
                addSseHeaders(sessionId)
            }.execute { response ->
                response.status shouldBe HttpStatusCode.OK
                response.headers[SESSION_ID_HEADER] shouldBe sessionId

                val channel = response.bodyAsChannel()
                channel.readLineMatching(CUSTOM_HEARTBEAT_LINE) shouldBe CUSTOM_HEARTBEAT_LINE
                channel.readLineMatching(CUSTOM_HEARTBEAT_LINE) shouldBe CUSTOM_HEARTBEAT_LINE
            }
        } finally {
            httpClient?.close()
            server?.ktorServer?.stopSuspend(1000, 2000)
        }
    }

    private suspend fun initializeSession(client: HttpClient, mcpUrl: String): String {
        val response = client.post(mcpUrl) {
            contentType(ContentType.Application.Json)
            header(
                HttpHeaders.Accept,
                "${ContentType.Application.Json}, ${ContentType.Text.EventStream}",
            )
            setBody(Json.encodeToString(buildInitPayload()))
        }

        response.status shouldBe HttpStatusCode.OK
        return assertNotNull(response.headers[SESSION_ID_HEADER])
    }

    private fun buildInitPayload(): JSONRPCRequest = InitializeRequest(
        InitializeRequestParams(
            protocolVersion = LATEST_PROTOCOL_VERSION,
            capabilities = ClientCapabilities(),
            clientInfo = Implementation(name = "heartbeat-test-client", version = "1.0.0"),
        ),
    ).toJSON()

    private fun io.ktor.client.request.HttpRequestBuilder.addSseHeaders(sessionId: String) {
        header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
        header(SESSION_ID_HEADER, sessionId)
        header(PROTOCOL_VERSION_HEADER, LATEST_PROTOCOL_VERSION)
    }

    private suspend fun ByteReadChannel.readLineMatching(expectedLine: String, timeoutMillis: Long = 2_000): String? =
        readLineMatching(timeoutMillis) { line -> line == expectedLine }

    private suspend fun ByteReadChannel.readLineMatching(
        timeoutMillis: Long = 2_000,
        matches: (String) -> Boolean,
    ): String? = withTimeoutOrNull(timeoutMillis.milliseconds) {
        var line = readUTF8Line()
        while (line != null) {
            if (matches(line)) return@withTimeoutOrNull line
            line = readUTF8Line()
        }
        null
    }

    private fun String.isHeartbeatSseLine(): Boolean = this == DEFAULT_HEARTBEAT_LINE ||
        this == CUSTOM_HEARTBEAT_LINE ||
        (startsWith(":") && contains("heartbeat", ignoreCase = true)) ||
        (startsWith("event:") && contains("heartbeat", ignoreCase = true))
}
