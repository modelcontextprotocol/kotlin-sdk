package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.ListPromptsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListPromptsResult
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesResult
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedResult
import io.modelcontextprotocol.kotlin.sdk.types.Prompt
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import io.modelcontextprotocol.kotlin.sdk.types.Resource
import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

/**
 * Pages are keyed by the cursor of the incoming request (`null` for the first page). A page of `null`
 * means "never answer" and an exception turns into a JSON-RPC error response.
 */
private class PagedTransport(private val pages: Map<String, Map<String?, () -> PaginatedResult?>>) : Transport {
    val requestedCursors = mutableMapOf<String, MutableList<String?>>()
    val requestedMeta = mutableListOf<JsonElement?>()
    private var onMessageBlock: (suspend (JSONRPCMessage) -> Unit)? = null

    override suspend fun start() = Unit

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        if (message !is JSONRPCRequest) return
        if (message.method == "initialize") {
            val capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(),
                prompts = ServerCapabilities.Prompts(),
                resources = ServerCapabilities.Resources(),
            )
            onMessageBlock?.invoke(
                JSONRPCResponse(
                    id = message.id,
                    result = InitializeResult(
                        protocolVersion = LATEST_PROTOCOL_VERSION,
                        capabilities = capabilities,
                        serverInfo = Implementation("paged-server", "1.0.0"),
                    ),
                ),
            )
            return
        }
        val cursor = (message.params as? JsonObject)?.get("cursor")?.jsonPrimitive?.contentOrNull
        requestedMeta += (message.params as? JsonObject)?.get("_meta")
        requestedCursors.getOrPut(message.method) { mutableListOf() } += cursor
        val page = try {
            pages.getValue(message.method).getValue(cursor)()
        } catch (e: IllegalStateException) {
            onMessageBlock?.invoke(
                JSONRPCError(message.id, RPCError(RPCError.ErrorCode.INTERNAL_ERROR, e.message.orEmpty())),
            )
            return
        } ?: return
        onMessageBlock?.invoke(JSONRPCResponse(id = message.id, result = page))
    }

    override suspend fun close() = Unit

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        onMessageBlock = block
    }

    override fun onClose(block: () -> Unit) = Unit

    override fun onError(block: (Throwable) -> Unit) = Unit
}

private fun tool(name: String) = Tool(name = name, inputSchema = ToolSchema())

private fun toolPage(vararg names: String, next: String? = null): () -> PaginatedResult? =
    { ListToolsResult(tools = names.map(::tool), nextCursor = next) }

@OptIn(ExperimentalCoroutinesApi::class)
class ClientListFlowTest {

    private suspend fun connect(transport: Transport): Client =
        Client(Implementation("test-client", "1.0.0")).apply { connect(transport) }

    private fun toolsTransport(pages: Map<String?, () -> PaginatedResult?>) =
        PagedTransport(mapOf("tools/list" to pages))

    @Test
    fun `toolsFlow should emit tools from all pages in order`() = runTest {
        val transport = toolsTransport(
            mapOf(
                null to toolPage("a", "b", next = "p2"),
                "p2" to toolPage("c", next = "p3"),
                "p3" to toolPage("d", "e"),
            ),
        )
        val client = connect(transport)

        client.toolsFlow().toList().map { it.name } shouldBe listOf("a", "b", "c", "d", "e")

        transport.requestedCursors.getValue("tools/list") shouldBe listOf(null, "p2", "p3")
    }

    @Test
    fun `toolsFlow should emit a single page without a second request`() = runTest {
        val transport = toolsTransport(mapOf(null to toolPage("only")))
        val client = connect(transport)

        client.toolsFlow().toList().map { it.name } shouldBe listOf("only")

        transport.requestedCursors.getValue("tools/list") shouldBe listOf(null)
    }

    @Test
    fun `toolsFlow should emit nothing for an empty result`() = runTest {
        val transport = toolsTransport(mapOf(null to toolPage()))
        val client = connect(transport)

        client.toolsFlow().toList().shouldBeEmpty()
    }

    @Test
    fun `toolsFlow should be cold and send nothing until collected`() = runTest {
        val transport = toolsTransport(mapOf(null to toolPage("a")))
        val client = connect(transport)

        val flow = client.toolsFlow()

        transport.requestedCursors.isEmpty() shouldBe true
        flow.toList().size shouldBe 1
        flow.toList().size shouldBe 1
        transport.requestedCursors.getValue("tools/list") shouldBe listOf(null, null)
    }

    @Test
    fun `toolsFlow should not request further pages once the collector is cancelled`() = runTest {
        val transport = toolsTransport(
            mapOf(
                null to toolPage("a", "b", next = "p2"),
                "p2" to toolPage("c"),
            ),
        )
        val client = connect(transport)
        val received = mutableListOf<String>()

        val job = launch {
            client.toolsFlow().collect {
                received += it.name
                currentCoroutineContext().cancel()
            }
        }
        job.join()

        received shouldBe listOf("a")
        transport.requestedCursors.getValue("tools/list") shouldBe listOf(null)
    }

    @Test
    fun `toolsFlow should stop requesting pages after take`() = runTest {
        val transport = toolsTransport(
            mapOf(
                null to toolPage("a", next = "p2"),
                "p2" to toolPage("b", next = "p3"),
                "p3" to toolPage("c"),
            ),
        )
        val client = connect(transport)

        client.toolsFlow().take(2).toList().map { it.name } shouldBe listOf("a", "b")
        client.toolsFlow().first().name shouldBe "a"

        transport.requestedCursors.getValue("tools/list") shouldBe listOf(null, "p2", null)
    }

    @Test
    fun `toolsFlow should cancel an in-flight page request`() = runTest {
        val transport = toolsTransport(
            mapOf(
                null to toolPage("a", next = "p2"),
                "p2" to { null },
            ),
        )
        val client = connect(transport)
        val received = mutableListOf<String>()

        val job = launch { client.toolsFlow().collect { received += it.name } }
        runCurrent()
        job.cancel()
        job.join()

        received shouldBe listOf("a")
        job.isCancelled shouldBe true
        transport.requestedCursors.getValue("tools/list") shouldBe listOf(null, "p2")
    }

    @Test
    fun `toolsFlow should propagate an error from the second page after emitting the first`() = runTest {
        val transport = toolsTransport(
            mapOf(
                null to toolPage("a", next = "p2"),
                "p2" to { error("boom") },
            ),
        )
        val client = connect(transport)
        val received = mutableListOf<String>()

        val error = shouldThrow<McpException> { client.toolsFlow().collect { received += it.name } }

        error.message shouldContain "boom"
        received shouldBe listOf("a")
    }

    @Test
    fun `toolsFlow should fail when the server repeats a cursor`() = runTest {
        val transport = toolsTransport(
            mapOf(
                null to toolPage("a", next = "p2"),
                "p2" to toolPage("b", next = "p2"),
            ),
        )
        val client = connect(transport)
        val received = mutableListOf<String>()

        val error = shouldThrow<IllegalStateException> { client.toolsFlow().collect { received += it.name } }

        error.message shouldContain "p2"
        received shouldBe listOf("a", "b")
        transport.requestedCursors.getValue("tools/list") shouldBe listOf(null, "p2")
    }

    @Test
    fun `toolsFlow should fail when the server returns to an earlier cursor`() = runTest {
        val transport = toolsTransport(
            mapOf(
                null to toolPage("a", next = "p2"),
                "p2" to toolPage("b", next = "p3"),
                "p3" to toolPage("c", next = "p2"),
            ),
        )
        val client = connect(transport)

        shouldThrow<IllegalStateException> { client.toolsFlow().toList() }

        transport.requestedCursors.getValue("tools/list") shouldBe listOf(null, "p2", "p3")
    }

    @Test
    fun `toolsFlow should start from the cursor of the given request and send its meta with every page`() = runTest {
        val transport = toolsTransport(
            mapOf(
                "start" to toolPage("a", next = "p2"),
                "p2" to toolPage("b"),
            ),
        )
        val client = connect(transport)

        val meta = RequestMeta(buildJsonObject { put("trace", "t-1") })
        val request = ListToolsRequest(PaginatedRequestParams(cursor = "start", meta = meta))

        client.toolsFlow(request).toList().map { it.name } shouldBe listOf("a", "b")

        transport.requestedMeta shouldBe List(2) { meta.json }

        transport.requestedCursors.getValue("tools/list") shouldBe listOf("start", "p2")
    }

    @Test
    fun `promptsFlow should emit prompts from all pages in order`() = runTest {
        val transport = PagedTransport(
            mapOf(
                "prompts/list" to mapOf(
                    null to { ListPromptsResult(listOf(Prompt("p1")), nextCursor = "c2") },
                    "c2" to { ListPromptsResult(listOf(Prompt("p2"), Prompt("p3"))) },
                ),
            ),
        )
        val client = connect(transport)

        client.promptsFlow(ListPromptsRequest()).toList().map { it.name } shouldBe listOf("p1", "p2", "p3")

        transport.requestedCursors.getValue("prompts/list") shouldBe listOf(null, "c2")
    }

    @Test
    fun `resourcesFlow should emit resources from all pages in order`() = runTest {
        val transport = PagedTransport(
            mapOf(
                "resources/list" to mapOf(
                    null to { ListResourcesResult(listOf(Resource("file:///1", "r1")), nextCursor = "c2") },
                    "c2" to { ListResourcesResult(listOf(Resource("file:///2", "r2"))) },
                ),
            ),
        )
        val client = connect(transport)

        client.resourcesFlow().toList().map { it.name } shouldBe listOf("r1", "r2")

        transport.requestedCursors.getValue("resources/list") shouldBe listOf(null, "c2")
    }

    @Test
    fun `resourceTemplatesFlow should emit resource templates from all pages in order`() = runTest {
        val transport = PagedTransport(
            mapOf(
                "resources/templates/list" to mapOf(
                    null to {
                        ListResourceTemplatesResult(listOf(ResourceTemplate("file:///{a}", "t1")), nextCursor = "c2")
                    },
                    "c2" to { ListResourceTemplatesResult(listOf(ResourceTemplate("file:///{b}", "t2"))) },
                ),
            ),
        )
        val client = connect(transport)

        client.resourceTemplatesFlow().toList().map { it.name } shouldBe listOf("t1", "t2")

        transport.requestedCursors.getValue("resources/templates/list") shouldBe listOf(null, "c2")
    }
}
