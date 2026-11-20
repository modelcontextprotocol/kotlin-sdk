package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.shared.RequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.shared.currentRequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_HANDSHAKE_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_MODERN_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * What a server does with a request-scoped `_meta` envelope whose **optional** fields are malformed
 * while its required ones are intact.
 *
 * The revision does not treat the two optional fields alike, so neither does this:
 * `io.modelcontextprotocol/clientInfo` is self-reported, unverified and for display only, and a
 * receiver **SHOULD NOT** change behaviour on account of it; `io.modelcontextprotocol/logLevel`
 * decides what the server may emit, and a receiver **SHOULD** answer `-32602` for a level it does
 * not recognize rather than guess.
 *
 * Driven over raw JSON-RPC rather than through [io.modelcontextprotocol.kotlin.sdk.client.Client]
 * because the typed client builds its envelope from typed values and cannot express a malformed one:
 * this is a property of what the server accepts from a foreign peer, which is exactly where the
 * revision's optional fields get sent imperfectly.
 */
@OptIn(ExperimentalMcpApi::class)
class MalformedEnvelopeFieldTest {

    private val declared = ClientCapabilities(sampling = ClientCapabilities.Sampling())

    @Test
    fun `a malformed client identity leaves the rest of the envelope in force`() = runBlocking {
        val seen = CompletableDeferred<RequestHandlerExtra>()
        val peer = connect(seen)

        // Well-formed required fields; an identity missing the `version` the schema requires.
        val answer = peer.call(
            id = 1,
            meta = buildJsonObject {
                put("io.modelcontextprotocol/protocolVersion", LATEST_MODERN_VERSION)
                put("io.modelcontextprotocol/clientCapabilities", McpJson.encodeToJsonElement(declared))
                put(
                    "io.modelcontextprotocol/clientInfo",
                    buildJsonObject { put("name", "no-version-client") },
                )
            },
        )

        answer.shouldBeSuccess()
        val extra = withTimeout(5.seconds) { seen.await() }

        // Identity degrades to not-supplied, which is what an absent value already means...
        extra.clientInfo shouldBe null
        // ...and nothing else does. Losing these is what silently demoted the request to the
        // connection-scoped lifecycle: capabilities the peer declared read as undeclared, and the
        // version fell back far enough that the log gate below inverted.
        extra.envelope.shouldNotBeNull()
        extra.protocolVersion shouldBe LATEST_MODERN_VERSION
        extra.clientCapabilities shouldBe declared

        peer.close()
    }

    @Test
    fun `a malformed client identity does not reopen log delivery`() = runBlocking {
        val seen = CompletableDeferred<RequestHandlerExtra>()
        val peer = connect(seen)

        peer.call(
            id = 1,
            meta = buildJsonObject {
                put("io.modelcontextprotocol/protocolVersion", LATEST_MODERN_VERSION)
                put("io.modelcontextprotocol/clientCapabilities", McpJson.encodeToJsonElement(declared))
                put("io.modelcontextprotocol/clientInfo", JsonPrimitive("not-an-implementation"))
            },
        )

        // The request opted into no level, so it gets nothing. Read after the awaited call over one
        // ordered in-memory stream, so this list is the whole delivery.
        peer.notifications.shouldContainExactly(emptyList())
        peer.close()
    }

    @Test
    fun `a request-scoped request never borrows an earlier handshake declaration`() = runBlocking {
        val seen = CompletableDeferred<RequestHandlerExtra>()
        val peer = connect(seen)

        // One connection carrying both lifecycles, which the revision states for stdio: an open
        // process is not a session. The handshake declares capabilities the envelope below does not.
        peer.initialize(
            id = 1,
            capabilities = ClientCapabilities(
                roots = ClientCapabilities.Roots(),
                elicitation = ClientCapabilities.Elicitation(),
            ),
        )

        peer.call(
            id = 2,
            meta = buildJsonObject {
                put("io.modelcontextprotocol/protocolVersion", LATEST_MODERN_VERSION)
                put("io.modelcontextprotocol/clientCapabilities", McpJson.encodeToJsonElement(declared))
                put(
                    "io.modelcontextprotocol/clientInfo",
                    buildJsonObject { put("name", "no-version-client") },
                )
            },
        )

        // Only what this request declared. The handshake's roots and elicitation are another
        // request's business, and on this transport they are sitting right there to be borrowed.
        val extra = withTimeout(5.seconds) { seen.await() }
        extra.clientCapabilities shouldBe declared
        extra.protocolVersion shouldBe LATEST_MODERN_VERSION
        extra.clientInfo shouldBe null

        peer.close()
    }

    @Test
    fun `a log level naming no known severity is rejected`() = runBlocking {
        val peer = connect(CompletableDeferred())

        listOf(JsonPrimitive("verbose"), JsonPrimitive(7), EmptyJsonObject).forEach { level ->
            val answer = peer.call(
                id = 1,
                meta = buildJsonObject {
                    put("io.modelcontextprotocol/protocolVersion", LATEST_MODERN_VERSION)
                    put("io.modelcontextprotocol/clientCapabilities", McpJson.encodeToJsonElement(declared))
                    put("io.modelcontextprotocol/logLevel", level)
                },
            )

            val error = (answer as JSONRPCError).error
            error.code shouldBe RPCError.ErrorCode.INVALID_PARAMS
            // Naming the key is what lets the peer fix the field rather than guess at the envelope.
            (error.message.contains("io.modelcontextprotocol/logLevel")) shouldBe true
        }

        peer.close()
    }

    private fun JSONRPCMessage.shouldBeSuccess() {
        if (this is JSONRPCError) {
            throw AssertionError("Expected the request to be served, but it answered ${error.code}: ${error.message}")
        }
    }

    /** A raw peer holding one connection to a server that logs while serving `echo`. */
    private class Peer(private val transport: ChannelTransport) {
        val notifications: MutableList<String> = mutableListOf()
        private val pending = mutableMapOf<RequestId, CompletableDeferred<JSONRPCMessage>>()

        suspend fun start() {
            transport.onMessage { message ->
                when (message) {
                    is JSONRPCResponse -> pending.remove(message.id)?.complete(message)
                    is JSONRPCError -> message.id?.let { pending.remove(it)?.complete(message) }
                    else -> notifications += McpJson.encodeToString(message)
                }
            }
            transport.start()
        }

        /** Settles the connection-scoped lifecycle, so its declaration is there to be borrowed. */
        suspend fun initialize(id: Int, capabilities: ClientCapabilities): JSONRPCMessage {
            val requestId = RequestId.NumberId(id.toLong())
            val answer = CompletableDeferred<JSONRPCMessage>()
            pending[requestId] = answer
            transport.send(
                JSONRPCRequest(
                    id = requestId,
                    method = "initialize",
                    params = buildJsonObject {
                        put("protocolVersion", LATEST_HANDSHAKE_VERSION)
                        put("capabilities", McpJson.encodeToJsonElement(capabilities))
                        put(
                            "clientInfo",
                            McpJson.encodeToJsonElement(Implementation("handshake-client", "1.0")),
                        )
                    },
                ),
            )
            return withTimeout(5.seconds) { answer.await() }
        }

        suspend fun call(id: Int, meta: JsonObject): JSONRPCMessage {
            val requestId = RequestId.NumberId(id.toLong())
            val answer = CompletableDeferred<JSONRPCMessage>()
            pending[requestId] = answer
            transport.send(
                JSONRPCRequest(
                    id = requestId,
                    method = "tools/call",
                    params = buildJsonObject {
                        put("name", "echo")
                        put("arguments", EmptyJsonObject)
                        put("_meta", meta)
                    },
                ),
            )
            return withTimeout(5.seconds) { answer.await() }
        }

        suspend fun close() = transport.close()
    }

    private suspend fun connect(seen: CompletableDeferred<RequestHandlerExtra>): Peer {
        val server = Server(
            serverInfo = Implementation("envelope-server", "1.0"),
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
            currentRequestHandlerExtra()?.let(seen::complete)
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

        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()
        server.createSession(serverTransport)
        return Peer(clientTransport).also { it.start() }
    }
}
