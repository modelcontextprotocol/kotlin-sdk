package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_MODERN_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import io.ktor.client.engine.cio.CIO as ClientCIO

/**
 * What a request-scoped response stream carries once it commits to `text/event-stream`: the
 * handler's notifications, then the response that ends it.
 *
 * Runs against a real CIO server and client rather than the in-process test engine, which does not
 * drive a streaming body concurrently with the handler still filling it — the very concurrency this
 * asserts. The commit itself (status, media type, `X-Accel-Buffering`) is covered in-process by
 * `ModernStreamableHttpTest`.
 */
@OptIn(ExperimentalMcpApi::class)
class ModernStreamableHttpStreamTest {

    @Test
    fun `a committed stream carries the handler's notifications and then its response`(): Unit =
        runBlocking(Dispatchers.IO) {
            val server = embeddedServer(CIO, port = 0) {
                mcpStatelessStreamableHttp {
                    Server(
                        Implementation("stream-server", "1.0"),
                        ServerOptions(
                            capabilities = ServerCapabilities(
                                tools = ServerCapabilities.Tools(null),
                                logging = EmptyJsonObject,
                            ),
                        ),
                    ).apply {
                        addTool(
                            name = "echo",
                            description = "echoes, noisily",
                            inputSchema = ToolSchema(properties = EmptyJsonObject, required = null),
                        ) {
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
                    }
                }
            }.start(wait = false)

            val client = HttpClient(ClientCIO)
            try {
                val port = server.engine.resolvedConnectors().first().port
                client.preparePost("http://localhost:$port/mcp") {
                    header(HttpHeaders.Host, "localhost")
                    header(HttpHeaders.Accept, "${ContentType.Application.Json}, ${ContentType.Text.EventStream}")
                    header("MCP-Protocol-Version", LATEST_MODERN_VERSION)
                    header("Mcp-Method", "tools/call")
                    header("Mcp-Name", "echo")
                    contentType(ContentType.Application.Json)
                    setBody(callEcho())
                }.execute { response ->
                    response.status shouldBe HttpStatusCode.OK
                    response.contentType()?.withoutParameters() shouldBe ContentType.Text.EventStream

                    // Read to completion: the stream ends when the response is written, so the body
                    // is the whole exchange rather than whatever had arrived at some instant.
                    val body = response.bodyAsText()
                    body shouldContain "notifications/message"
                    body shouldContain "echoed"
                    // Ordering is the point of a response stream: the notifications belong to the
                    // request being answered, and the answer is what closes it.
                    (body.indexOf("notifications/message") < body.indexOf("echoed")) shouldBe true
                }
            } finally {
                client.close()
                server.stop(1000, 2000)
            }
        }

    private fun callEcho(): String = """
        {"jsonrpc":"2.0","id":1,"method":"tools/call",
         "params":{"name":"echo","arguments":{},
                   "_meta":{"io.modelcontextprotocol/protocolVersion":"$LATEST_MODERN_VERSION",
                            "io.modelcontextprotocol/clientCapabilities":{},
                            "io.modelcontextprotocol/logLevel":"warning"}}}
    """.trimIndent()
}
