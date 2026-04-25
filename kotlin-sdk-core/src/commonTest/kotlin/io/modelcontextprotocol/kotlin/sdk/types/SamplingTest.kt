package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SamplingTest {

    @Serializable
    private data class Holder(
        @Serializable(with = SamplingContentSerializer::class)
        val content: List<SamplingMessageContent>,
    )

    private val dummyTool = Tool(
        name = "get_weather",
        description = "returns weather",
        inputSchema = ToolSchema(
            properties = buildJsonObject { },
            required = emptyList(),
        ),
    )

    @Test
    fun `should serialize ModelHint`() {
        val hint = ModelHint(name = "claude-3-5-sonnet")

        verifySerialization(
            hint,
            McpJson,
            """
            {
              "name": "claude-3-5-sonnet"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ModelPreferences with priorities`() {
        val preferences = ModelPreferences(
            hints = listOf(ModelHint(name = "haiku"), ModelHint(name = "openaichat")),
            costPriority = 0.25,
            speedPriority = 0.75,
            intelligencePriority = 1.0,
        )

        verifySerialization(
            preferences,
            McpJson,
            """
            {
              "hints": [
                {"name": "haiku"},
                {"name": "openaichat"}
              ],
              "costPriority": 0.25,
              "speedPriority": 0.75,
              "intelligencePriority": 1.0
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should reject ModelPreferences with invalid priority`() {
        assertFailsWith<IllegalArgumentException> {
            ModelPreferences(costPriority = 1.5)
        }
    }

    @Test
    fun `should serialize SamplingMessage`() {
        val message = SamplingMessage(
            role = Role.User,
            content = listOf(TextContent(text = "Summarize the latest release.")),
        )

        verifySerialization(
            message,
            McpJson,
            """
            {
              "role": "user",
              "content": {
                "type": "text",
                "text": "Summarize the latest release."
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    @Suppress("LongMethod")
    fun `should serialize CreateMessageRequest with all fields`() {
        val request = CreateMessageRequest(
            CreateMessageRequestParams(
                maxTokens = 512,
                messages = listOf(
                    SamplingMessage(
                        role = Role.User,
                        content = listOf(TextContent(text = "You are a helpful assistant.")),
                    ),
                    SamplingMessage(
                        role = Role.User,
                        content = listOf(TextContent(text = "Provide a short summary.")),
                    ),
                ),
                modelPreferences = ModelPreferences(
                    hints = listOf(ModelHint(name = "claude")),
                    speedPriority = 0.6,
                ),
                systemPrompt = "Respond with concise bullet points.",
                includeContext = IncludeContext.AllServers,
                temperature = 0.8,
                stopSequences = listOf("END"),
                metadata = buildJsonObject { put("provider", "anthropic") },
                meta = RequestMeta(buildJsonObject { put("progressToken", "sample-1") }),
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "sampling/createMessage",
              "params": {
                "maxTokens": 512,
                "messages": [
                  {
                    "role": "user",
                    "content": {
                      "type": "text",
                      "text": "You are a helpful assistant."
                    }
                  },
                  {
                    "role": "user",
                    "content": {
                      "type": "text",
                      "text": "Provide a short summary."
                    }
                  }
                ],
                "modelPreferences": {
                  "hints": [
                    {"name": "claude"}
                  ],
                  "speedPriority": 0.6
                },
                "systemPrompt": "Respond with concise bullet points.",
                "includeContext": "allServers",
                "temperature": 0.8,
                "stopSequences": ["END"],
                "metadata": {
                  "provider": "anthropic"
                },
                "_meta": {
                  "progressToken": "sample-1"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize CreateMessageRequest`() {
        val json = """
            {
              "method": "sampling/createMessage",
              "params": {
                "maxTokens": 256,
                "messages": [
                  {
                    "role": "user",
                    "content": {
                      "type": "text",
                      "text": "Draft a project update."
                    }
                  }
                ],
                "modelPreferences": {
                  "costPriority": 0.4
                },
                "includeContext": "thisServer",
                "temperature": 1.1,
                "stopSequences": ["\n\n"],
                "metadata": {
                  "provider": "openai"
                },
                "_meta": {
                  "progressToken": 42
                }
              }
            }
        """.trimIndent()

        val request = verifyDeserialization<CreateMessageRequest>(McpJson, json)

        assertEquals(Method.Defined.SamplingCreateMessage, request.method)
        val params = request.params
        assertEquals(256, params.maxTokens)
        assertEquals(IncludeContext.ThisServer, params.includeContext)
        assertEquals(ProgressToken(42), params.meta?.progressToken)
        assertEquals("openai", params.metadata?.get("provider")?.jsonPrimitive?.content)

        val message = params.messages.first()
        assertEquals(Role.User, message.role)
        val content = assertIs<TextContent>(message.content.single())
        assertEquals("Draft a project update.", content.text)

        val preferences = assertNotNull(params.modelPreferences)
        assertEquals(0.4, preferences.costPriority)
    }

    @Test
    fun `should serialize CreateMessageResult with stop reason`() {
        val result = CreateMessageResult(
            role = Role.Assistant,
            content = TextContent(text = "Here is the requested update."),
            model = "claude-3-5-sonnet",
            stopReason = StopReason.MaxTokens,
            meta = buildJsonObject { put("latencyMs", 850) },
        )

        verifySerialization(
            result,
            McpJson,
            """
            {
              "role": "assistant",
              "content": {
                "type": "text",
                "text": "Here is the requested update."
              },
              "model": "claude-3-5-sonnet",
              "stopReason": "maxTokens",
              "_meta": {
                "latencyMs": 850
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize CreateMessageResult`() {
        val json = """
            {
              "role": "assistant",
              "content": {
                "type": "text",
                "text": "Summary complete."
              },
              "model": "gpt-4o",
              "stopReason": "stopSequence",
              "_meta": {
                "latencyMs": 1200.5
              }
            }
        """.trimIndent()

        val result = verifyDeserialization<CreateMessageResult>(McpJson, json)

        assertEquals(Role.Assistant, result.role)
        val text = assertIs<TextContent>(result.content.single())
        assertEquals("Summary complete.", text.text)
        assertEquals("gpt-4o", result.model)
        assertEquals(StopReason.StopSequence, result.stopReason)
        val meta = result.meta
        assertNotNull(meta)
        assertEquals(1200.5, meta["latencyMs"]?.jsonPrimitive?.double)
    }

    // ============================================================================
    // SamplingMessage shape (SEP-1577)
    // ============================================================================

    @Test
    fun `SamplingMessage content is a list of SamplingMessageContent`() {
        val m = SamplingMessage(
            role = Role.User,
            content = listOf(TextContent("hi")),
        )
        m.content.size shouldBe 1
        (m.content[0] as TextContent).text shouldBe "hi"
    }

    @Test
    fun `SamplingMessage single-element content serialises as single object`() {
        val m = SamplingMessage(role = Role.User, content = listOf(TextContent("hi")))
        val json = McpJson.encodeToString(SamplingMessage.serializer(), m)
        check("""{"role":"user","content":""" in json) { "expected role/content prefix, got $json" }
        check("\"type\":\"text\"" in json && "\"text\":\"hi\"" in json) {
            "expected single-object content with text discriminator, got $json"
        }
        check("\"content\":[" !in json) { "expected single-object wire, but array form found: $json" }
    }

    @Test
    fun `SamplingMessage multi-element content serialises as array`() {
        val m = SamplingMessage(
            role = Role.Assistant,
            content = listOf(
                TextContent("Let me use a tool"),
                ToolUseContent(id = "c1", name = "get_weather", input = JsonObject(emptyMap())),
            ),
        )
        val json = McpJson.encodeToString(SamplingMessage.serializer(), m)
        check("\"content\":[" in json) { "expected array wire form, got $json" }
    }

    @Test
    fun `SamplingMessage _meta round-trips`() {
        val meta = buildJsonObject { put("k", JsonPrimitive("v")) }
        val m = SamplingMessage(role = Role.User, content = listOf(TextContent("hi")), meta = meta)
        val json = McpJson.encodeToString(SamplingMessage.serializer(), m)
        val decoded = McpJson.decodeFromString(SamplingMessage.serializer(), json)
        decoded.meta shouldBe meta
    }

    // ============================================================================
    // CreateMessageRequestParams: tools / toolChoice + StopReason.ToolUse
    // ============================================================================

    @Test
    fun `CreateMessageRequestParams tools and toolChoice default to null`() {
        val params = CreateMessageRequestParams(maxTokens = 100, messages = emptyList())
        params.tools shouldBe null
        params.toolChoice shouldBe null
    }

    @Test
    fun `CreateMessageRequestParams round-trips tools and toolChoice`() {
        val original = CreateMessageRequestParams(
            maxTokens = 100,
            messages = emptyList(),
            tools = listOf(dummyTool),
            toolChoice = ToolChoice(mode = ToolChoice.Mode.Required),
        )
        val encoded = McpJson.encodeToString(CreateMessageRequestParams.serializer(), original)
        val decoded = McpJson.decodeFromString(CreateMessageRequestParams.serializer(), encoded)
        decoded.tools?.single()?.name shouldBe "get_weather"
        decoded.toolChoice shouldBe ToolChoice(mode = ToolChoice.Mode.Required)
    }

    @Test
    fun `StopReason ToolUse serialises as toolUse`() {
        StopReason.ToolUse.value shouldBe "toolUse"
    }

    // ============================================================================
    // CreateMessageResult shape (SEP-1577)
    // ============================================================================

    @Test
    fun `CreateMessageResult single-block content serialises as single object`() {
        val r = CreateMessageResult(
            role = Role.Assistant,
            content = TextContent("42"),
            model = "test-model",
            stopReason = StopReason.EndTurn,
        )
        val json = McpJson.encodeToString(CreateMessageResult.serializer(), r)
        check("\"content\":{" in json) { "expected single-object content wire form, got $json" }
        check("\"content\":[" !in json) { "expected NOT to use array wire form for size-1, got $json" }
    }

    @Test
    fun `CreateMessageResult multi-block content with ToolUse stopReason round-trips`() {
        val r = CreateMessageResult(
            role = Role.Assistant,
            content = listOf(
                TextContent("Let me use a tool"),
                ToolUseContent(id = "c1", name = "get_weather", input = JsonObject(emptyMap())),
            ),
            model = "test-model",
            stopReason = StopReason.ToolUse,
        )
        val json = McpJson.encodeToString(CreateMessageResult.serializer(), r)
        val decoded = McpJson.decodeFromString(CreateMessageResult.serializer(), json)
        decoded shouldBe r
        decoded.stopReason shouldBe StopReason.ToolUse
        decoded.content.size shouldBe 2
    }

    @Test
    fun `CreateMessageResult pre-SEP single-object wire decodes correctly`() {
        val json = """{"role":"assistant","content":{"type":"text","text":"hi"},"model":"m"}"""
        val decoded = McpJson.decodeFromString(CreateMessageResult.serializer(), json)
        decoded.content.size shouldBe 1
        (decoded.content[0] as TextContent).text shouldBe "hi"
    }

    // ============================================================================
    // SamplingContentSerializer (single-or-array wire heuristic)
    // ============================================================================

    @Test
    fun `SamplingContentSerializer decodes single object into list of one`() {
        val json = """{"content":{"type":"text","text":"hi"}}"""
        val h = McpJson.decodeFromString(Holder.serializer(), json)
        h.content.size shouldBe 1
        h.content[0].shouldBeInstanceOf<TextContent>().text shouldBe "hi"
    }

    @Test
    fun `SamplingContentSerializer decodes array into list`() {
        val json = """{"content":[{"type":"text","text":"a"},{"type":"text","text":"b"}]}"""
        val h = McpJson.decodeFromString(Holder.serializer(), json)
        h.content.size shouldBe 2
    }

    @Test
    fun `SamplingContentSerializer encodes list of size one as a single object`() {
        val h = Holder(content = listOf(TextContent("hi")))
        val json = McpJson.encodeToString(Holder.serializer(), h)
        json shouldBe """{"content":{"text":"hi","type":"text"}}"""
    }

    @Test
    fun `SamplingContentSerializer encodes list of size two as an array`() {
        val h = Holder(content = listOf(TextContent("a"), TextContent("b")))
        val json = McpJson.encodeToString(Holder.serializer(), h)
        json shouldBe """{"content":[{"text":"a","type":"text"},{"text":"b","type":"text"}]}"""
    }

    @Test
    fun `SamplingContentSerializer encoding an empty list throws`() {
        val h = Holder(content = emptyList())
        assertFailsWith<IllegalStateException> {
            McpJson.encodeToString(Holder.serializer(), h)
        }
    }

    @Test
    fun `SamplingContentSerializer decoding an empty array throws`() {
        val json = """{"content":[]}"""
        assertFailsWith<SerializationException> {
            McpJson.decodeFromString(Holder.serializer(), json)
        }
    }

    // ============================================================================
    // ToolChoice
    // ============================================================================

    @Test
    fun `ToolChoice round-trips auto mode`() {
        val original = ToolChoice(mode = ToolChoice.Mode.Auto)
        val json = McpJson.encodeToString(ToolChoice.serializer(), original)
        json shouldBe """{"mode":"auto"}"""
        McpJson.decodeFromString(ToolChoice.serializer(), json) shouldBe original
    }

    @Test
    fun `ToolChoice round-trips required mode`() {
        val original = ToolChoice(mode = ToolChoice.Mode.Required)
        McpJson.decodeFromString(
            ToolChoice.serializer(),
            McpJson.encodeToString(ToolChoice.serializer(), original),
        ) shouldBe original
    }

    @Test
    fun `ToolChoice round-trips none mode`() {
        val original = ToolChoice(mode = ToolChoice.Mode.None)
        McpJson.decodeFromString(
            ToolChoice.serializer(),
            McpJson.encodeToString(ToolChoice.serializer(), original),
        ) shouldBe original
    }

    @Test
    fun `ToolChoice absent mode serialises as empty object and deserialises to null mode`() {
        val original = ToolChoice()
        val json = McpJson.encodeToString(ToolChoice.serializer(), original)
        json shouldBe """{}"""
        McpJson.decodeFromString(ToolChoice.serializer(), json) shouldBe ToolChoice(mode = null)
    }
}
