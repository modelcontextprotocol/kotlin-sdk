package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.shared.InMemoryTransport
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SamplingTest {

    private val dummyTool = Tool(
        name = "t",
        inputSchema = ToolSchema(properties = buildJsonObject { }, required = emptyList()),
    )

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

    private val minimalMessages = listOf(SamplingMessage(Role.User, TextContent("hi")))

    /**
     * Builds a connected [Server]+[Client] pair using [InMemoryTransport].
     *
     * @param clientCapabilities the capabilities the client advertises during initialize
     * @param samplingHandler the handler the client uses to respond to sampling requests
     * @return Pair of (server, sessionId) ready for [Server.createMessage] calls.
     */
    private fun buildPair(
        clientCapabilities: ClientCapabilities = ClientCapabilities(
            sampling = ClientCapabilities.Sampling(),
        ),
        samplingHandler: (CreateMessageRequest) -> CreateMessageResult = { _ ->
            CreateMessageResult(role = Role.Assistant, content = TextContent("ok"), model = "m")
        },
    ): Pair<Server, String> {
        val server = Server(
            serverInfo = Implementation(name = "srv", version = "1.0"),
            options = ServerOptions(capabilities = ServerCapabilities()),
        )

        val client = Client(
            clientInfo = Implementation(name = "cli", version = "1.0"),
            options = ClientOptions(capabilities = clientCapabilities),
        )

        client.setRequestHandler<CreateMessageRequest>(Method.Defined.SamplingCreateMessage) { req, _ ->
            samplingHandler(req)
        }

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        var sessionId: String? = null
        runBlocking {
            val sessionDeferred = CompletableDeferred<String>()
            launch { client.connect(clientTransport) }
            launch {
                val session = server.createSession(serverTransport)
                sessionDeferred.complete(session.sessionId)
            }
            sessionId = sessionDeferred.await()
        }

        return Pair(server, checkNotNull(sessionId))
    }

    // ============================================================================
    // Server.createMessage — capability enforcement (SEP-1577)
    // ============================================================================

    @Test
    fun `tools field rejected when client has no sampling tools capability`() {
        val (server, sessionId) = buildPair()
        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                server.createMessage(
                    sessionId = sessionId,
                    params = CreateMessageRequest(
                        params = CreateMessageRequestParams(
                            maxTokens = 100,
                            messages = minimalMessages,
                            tools = listOf(dummyTool),
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun `toolChoice field rejected when client has no sampling tools capability`() {
        val (server, sessionId) = buildPair()
        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                server.createMessage(
                    sessionId = sessionId,
                    params = CreateMessageRequest(
                        params = CreateMessageRequestParams(
                            maxTokens = 100,
                            messages = minimalMessages,
                            toolChoice = ToolChoice(),
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun `includeContext with no sampling context capability succeeds with a warning`() {
        val (server, sessionId) = buildPair()
        assertDoesNotThrow {
            runBlocking {
                server.createMessage(
                    sessionId = sessionId,
                    params = CreateMessageRequest(
                        params = CreateMessageRequestParams(
                            maxTokens = 100,
                            messages = minimalMessages,
                            includeContext = IncludeContext.ThisServer,
                        ),
                    ),
                )
            }
        }
    }

    // ============================================================================
    // End-to-end tool-loop integration
    // ============================================================================

    @Test
    fun `server sends tools, client returns tool_use then final text`() {
        var turn = 0
        val (server, sessionId) = buildPair(
            clientCapabilities = ClientCapabilities(
                sampling = ClientCapabilities.Sampling(tools = EmptyJsonObject),
            ),
            samplingHandler = { _ ->
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
            },
        )

        runBlocking {
            val messages = mutableListOf(
                SamplingMessage(Role.User, TextContent("What is the weather in London?")),
            )

            // — Turn 1: server sends tools, expects tool_use stop reason
            val first = server.createMessage(
                sessionId = sessionId,
                params = CreateMessageRequest(
                    params = CreateMessageRequestParams(
                        maxTokens = 256,
                        messages = messages.toList(),
                        tools = listOf(weatherTool),
                        toolChoice = ToolChoice(mode = ToolChoice.Mode.Auto),
                    ),
                ),
            )
            assertEquals(StopReason.ToolUse, first.stopReason)
            assertEquals(2, first.content.size)

            // — Append assistant turn and inject tool result
            messages.add(SamplingMessage(Role.Assistant, first.content))
            val toolUse = first.content.filterIsInstance<ToolUseContent>().single()
            messages.add(
                SamplingMessage(
                    Role.User,
                    ToolResultContent(
                        toolUseId = toolUse.id,
                        content = listOf(TextContent("""{"tempC":20}""")),
                    ),
                ),
            )

            // — Turn 2: server sends updated history, expects final text
            val second = server.createMessage(
                sessionId = sessionId,
                params = CreateMessageRequest(
                    params = CreateMessageRequestParams(
                        maxTokens = 256,
                        messages = messages.toList(),
                        tools = listOf(weatherTool),
                        toolChoice = ToolChoice(mode = ToolChoice.Mode.Auto),
                    ),
                ),
            )
            assertEquals(StopReason.EndTurn, second.stopReason)
            assertEquals(1, second.content.size)
            val text = second.content.single() as TextContent
            assertEquals("The temperature in London is 20°C.", text.text)

            server.close()
        }
    }
}
