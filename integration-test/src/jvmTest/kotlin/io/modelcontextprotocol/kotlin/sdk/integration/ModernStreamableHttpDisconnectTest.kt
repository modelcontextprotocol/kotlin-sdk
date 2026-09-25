package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
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
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.Socket
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Closing the response stream is the request-scoped wire's only cancellation channel, so the
 * server must notice a client disconnect while the handler is still running — including inside the
 * SSE deferral window, where nothing is written to the socket that could fail.
 *
 * Runs against a real CIO server: the in-process test engine has no socket to close, so it cannot
 * express a disconnect at all.
 */
@OptIn(ExperimentalMcpApi::class)
class ModernStreamableHttpDisconnectTest {

    @Test
    fun `a client disconnect during the deferral window cancels the handler and closes the session`(): Unit =
        runBlocking(Dispatchers.IO) {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val mcpServer = hangingServer(started, cancelled)
            val server = embeddedServer(CIO, port = 0) {
                mcpStatelessStreamableHttp { mcpServer }
            }.start(wait = false)

            try {
                val port = server.engine.resolvedConnectors().first().port
                Socket("localhost", port).use { socket ->
                    socket.getOutputStream().run {
                        write(post(callTool("hang"), name = "hang").encodeToByteArray())
                        flush()
                    }
                    withTimeout(5.seconds) { started.await() }
                }
                // The socket is closed with the handler parked mid-request and the response
                // uncommitted: only the engine's disconnect signal can end the exchange now — a
                // write failure never will, because nothing is being written.
                withTimeout(5.seconds) { cancelled.await() }
                eventually(5.seconds) { mcpServer.sessions.shouldBeEmpty() }
            } finally {
                server.stop(1000, 2000)
            }
        }

    @Test
    fun `a request that closes its own connection still gets its full response`(): Unit = runBlocking(Dispatchers.IO) {
        // CIO fires the same close signal early for a request that asks for connection close,
        // while the handler is still running. That signal must not be read as a disconnect: the
        // shape is what a default nginx proxy_pass sends upstream.
        val server = embeddedServer(CIO, port = 0) {
            mcpStatelessStreamableHttp { slowServer() }
        }.start(wait = false)

        try {
            val port = server.engine.resolvedConnectors().first().port
            val body = Socket("localhost", port).use { socket ->
                socket.getOutputStream().run {
                    write(post(callTool("slow"), name = "slow", connection = "close").encodeToByteArray())
                    flush()
                }
                withTimeout(10.seconds) {
                    withContext(Dispatchers.IO) { socket.getInputStream().readBytes().decodeToString() }
                }
            }
            body shouldContain "200 OK"
            body shouldContain "slowed"
        } finally {
            server.stop(1000, 2000)
        }
    }

    private fun hangingServer(started: CompletableDeferred<Unit>, cancelled: CompletableDeferred<Unit>): Server =
        toolServer("hang") {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }

    private fun slowServer(): Server = toolServer("slow") {
        // Long enough that the engine's early close signal — fired as soon as the request parses —
        // lands while the handler is still suspended; the assertion is on the response, not time.
        delay(1.seconds)
        CallToolResult(content = listOf(TextContent("slowed")))
    }

    private fun toolServer(name: String, block: suspend () -> CallToolResult): Server = Server(
        Implementation("disconnect-server", "1.0"),
        ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(null))),
    ).apply {
        addTool(
            name = name,
            description = "test tool",
            inputSchema = ToolSchema(properties = EmptyJsonObject, required = null),
        ) { block() }
    }

    private fun callTool(name: String): String = """
        {"jsonrpc":"2.0","id":1,"method":"tools/call",
         "params":{"name":"$name","arguments":{},
                   "_meta":{"io.modelcontextprotocol/protocolVersion":"$LATEST_MODERN_VERSION",
                            "io.modelcontextprotocol/clientCapabilities":{}}}}
    """.trimIndent()

    private fun post(body: String, name: String, connection: String? = null): String = buildString {
        append("POST /mcp HTTP/1.1\r\n")
        append("Host: localhost\r\n")
        append("Content-Type: application/json\r\n")
        append("Accept: application/json, text/event-stream\r\n")
        append("MCP-Protocol-Version: $LATEST_MODERN_VERSION\r\n")
        append("Mcp-Method: tools/call\r\n")
        append("Mcp-Name: $name\r\n")
        connection?.let { append("Connection: $it\r\n") }
        append("Content-Length: ${body.encodeToByteArray().size}\r\n")
        append("\r\n")
        append(body)
    }
}
