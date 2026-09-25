package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_MODERN_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.NAME_BEARING_METHODS
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

/**
 * The HTTP contract of the request-scoped lifecycle (protocol revision 2026-07-28, SEP-2575): the
 * status each rejection travels on, notification handling, and the deferral before a response
 * commits to `text/event-stream`.
 *
 * Driven over raw HTTP rather than through [io.modelcontextprotocol.kotlin.sdk.client.Client]
 * because every assertion here is about a status code, a header or the response media type — none of
 * which the typed client surface can observe. Bodies are built by hand for the same reason: several
 * of these requests are ones a conforming client cannot produce.
 *
 * All statuses are spec-mandated (revision §Transports, Streamable HTTP).
 */
@OptIn(ExperimentalMcpApi::class, io.modelcontextprotocol.kotlin.sdk.InternalMcpApi::class)
class ModernStreamableHttpTest {

    @Test
    fun `a request-scoped tool call answers 200 with the tool result`() = testApplication {
        serve()

        val response = post(callTool("echo"))

        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldContain "echoed"
    }

    @Test
    fun `a method the revision does not define answers 404`() = testApplication {
        serve()

        // `ping` is one of the six the revision removed, so it is absent from this lifecycle's
        // registry even though the SDK still serves it on the other one.
        val response = post(request(id = 1, method = "ping"), method = "ping")

        response.status shouldBe HttpStatusCode.NotFound
        response.errorCode() shouldBe RPCError.ErrorCode.METHOD_NOT_FOUND
    }

    @Test
    fun `a protocol version this server does not serve answers 400`() = testApplication {
        serve()

        // Header and body agree; it is the revision they agree on that this server does not serve.
        val response = post(callTool("echo", protocolVersion = "2999-01-01"), versionHeader = "2999-01-01")

        response.status shouldBe HttpStatusCode.BadRequest
        response.errorCode() shouldBe RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION
    }

    @Test
    fun `a protocol version header disagreeing with the body answers 400`() = testApplication {
        serve()

        val response = post(callTool("echo"), versionHeader = "2025-11-25")

        // The two restate one another, so a disagreement means one is wrong and neither can be
        // trusted — reported before the version gate, so a client contradicting itself is told that.
        response.status shouldBe HttpStatusCode.BadRequest
        response.errorCode() shouldBe RPCError.ErrorCode.HEADER_MISMATCH
    }

    @Test
    fun `an envelope missing its client capabilities answers 400`() = testApplication {
        serve()

        val body = """
            {"jsonrpc":"2.0","id":1,"method":"tools/list",
             "params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"$LATEST_MODERN_VERSION"}}}
        """.trimIndent()
        val response = post(body, method = "tools/list")

        response.status shouldBe HttpStatusCode.BadRequest
        response.errorCode() shouldBe RPCError.ErrorCode.INVALID_PARAMS
        response.bodyAsText() shouldContain "clientCapabilities"
    }

    @Test
    fun `an invalid-params error from dispatch stays in band on 200`() = testApplication {
        serve()

        val body = """
            {"jsonrpc":"2.0","id":1,"method":"resources/read",
             "params":{"uri":"test://absent",
                       "_meta":{"io.modelcontextprotocol/protocolVersion":"$LATEST_MODERN_VERSION",
                                "io.modelcontextprotocol/clientCapabilities":{}}}}
        """.trimIndent()
        val response = post(body, method = "resources/read", name = "test://absent")

        // The defining case for keying status on origin. The envelope rung is the only
        // invalid-params rejection the revision maps to 400; one produced past the entry gate is
        // the payload, not an HTTP failure — otherwise a bad argument would mislead every proxy,
        // retry and metric between here and the caller.
        response.status shouldBe HttpStatusCode.OK
        response.errorCode() shouldBe RPCError.ErrorCode.INVALID_PARAMS
    }

    @Test
    fun `a request-scoped notification is acknowledged with 202 and no body`() = testApplication {
        serve()

        val body = """
            {"jsonrpc":"2.0","method":"notifications/cancelled",
             "params":{"requestId":1,
                       "_meta":{"io.modelcontextprotocol/protocolVersion":"$LATEST_MODERN_VERSION",
                                "io.modelcontextprotocol/clientCapabilities":{}}}}
        """.trimIndent()
        val response = post(body, method = "notifications/cancelled")

        response.status shouldBe HttpStatusCode.Accepted
        response.bodyAsText() shouldBe ""
    }

    @Test
    fun `a session id offered on a request-scoped POST is ignored`() = testApplication {
        serve()

        val response = post(callTool("echo")) { header("mcp-session-id", "not-a-session") }

        // The revision has no sessions: an id is neither honoured nor echoed back.
        response.status shouldBe HttpStatusCode.OK
        response.headers["mcp-session-id"] shouldBe null
    }

    @Test
    fun `a handler that emits a notification commits the response to an event stream`() = testApplication {
        serve {
            sendLoggingMessage(
                LoggingMessageNotification(
                    LoggingMessageNotificationParams(
                        level = LoggingLevel.Warning,
                        data = JsonPrimitive("mid-flight"),
                    ),
                ),
            )
            CallToolResult(content = listOf(TextContent("echoed")))
        }

        // The stream stays open until the handler answers, so the body is read inside `execute`;
        // reading it afterwards would race the writer and see only what had arrived by then.
        client.preparePost("/mcp") {
            modernHeaders(method = "tools/call", name = "echo")
            setBody(callTool("echo", logLevel = LoggingLevel.Warning))
        }.execute { response ->
            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe ContentType.Text.EventStream
            response.headers["X-Accel-Buffering"] shouldBe "no"
            // What arrives after the commit is asserted over a real engine, in
            // `ModernStreamableHttpStreamTest`: this engine does not drive a streaming body
            // concurrently with the handler that is still filling it.
            response.bodyAsText() shouldContain "notifications/message"
        }
    }

    /** Registers a server exposing one `echo` tool at `/mcp`. */
    private fun ApplicationTestBuilder.serve(
        handler: suspend ClientConnection.(io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest) -> CallToolResult =
            { CallToolResult(content = listOf(TextContent("echoed"))) },
    ) {
        application {
            mcpStatelessStreamableHttp {
                Server(
                    Implementation("test-server", "1.0"),
                    ServerOptions(
                        capabilities = ServerCapabilities(
                            tools = ServerCapabilities.Tools(null),
                            resources = ServerCapabilities.Resources(subscribe = null, listChanged = null),
                            logging = EmptyJsonObject,
                        ),
                    ),
                ).apply {
                    addTool(
                        name = "echo",
                        description = "echoes",
                        inputSchema = ToolSchema(properties = EmptyJsonObject, required = null),
                        handler = handler,
                    )
                }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.post(
        body: String,
        method: String = "tools/call",
        name: String? = "echo",
        versionHeader: String = LATEST_MODERN_VERSION,
        extra: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = client.post("/mcp") {
        modernHeaders(method, name, versionHeader)
        setBody(body)
        extra()
    }

    private fun HttpRequestBuilder.modernHeaders(
        method: String,
        name: String?,
        versionHeader: String = LATEST_MODERN_VERSION,
    ) {
        header(HttpHeaders.Host, "localhost")
        header(HttpHeaders.Accept, "${ContentType.Application.Json}, ${ContentType.Text.EventStream}")
        header("MCP-Protocol-Version", versionHeader)
        header("Mcp-Method", method)
        if (NAME_BEARING_METHODS.containsKey(method) && name != null) header("Mcp-Name", name)
        contentType(ContentType.Application.Json)
    }

    private fun callTool(
        name: String,
        protocolVersion: String = LATEST_MODERN_VERSION,
        logLevel: LoggingLevel? = null,
    ): String {
        val level = logLevel?.let { ""","io.modelcontextprotocol/logLevel":"${it.name.lowercase()}"""" }.orEmpty()
        return """
            {"jsonrpc":"2.0","id":1,"method":"tools/call",
             "params":{"name":"$name","arguments":{},
                       "_meta":{"io.modelcontextprotocol/protocolVersion":"$protocolVersion",
                                "io.modelcontextprotocol/clientCapabilities":{}$level}}}
        """.trimIndent()
    }

    private fun request(id: Int, method: String): String = """
        {"jsonrpc":"2.0","id":$id,"method":"$method",
         "params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"$LATEST_MODERN_VERSION",
                            "io.modelcontextprotocol/clientCapabilities":{}}}}
    """.trimIndent()

    private suspend fun HttpResponse.errorCode(): Int = McpJson.decodeFromString<JSONRPCError>(bodyAsText()).error.code
}
