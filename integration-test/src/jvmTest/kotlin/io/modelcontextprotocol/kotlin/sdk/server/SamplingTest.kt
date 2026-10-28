package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.IncludeContext
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.SamplingMessage
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.StopReason
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolChoice
import io.modelcontextprotocol.kotlin.sdk.types.ToolResultContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.ToolUseContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test

class SamplingTest {

    private val weatherTool = Tool(
        name = "get_weather",
        description = "Return the current temperature in Celsius.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("location", buildJsonObject { put("type", JsonPrimitive("string")) })
            },
            required = listOf("location"),
        ),
    )

    private val minimalParams = CreateMessageRequestParams(
        maxTokens = 100,
        messages = listOf(SamplingMessage(Role.User, TextContent("hi"))),
    )

    @Test
    fun `tools and toolChoice should be rejected when client has no sampling tools capability`(): Unit = runBlocking {
        val (server, sessionId) = connectSamplingClient()
        val requests = mapOf(
            "tools" to minimalParams.copy(tools = listOf(weatherTool)),
            "toolChoice" to minimalParams.copy(toolChoice = ToolChoice()),
        )

        for ((field, params) in requests) {
            withClue(field) {
                shouldThrow<IllegalArgumentException> {
                    server.createMessage(sessionId = sessionId, params = CreateMessageRequest(params))
                }.message shouldBe "Client did not advertise sampling.tools capability; cannot send " +
                    "tools/toolChoice in sampling/createMessage request."
            }
        }
    }

    @Test
    fun `includeContext should be sent even without sampling context capability`(): Unit = runBlocking {
        var received: CreateMessageRequest? = null
        val (server, sessionId) = connectSamplingClient { request ->
            received = request
            CreateMessageResult(role = Role.Assistant, content = TextContent("ok"), model = "m")
        }

        server.createMessage(
            sessionId = sessionId,
            params = CreateMessageRequest(minimalParams.copy(includeContext = IncludeContext.ThisServer)),
        )

        received?.params?.includeContext shouldBe IncludeContext.ThisServer
    }

    @Test
    fun `server sends tools, client returns tool_use then final text`(): Unit = runBlocking {
        var turn = 0
        val (server, sessionId) = connectSamplingClient(
            clientCapabilities = ClientCapabilities(sampling = ClientCapabilities.Sampling(tools = EmptyJsonObject)),
        ) { _ ->
            turn++
            if (turn == 1) {
                CreateMessageResult(
                    role = Role.Assistant,
                    content = listOf(
                        TextContent("Let me check."),
                        ToolUseContent(
                            id = "call_1",
                            name = "get_weather",
                            input = buildJsonObject { put("location", JsonPrimitive("London")) },
                        ),
                    ),
                    model = "test",
                    stopReason = StopReason.ToolUse,
                )
            } else {
                CreateMessageResult(
                    role = Role.Assistant,
                    content = TextContent("The temperature in London is 20°C."),
                    model = "test",
                    stopReason = StopReason.EndTurn,
                )
            }
        }
        val messages = mutableListOf(SamplingMessage(Role.User, TextContent("What is the weather in London?")))

        // Turn 1: server sends tools, expects tool_use stop reason
        val first = server.createMessage(sessionId = sessionId, params = weatherRequest(messages))
        first.stopReason shouldBe StopReason.ToolUse
        first.content.size shouldBe 2

        // Append the assistant turn and inject the tool result
        messages.add(SamplingMessage(Role.Assistant, first.content))
        val toolUse = first.content.filterIsInstance<ToolUseContent>().single()
        messages.add(
            SamplingMessage(
                Role.User,
                ToolResultContent(toolUseId = toolUse.id, content = listOf(TextContent("""{"tempC":20}"""))),
            ),
        )

        // Turn 2: server sends updated history, expects final text
        val second = server.createMessage(sessionId = sessionId, params = weatherRequest(messages))
        second.stopReason shouldBe StopReason.EndTurn
        (second.content.single() as TextContent).text shouldBe "The temperature in London is 20°C."

        server.close()
    }

    private fun weatherRequest(messages: List<SamplingMessage>) = CreateMessageRequest(
        CreateMessageRequestParams(
            maxTokens = 256,
            messages = messages.toList(),
            tools = listOf(weatherTool),
            toolChoice = ToolChoice(mode = ToolChoice.Mode.Auto),
        ),
    )

    /** Connects a sampling client to a new [Server] and returns the server with the client's session id. */
    private suspend fun connectSamplingClient(
        clientCapabilities: ClientCapabilities = ClientCapabilities(sampling = ClientCapabilities.Sampling()),
        samplingHandler: (CreateMessageRequest) -> CreateMessageResult = {
            CreateMessageResult(role = Role.Assistant, content = TextContent("ok"), model = "m")
        },
    ): Pair<Server, String> {
        val server = Server(Implementation(name = "srv", version = "1.0"), ServerOptions(ServerCapabilities()))
        val client = Client(Implementation(name = "cli", version = "1.0"), ClientOptions(clientCapabilities))
        client.setRequestHandler<CreateMessageRequest>(Method.Defined.SamplingCreateMessage) { request, _ ->
            samplingHandler(request)
        }
        return server to connect(server, client).sessionId
    }
}
