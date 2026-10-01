package io.modelcontextprotocol.kotlin.sdk.client.streamable.http

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.shared.TooLongFrameException
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test

private const val URL = "http://localhost:8080/mcp"
private const val SESSION_ID_HEADER = "mcp-session-id"
private const val SESSION_ID = "test-session-id"
private const val MAX_INLINE_SSE_EVENT_SIZE = 16 * 1024 * 1024

class StreamableHttpClientTransportTest {

    private val request = JSONRPCRequest(id = "req-1", method = "test")

    private val requests = mutableListOf<HttpRequestData>()
    private val messages = mutableListOf<JSONRPCMessage>()
    private val errors = mutableListOf<Throwable>()

    private fun createTransport(
        maxInlineSseEventSize: Int = MAX_INLINE_SSE_EVENT_SIZE,
        handler: MockRequestHandler = { respond("", HttpStatusCode.Accepted) },
    ): StreamableHttpClientTransport {
        val engine = MockEngine { data ->
            requests += data
            handler(data)
        }
        val httpClient = HttpClient(engine) { install(SSE) }
        return StreamableHttpClientTransport(httpClient, URL, maxInlineSseEventSize = maxInlineSseEventSize).apply {
            onMessage { messages += it }
            onError { errors += it }
        }
    }

    @Test
    fun `should post the message as JSON to the endpoint`() = runTest {
        val message = JSONRPCRequest(id = "test-id", method = "test", params = buildJsonObject { })
        val transport = createTransport()

        transport.start()
        transport.send(message)
        transport.close()

        val post = requests.single()
        post.method shouldBe HttpMethod.Post
        post.url.toString() shouldBe URL
        post.body.contentType shouldBe ContentType.Application.Json
        McpJson.decodeFromString<JSONRPCMessage>((post.body as TextContent).text) shouldBe message
    }

    @Test
    fun `should store the session id and send it with later requests`() = runTest {
        val transport = createTransport { respond("", HttpStatusCode.OK, headersOf(SESSION_ID_HEADER, SESSION_ID)) }

        transport.start()
        transport.send(request)
        transport.sessionId shouldBe SESSION_ID
        transport.send(JSONRPCNotification(method = "test"))
        transport.close()

        requests.map { it.headers[SESSION_ID_HEADER] } shouldBe listOf(null, SESSION_ID)
    }

    @Test
    fun `terminateSession should send DELETE with the session id and clear it`() = runTest {
        val transport = transportWithSession(deleteStatus = HttpStatusCode.OK)

        transport.terminateSession()

        val delete = requests.last()
        delete.method shouldBe HttpMethod.Delete
        delete.headers[SESSION_ID_HEADER] shouldBe SESSION_ID
        transport.sessionId.shouldBeNull()
        transport.close()
    }

    @Test
    fun `terminateSession should tolerate 405 and clear the session id`() = runTest {
        val transport = transportWithSession(deleteStatus = HttpStatusCode.MethodNotAllowed)

        transport.terminateSession()

        requests.last().method shouldBe HttpMethod.Delete
        transport.sessionId.shouldBeNull()
        transport.close()
    }

    @Test
    fun `should send the protocol version header once it is set`() = runTest {
        val headers = postHeaders(JSONRPCNotification(method = "test")) { protocolVersion = "2025-06-18" }

        headers["mcp-protocol-version"] shouldBe "2025-06-18"
    }

    @Test
    fun `connect should apply the negotiated protocol version to subsequent streamable HTTP requests`() = runTest {
        val negotiated = "2025-03-26"
        val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        val transport = createTransport { data ->
            if (data.method != HttpMethod.Post) {
                return@createTransport respond("", HttpStatusCode.MethodNotAllowed)
            }
            when (val message = McpJson.decodeFromString<JSONRPCMessage>((data.body as TextContent).text)) {
                is JSONRPCRequest -> when (message.method) {
                    "initialize" -> respond(
                        McpJson.encodeToString(
                            JSONRPCMessage.serializer(),
                            JSONRPCResponse(
                                id = message.id,
                                result = InitializeResult(
                                    protocolVersion = negotiated,
                                    capabilities = ServerCapabilities(),
                                    serverInfo = Implementation("test-server", "1.0.0"),
                                ),
                            ),
                        ),
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )

                    "ping" -> respond(
                        McpJson.encodeToString(
                            JSONRPCMessage.serializer(),
                            JSONRPCResponse(id = message.id, result = EmptyResult()),
                        ),
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )

                    else -> respond("", HttpStatusCode.Accepted)
                }

                else -> respond("", HttpStatusCode.Accepted)
            }
        }

        // Protocol request timeouts use delay(); runTest would skip the 60s budget before MockEngine replies.
        withContext(Dispatchers.Default) {
            val client = Client(Implementation("test-client", "1.0.0"))
            try {
                client.connect(transport)
                transport.protocolVersion shouldBe negotiated

                client.ping()
                val ping = requests.last { req ->
                    req.method == HttpMethod.Post &&
                        (req.body as TextContent).text.contains("\"method\":\"ping\"")
                }
                ping.headers["mcp-protocol-version"] shouldBe negotiated
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `should send MCP method and name headers from name`() = runTest {
        val headers = postHeaders(
            JSONRPCRequest(
                id = "test-id",
                method = "tools/call",
                params = buildJsonObject {
                    put("name", "read_file")
                    putJsonObject("arguments") { put("path", "README.md") }
                },
            ),
        )

        headers["Mcp-Method"] shouldBe "tools/call"
        headers["Mcp-Name"] shouldBe "read_file"
    }

    @Test
    fun `should send MCP name header from URI`() = runTest {
        val headers = postHeaders(
            JSONRPCRequest(
                id = "test-id",
                method = "resources/read",
                params = buildJsonObject { put("uri", "file:///README.md") },
            ),
        )

        headers["Mcp-Method"] shouldBe "resources/read"
        headers["Mcp-Name"] shouldBe "file:///README.md"
    }

    @Test
    fun `should encode MCP name header values when required`() = runTest {
        listOf(
            "Hello, 世界" to "=?base64?SGVsbG8sIOS4lueVjA==?=",
            " padded " to "=?base64?IHBhZGRlZCA=?=",
            "line1\nline2" to "=?base64?bGluZTEKbGluZTI=?=",
            "=?base64?literal?=" to "=?base64?PT9iYXNlNjQ/bGl0ZXJhbD89?=",
        ).forEach { (name, expectedHeader) ->
            withClue(name) {
                val headers = postHeaders(
                    JSONRPCRequest(
                        id = "test-id",
                        method = "tools/call",
                        params = buildJsonObject {
                            put("name", name)
                        },
                    ),
                )
                headers["Mcp-Name"] shouldBe expectedHeader
            }
        }
    }

    @Test
    fun `should send only the MCP method header for notifications without params`() = runTest {
        val headers = postHeaders(JSONRPCNotification(method = "notifications/tools/list_changed"))

        headers["Mcp-Method"] shouldBe "notifications/tools/list_changed"
        headers["Mcp-Name"].shouldBeNull()
    }

    @Test
    fun `should omit MCP standard headers for responses`() = runTest {
        val headers = postHeaders(JSONRPCResponse(id = RequestId.StringId("test-id")))

        headers["Mcp-Method"].shouldBeNull()
        headers["Mcp-Name"].shouldBeNull()
    }

    @Test
    fun `should fail the send and report the error when a JSON response is invalid`() = runTest {
        val transport = createTransport {
            respond(
                "this is not valid json",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        transport.start()

        val exception = shouldThrow<McpException> { transport.send(request) }

        exception.code shouldBe RPCError.ErrorCode.INTERNAL_ERROR
        errors.first() shouldBeSameInstanceAs exception.cause
        transport.close()
    }

    @Test
    fun `should skip blank inline SSE events and join multi-line data`() = runTest {
        sendWithInlineSse(
            sseEvent("retry: 5000", "id: a", "data:") +
                sseEvent("id: b", "data:  \t ") +
                sseEvent(
                    "id: c",
                    "event: message",
                    """data: {"jsonrpc":"2.0",""",
                    """data: "id":"req-1",""",
                    """data: "result":{"tools":[]}}""",
                ),
        )

        messages.single().shouldBeInstanceOf<JSONRPCResponse>().id shouldBe RequestId.StringId("req-1")
        errors.shouldBeEmpty()
    }

    @Test
    fun `should dispatch inline SSE notifications in order`() = runTest {
        sendWithInlineSse(
            sseEvent(
                "event: message",
                "id: 1",
                """data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":"t","progress":50}}""",
            ) + sseEvent("id: 2", """data: {"jsonrpc":"2.0","method":"notifications/tools/list_changed"}"""),
        )

        messages.map { it.shouldBeInstanceOf<JSONRPCNotification>().method } shouldBe
            listOf("notifications/progress", "notifications/tools/list_changed")
        errors.shouldBeEmpty()
    }

    @Test
    fun `should report inline SSE error events without failing the send`() = runTest {
        sendWithInlineSse(sseEvent("event: error", "data: Something went wrong"))

        errors.single().shouldBeInstanceOf<StreamableHttpError>().message shouldBe
            "Streamable HTTP error: Something went wrong"
        messages.shouldBeEmpty()
    }

    @Test
    fun `should reject an inline SSE event larger than the size limit`() = runTest {
        // 20 data lines of 16 chars without the blank line that would dispatch the event.
        val exception = shouldThrow<McpException> {
            sendWithInlineSse("event: message\n" + "data: ${"A".repeat(16)}\n".repeat(20), maxInlineSseEventSize = 64)
        }

        exception.cause.shouldBeInstanceOf<TooLongFrameException>()
        errors.single().shouldBeInstanceOf<TooLongFrameException>()
        messages.shouldBeEmpty()
    }

    @Test
    fun `should accept an inline SSE event exactly at the size limit`() = runTest {
        val part1 = """{"jsonrpc":"2.0","""
        val part2 = """"method":"notifications/tools/list_changed"}"""

        sendWithInlineSse(
            sseEvent("event: message", "data: $part1", "data: $part2"),
            maxInlineSseEventSize = (part1 + part2).length,
        )

        messages.single().shouldBeInstanceOf<JSONRPCNotification>().method shouldBe "notifications/tools/list_changed"
        errors.shouldBeEmpty()
    }

    @Test
    fun `should reject a non-positive inline SSE event size limit`() {
        shouldThrow<IllegalArgumentException> { createTransport(maxInlineSseEventSize = 0) }
    }

    private suspend fun transportWithSession(deleteStatus: HttpStatusCode): StreamableHttpClientTransport {
        val transport = createTransport { data ->
            if (data.method == HttpMethod.Delete) {
                respond("", deleteStatus)
            } else {
                respond("", HttpStatusCode.Accepted, headersOf(SESSION_ID_HEADER, SESSION_ID))
            }
        }
        transport.start()
        transport.send(JSONRPCNotification(method = "test"))
        transport.sessionId shouldBe SESSION_ID
        return transport
    }

    private suspend fun postHeaders(
        message: JSONRPCMessage,
        configure: StreamableHttpClientTransport.() -> Unit = {},
    ): Headers {
        val transport = createTransport().apply(configure)
        transport.start()
        transport.send(message)
        transport.close()
        return requests.last().headers
    }

    /** Sends [request] and answers the POST with [sse] as an inline event stream. */
    private suspend fun sendWithInlineSse(sse: String, maxInlineSseEventSize: Int = MAX_INLINE_SSE_EVENT_SIZE) {
        val transport = createTransport(maxInlineSseEventSize) { data ->
            if (data.method == HttpMethod.Post) {
                respond(
                    ByteReadChannel(sse),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
                )
            } else {
                respond("", HttpStatusCode.MethodNotAllowed)
            }
        }
        transport.start()
        try {
            transport.send(request)
        } finally {
            transport.close()
        }
    }

    private fun sseEvent(vararg lines: String): String = lines.joinToString(separator = "\n", postfix = "\n\n")
}
