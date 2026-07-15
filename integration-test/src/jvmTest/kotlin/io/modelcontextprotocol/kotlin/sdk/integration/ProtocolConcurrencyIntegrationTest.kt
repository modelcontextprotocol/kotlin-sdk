package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.shared.InMemoryTransport
import io.modelcontextprotocol.kotlin.sdk.shared.currentRequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.SamplingMessage
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end tests for concurrent inbound dispatch enabled after the MCP handshake.
 *
 * Handlers run on a real [kotlinx.coroutines.Dispatchers.Default], so these use [runBlocking] with
 * explicit [withTimeout] guards. A virtual test clock would false-fire timeouts against cross-thread
 * real dispatchers.
 */
@OptIn(ExperimentalMcpApi::class)
class ProtocolConcurrencyIntegrationTest {

    private fun rootsClient(): Client = Client(
        clientInfo = Implementation(name = "test client", version = "1.0"),
        options = ClientOptions(
            capabilities = ClientCapabilities(roots = ClientCapabilities.Roots(listChanged = null)),
        ),
    )

    private fun toolsServer(): Server = Server(
        serverInfo = Implementation(name = "test server", version = "1.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = null)),
        ),
    )

    // ChannelTransport delivers inbound messages as soon as its read loop starts, which is before
    // start() marks it operational. Creating the session first keeps the client's initialize from
    // landing on a transport that would reject the session's response.
    private suspend fun connect(
        client: Client,
        clientTransport: ChannelTransport,
        server: Server,
        serverTransport: ChannelTransport,
    ): ServerSession {
        val serverSession = server.createSession(serverTransport)
        client.connect(clientTransport)
        return serverSession
    }

    // The motivating deadlock over real serial read loops: a server tool handler makes a
    // server-to-client request (roots/list) while still handling the client's tools/call.
    @Test
    fun `server tool handler can call roots list mid-handling without deadlock`() = runBlocking {
        val client = rootsClient()
        val server = toolsServer()
        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()

        val serverSession = connect(client, clientTransport, server, serverTransport)

        // Register the tools/call handler directly on the session so it can call back to the client
        // mid-handling. Before concurrent dispatch this would deadlock: the server read loop is
        // blocked by the tool handler and can never deliver the roots/list response.
        serverSession.setRequestHandler<CallToolRequest>(Method.Defined.ToolsCall) { _, _ ->
            val roots = serverSession.request<ListRootsResult>(ListRootsRequest())
            CallToolResult(content = listOf(TextContent(text = "roots=${roots.roots.size}")))
        }

        val result = withTimeout(10.seconds) {
            client.callTool(CallToolRequest(CallToolRequestParams("needs-roots")))
        }
        val text = (result as CallToolResult).content.filterIsInstance<TextContent>().single().text
        text shouldBe "roots=0"

        client.close()
        server.close()
    }

    // Client-side mirror: a client sampling handler makes a client-to-server request (tools/list)
    // while still handling the server's sampling/createMessage.
    @Test
    fun `client sampling handler can call the server mid-handling without deadlock`() = runBlocking {
        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(sampling = ClientCapabilities.Sampling()),
            ),
        )
        val server = toolsServer()
        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()

        client.setRequestHandler<CreateMessageRequest>(Method.Defined.SamplingCreateMessage) { _, _ ->
            val tools = client.request<ListToolsResult>(ListToolsRequest())
            CreateMessageResult(
                role = Role.Assistant,
                content = TextContent(text = "tools=${tools.tools.size}"),
                model = "test-model",
            )
        }

        val serverSession = connect(client, clientTransport, server, serverTransport)

        val result = withTimeout(10.seconds) {
            serverSession.request<CreateMessageResult>(
                CreateMessageRequest(
                    CreateMessageRequestParams(
                        maxTokens = 100,
                        messages = listOf(SamplingMessage(role = Role.User, content = TextContent(text = "hi"))),
                    ),
                ),
            )
        }
        val text = result.content.filterIsInstance<TextContent>().single().text
        text shouldBe "tools=0"

        client.close()
        server.close()
    }

    // Nested-cancel cascade: cancelling the client's tools/call must reach the server tool handler
    // over the wire and cascade into its nested roots/list request, cancelling the parked
    // client-side roots handler. No tools/call response is ever sent.
    @Test
    fun `cancelling a tool call cascades into its nested roots list request`() = runBlocking {
        val client = rootsClient()
        val server = toolsServer()
        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()

        val capturedToolCallId = AtomicReference<RequestId?>(null)
        val toolHandlerCancelled = CompletableDeferred<CancellationException>()
        val rootsHandlerEntered = CompletableDeferred<Unit>()
        val rootsHandlerCancelled = CompletableDeferred<CancellationException>()
        val parkForever = CompletableDeferred<ListRootsResult>()
        val unexpectedToolCallResponse = CompletableDeferred<Unit>()

        // Tap the server->client direction (client transport inbound) to prove no tools/call
        // response is ever delivered once the call is cancelled.
        clientTransport.onMessage { message ->
            if (message is JSONRPCResponse && message.id == capturedToolCallId.get()) {
                unexpectedToolCallResponse.complete(Unit)
            }
        }

        val serverSession = connect(client, clientTransport, server, serverTransport)

        // Client roots handler parks until cancelled and records the cancellation.
        client.setRequestHandler<ListRootsRequest>(Method.Defined.RootsList) { _, _ ->
            rootsHandlerEntered.complete(Unit)
            try {
                parkForever.await()
            } catch (e: CancellationException) {
                rootsHandlerCancelled.complete(e)
                throw e
            }
        }

        // Tool handler makes a nested server->client request and records its own cancellation.
        serverSession.setRequestHandler<CallToolRequest>(Method.Defined.ToolsCall) { _, _ ->
            val extra = currentRequestHandlerExtra()!!
            capturedToolCallId.set(extra.requestId)
            try {
                extra.sendRequest<ListRootsResult>(ListRootsRequest())
            } catch (e: CancellationException) {
                toolHandlerCancelled.complete(e)
                throw e
            }
            CallToolResult(content = listOf(TextContent(text = "unreachable")))
        }

        val call = launch { client.callTool(CallToolRequest(CallToolRequestParams("nested"))) }

        withTimeout(10.seconds) { rootsHandlerEntered.await() }
        call.cancel(CancellationException("user aborted"))

        withTimeout(10.seconds) { toolHandlerCancelled.await() }
        withTimeout(10.seconds) { rootsHandlerCancelled.await() }

        // The server suppresses the response for a cancelled request, so none is ever sent.
        assertFalse(
            unexpectedToolCallResponse.isCompleted,
            "no tools/call response should be delivered after the call was cancelled",
        )

        client.close()
        server.close()
    }

    // Init gate plus context element in a high-level handler: initialize and tools/list are
    // pipelined before notifications/initialized and answered serially in order. After the
    // handshake a high-level addTool handler can observe currentRequestHandlerExtra().
    @Test
    fun `pipelined initialize and tools list are processed serially then context element is available`(): Unit =
        runBlocking {
            val server = toolsServer()
            server.addTool(name = "ctx-tool", description = "returns its request id") {
                val extra = currentRequestHandlerExtra()
                checkNotNull(extra) { "currentRequestHandlerExtra() must be non-null inside a tool handler" }
                CallToolResult(content = listOf(TextContent(text = extra.requestId.asText())))
            }

            val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

            val arrivalOrder = CopyOnWriteArrayList<RequestId>()
            val pending = ConcurrentHashMap<RequestId, CompletableDeferred<JSONRPCMessage>>()
            clientTransport.onMessage { message ->
                val id = when (message) {
                    is JSONRPCResponse -> message.id
                    is JSONRPCError -> message.id
                    else -> null
                }
                if (id != null) {
                    arrivalOrder.add(id)
                    pending[id]?.complete(message)
                }
            }

            // Drive the client side manually over the raw transport.
            server.createSession(serverTransport)

            val initRequest = InitializeRequest(
                InitializeRequestParams(
                    protocolVersion = LATEST_PROTOCOL_VERSION,
                    capabilities = ClientCapabilities(),
                    clientInfo = Implementation(name = "raw-client", version = "1.0"),
                ),
            ).toJSON()
            val toolsListRequest = ListToolsRequest().toJSON()

            val initAnswered = pending.expect(initRequest.id)
            val toolsListAnswered = pending.expect(toolsListRequest.id)

            // Pipelined before notifications/initialized: both handled in the serial dispatch phase.
            clientTransport.send(initRequest)
            clientTransport.send(toolsListRequest)

            withTimeout(10.seconds) { initAnswered.await() }
            withTimeout(10.seconds) { toolsListAnswered.await() }

            // Deterministic serial ordering: initialize answered before tools/list.
            assertEquals(
                listOf(initRequest.id, toolsListRequest.id),
                arrivalOrder.toList(),
                "pipelined requests must be answered in arrival order",
            )

            // Complete the handshake, then call the high-level tool.
            clientTransport.send(InitializedNotification().toJSON())

            val toolCallRequest = CallToolRequest(CallToolRequestParams("ctx-tool")).toJSON()
            val toolCallAnswered = pending.expect(toolCallRequest.id)
            clientTransport.send(toolCallRequest)

            val toolCallResponse = withTimeout(10.seconds) { toolCallAnswered.await() } as JSONRPCResponse
            val text = (toolCallResponse.result as CallToolResult).content.filterIsInstance<TextContent>().single().text
            // The handler echoed its request id, proving currentRequestHandlerExtra() was available.
            text shouldBe toolCallRequest.id.asText()
        }

    private fun ConcurrentHashMap<RequestId, CompletableDeferred<JSONRPCMessage>>.expect(
        id: RequestId,
    ): CompletableDeferred<JSONRPCMessage> = CompletableDeferred<JSONRPCMessage>().also { put(id, it) }

    private fun RequestId.asText(): String = when (this) {
        is RequestId.StringId -> value
        is RequestId.NumberId -> value.toString()
    }
}
