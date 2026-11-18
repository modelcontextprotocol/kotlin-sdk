package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_MODERN_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.serverInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * One connection, both lifecycles, no memory between requests — the property the revision states for
 * stdio, where an open process is explicitly not a session and a client may interleave unrelated
 * requests freely.
 *
 * Driven over raw JSON-RPC messages rather than through
 * [io.modelcontextprotocol.kotlin.sdk.client.Client] because the typed client settles exactly one
 * lifecycle per connection: interleaving is something a conforming client does not do, so no
 * client-shaped API can produce it. The property under test is classification, not framing, so an
 * in-memory pair stands in for the pipe.
 */
@OptIn(ExperimentalMcpApi::class)
class EraInterleavingTest {

    private val hangCancelled = CompletableDeferred<Unit>()

    @Test
    fun `one connection answers a handshake request and a request-scoped request in turn`() = runBlocking {
        val peer = connect()

        val handshake = peer.call(id = 1, method = "tools/call", envelope = null)
        val scoped = peer.call(id = 2, method = "tools/call", envelope = LATEST_MODERN_VERSION)

        // Same connection, same tool, two lifecycles: the identity stamp is the observable
        // difference, and each request got the one its own body asked for.
        handshake.meta()?.serverInfo shouldBe null
        scoped.meta()?.serverInfo shouldBe Implementation("interleaving-server", "1.0")
        peer.close()
    }

    @Test
    fun `a request-scoped request does not inherit a log level an earlier request set`() = runBlocking {
        val peer = connect()

        // The handshake lifecycle's own way of asking for logs, which the revision replaced.
        peer.call(
            id = 1,
            method = "logging/setLevel",
            envelope = null,
            params = buildJsonObject {
                put("level", "debug")
            },
        )
        peer.call(id = 2, method = "tools/call", envelope = LATEST_MODERN_VERSION)

        // A level set for one request must not widen what any other request may emit; a
        // request-scoped request that did not opt in gets nothing.
        peer.notifications shouldContainExactly emptyList()
        peer.close()
    }

    @Test
    fun `a request-scoped request is answered while an earlier one is still running`() = runBlocking {
        val peer = connect()

        peer.send(id = 1, method = "tools/call", envelope = LATEST_MODERN_VERSION, params = hangCall())
        val listed = peer.call(id = 2, method = "tools/list", envelope = LATEST_MODERN_VERSION)

        listed.id shouldBe RequestId.NumberId(2)
        peer.close()
    }

    @Test
    fun `a running request-scoped request is cancelled by notifications cancelled`() = runBlocking {
        val peer = connect()

        peer.send(id = 1, method = "tools/call", envelope = LATEST_MODERN_VERSION, params = hangCall())
        peer.cancel(id = 1)

        withTimeout(5.seconds) { hangCancelled.await() }
        peer.close()
    }

    @Test
    fun `a request-scoped request is not narrowed by a log level an earlier request set`() = runBlocking {
        val peer = connect()

        peer.call(
            id = 1,
            method = "logging/setLevel",
            envelope = null,
            params = buildJsonObject {
                put("level", "error")
            },
        )
        peer.call(id = 2, method = "tools/call", envelope = LATEST_MODERN_VERSION, logLevel = "debug")

        // The request asked for debug and above on its own; no earlier request takes that away.
        peer.notifications.map { McpJson.decodeFromString<JSONRPCNotification>(it).method } shouldContainExactly
            listOf("notifications/message")
        peer.close()
    }

    /** A raw peer holding one connection to a server that logs while serving `echo`. */
    private class Peer(private val transport: ChannelTransport) {
        val notifications: MutableList<String> = mutableListOf()
        private val pending = ConcurrentHashMap<RequestId, CompletableDeferred<JSONRPCResponse>>()

        suspend fun start() {
            transport.onMessage { message ->
                when (message) {
                    is JSONRPCResponse -> pending.remove(message.id)?.complete(message)
                    else -> notifications += McpJson.encodeToString(message)
                }
            }
            transport.start()
        }

        suspend fun call(
            id: Int,
            method: String,
            envelope: String?,
            params: JsonObject = buildJsonObject { },
            logLevel: String? = null,
        ): JSONRPCResponse = withTimeout(5.seconds) { send(id, method, envelope, params, logLevel).await() }

        suspend fun send(
            id: Int,
            method: String,
            envelope: String?,
            params: JsonObject = buildJsonObject { },
            logLevel: String? = null,
        ): Deferred<JSONRPCResponse> {
            val requestId = RequestId.NumberId(id.toLong())
            val answer = CompletableDeferred<JSONRPCResponse>()
            pending[requestId] = answer
            transport.send(request(requestId, method, envelope, params, logLevel))
            return answer
        }

        suspend fun cancel(id: Int) = transport.send(
            JSONRPCNotification(
                method = "notifications/cancelled",
                params = buildJsonObject { put("requestId", id) },
            ),
        )

        suspend fun close() = transport.close()

        private fun request(
            id: RequestId,
            method: String,
            envelope: String?,
            params: JsonObject,
            logLevel: String?,
        ): JSONRPCMessage {
            val body = when (method) {
                "tools/call" -> JsonObject(
                    buildJsonObject {
                        put("name", "echo")
                        put("arguments", EmptyJsonObject)
                    } + params,
                )

                else -> params
            }
            val withMeta = if (envelope == null) {
                body
            } else {
                JsonObject(
                    body + (
                        "_meta" to buildJsonObject {
                            put("io.modelcontextprotocol/protocolVersion", envelope)
                            put("io.modelcontextprotocol/clientCapabilities", EmptyJsonObject)
                            logLevel?.let { put("io.modelcontextprotocol/logLevel", it) }
                        }
                        ),
                )
            }
            return JSONRPCRequest(id = id, method = method, params = withMeta)
        }
    }

    private suspend fun connect(): Peer {
        val server = Server(
            serverInfo = Implementation("interleaving-server", "1.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(null),
                    logging = EmptyJsonObject,
                ),
            ),
        )
        server.addTool(
            name = "echo",
            description = "echoes, noisily",
            inputSchema = ToolSchema(properties = EmptyJsonObject, required = null),
        ) {
            sendLoggingMessage(
                LoggingMessageNotification(
                    LoggingMessageNotificationParams(
                        level = LoggingLevel.Debug,
                        data = JsonPrimitive("serving echo"),
                    ),
                ),
            )
            CallToolResult(content = listOf(TextContent("echoed")))
        }
        server.addTool(
            name = "hang",
            description = "runs until cancelled",
            inputSchema = ToolSchema(properties = EmptyJsonObject, required = null),
        ) {
            try {
                awaitCancellation()
            } finally {
                hangCancelled.complete(Unit)
            }
        }

        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()
        server.createSession(serverTransport)
        return Peer(clientTransport).also { it.start() }
    }

    private fun JSONRPCResponse.meta() = result.meta

    private fun hangCall() = buildJsonObject { put("name", "hang") }
}
