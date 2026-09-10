package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.VersionNegotiationMode
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.shared.RequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.shared.currentRequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_HANDSHAKE_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_MODERN_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.MODERN_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.serverInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * End-to-end behaviour of the per-request `_meta` envelope of protocol revision 2026-07-28
 * (SEP-2575), driven through [Client] and [Server] over an in-memory transport pair.
 *
 * Every assertion is spec-mandated unless its own comment says otherwise. The two lifecycles are
 * exercised side by side on purpose: most of this contract is about which of the two a request is
 * served under, so a claim about one is only meaningful against the other.
 */
@OptIn(ExperimentalMcpApi::class)
class RequestScopedLifecycleTest {

    private val clientInfo = Implementation(name = "probe-client", version = "3.1")

    private val serverInfo = Implementation(name = "probe-server", version = "9.9")

    private val clientCapabilities = ClientCapabilities(roots = ClientCapabilities.Roots())

    @Test
    fun `a request-scoped request carries the client identity and capabilities to the handler`() = runBlocking {
        val seen = CompletableDeferred<RequestHandlerExtra>()
        val client = connect(VersionNegotiationMode.Auto) { request ->
            request.params.name shouldBe "echo"
            seen.complete(checkNotNull(currentRequestHandlerExtra()))
            ok()
        }

        client.callTool("echo", emptyMap())

        val extra = seen.await()
        extra.envelope?.protocolVersion shouldBe LATEST_MODERN_VERSION
        extra.protocolVersion shouldBe LATEST_MODERN_VERSION
        extra.clientCapabilities shouldBe clientCapabilities
        extra.clientInfo shouldBe clientInfo
        client.close()
    }

    @Test
    fun `a handshake request reports the negotiated version and the declared capabilities`() = runBlocking {
        val seen = CompletableDeferred<RequestHandlerExtra>()
        val client = connect(VersionNegotiationMode.Legacy) {
            seen.complete(checkNotNull(currentRequestHandlerExtra()))
            ok()
        }

        client.callTool("echo", emptyMap())

        val extra = seen.await()
        // An absent envelope is what distinguishes the lifecycles; the derived accessors are
        // populated either way, which is what lets a tool author write one implementation.
        extra.envelope.shouldBeNull()
        extra.protocolVersion shouldBe LATEST_HANDSHAKE_VERSION
        extra.clientCapabilities shouldBe clientCapabilities
        extra.clientInfo shouldBe clientInfo
        client.close()
    }

    @Test
    fun `a request-scoped result identifies the server in its metadata`() = runBlocking {
        val client = connect(VersionNegotiationMode.Auto) { ok() }

        val result = client.callTool("echo", emptyMap())

        result?.meta?.serverInfo shouldBe serverInfo
        client.close()
    }

    @Test
    fun `a handshake result carries no server identity`() = runBlocking {
        val client = connect(VersionNegotiationMode.Legacy) { ok() }

        val result = client.callTool("echo", emptyMap())

        // The handshake reports the identity once, in its own result; stamping it onto every result
        // would change a wire this SDK has always emitted.
        result?.meta?.serverInfo.shouldBeNull()
        client.close()
    }

    @Test
    fun `discovery advertises the request-scoped revisions and the server capabilities`() = runBlocking {
        val client = connect(VersionNegotiationMode.Auto) { ok() }

        val discovered = client.discover()

        discovered.supportedVersions shouldContainExactly MODERN_PROTOCOL_VERSIONS
        discovered.capabilities.tools shouldBe ServerCapabilities.Tools(null)
        discovered.meta?.serverInfo shouldBe serverInfo
        client.protocolVersion shouldBe LATEST_MODERN_VERSION
        client.close()
    }

    @Test
    fun `a method the revision removed is refused on a request-scoped connection`() = runBlocking {
        val client = connect(VersionNegotiationMode.Auto) { ok() }

        // `resources/subscribe` is one of the six the revision removed. Refused before the
        // transport, because the connection's revision has no such method to answer it.
        val error = assertFailsWith<McpException> {
            client.subscribeResource(SubscribeRequest(SubscribeRequestParams(uri = "test://thing")))
        }

        error.code shouldBe RPCError.ErrorCode.METHOD_NOT_FOUND
        client.close()
    }

    @Test
    fun `a server asking a request-scoped client for roots is refused`() = runBlocking {
        val refusal = CompletableDeferred<McpException>()
        val client = connect(VersionNegotiationMode.Auto) {
            refusal.complete(assertFailsWith { listRoots() })
            ok()
        }

        client.callTool("echo", emptyMap())

        // Server-initiated requests are replaced by multi-round-trip requests, which this SDK does
        // not implement — so this refusal is what a server author meets, and it must be legible.
        refusal.await().code shouldBe RPCError.ErrorCode.METHOD_NOT_FOUND
        client.close()
    }

    @Test
    fun `a request-scoped request that did not opt in receives no log notifications`() = runBlocking {
        val logs = mutableListOf<LoggingLevel>()
        val client = connect(VersionNegotiationMode.Auto, onLog = { logs += it }) {
            sendLoggingMessage(log(LoggingLevel.Warning))
            ok()
        }

        client.callTool("echo", emptyMap())

        // Emitted while serving the awaited call, over one ordered in-memory stream, so the list
        // read afterwards is the whole delivery.
        logs.shouldContainExactly()
        client.close()
    }

    @Test
    fun `a request-scoped request that opted in receives logs at and above its level`() = runBlocking {
        val logs = mutableListOf<LoggingLevel>()
        val client = connect(
            negotiation = VersionNegotiationMode.Auto,
            logLevel = LoggingLevel.Warning,
            onLog = { logs += it },
        ) {
            sendLoggingMessage(log(LoggingLevel.Debug))
            sendLoggingMessage(log(LoggingLevel.Warning))
            ok()
        }

        client.callTool("echo", emptyMap())

        logs.shouldContainExactly(LoggingLevel.Warning)
        client.close()
    }

    private fun ok() = CallToolResult(content = listOf(TextContent("ok")))

    private fun log(level: LoggingLevel) = LoggingMessageNotification(
        LoggingMessageNotificationParams(level = level, data = JsonPrimitive("entry")),
    )

    /**
     * Serves one `echo` tool backed by [handler] and returns a client connected to it, having
     * settled its lifecycle through [negotiation].
     */
    private suspend fun connect(
        negotiation: VersionNegotiationMode,
        logLevel: LoggingLevel? = null,
        onLog: (LoggingLevel) -> Unit = {},
        handler: suspend ClientConnection.(CallToolRequest) -> CallToolResult,
    ): Client {
        val server = Server(
            serverInfo = serverInfo,
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(null),
                    resources = ServerCapabilities.Resources(subscribe = true, listChanged = null),
                    logging = EmptyJsonObject,
                ),
            ),
        )
        server.addTool(
            name = "echo",
            description = "echoes",
            inputSchema = ToolSchema(properties = EmptyJsonObject, required = null),
            handler = handler,
        )

        val client = Client(
            clientInfo = clientInfo,
            options = ClientOptions(
                capabilities = clientCapabilities,
                versionNegotiation = negotiation,
                logLevel = logLevel,
            ),
        )
        client.setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) {
            onLog(it.params.level)
            CompletableDeferred(Unit)
        }

        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()
        runBlocking {
            launch { server.createSession(serverTransport) }
            launch { client.connect(clientTransport) }
        }
        return client
    }
}
