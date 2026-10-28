package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SamplingTest {

    @Serializable
    private data class Holder(
        @Serializable(with = SamplingContentSerializer::class)
        val content: List<SamplingMessageContent>,
    )

    @Test
    fun `should reject ModelPreferences with invalid priority`() {
        shouldThrow<IllegalArgumentException> { ModelPreferences(costPriority = 1.5) }
        shouldThrow<IllegalArgumentException> { ModelPreferences(speedPriority = -0.1) }
        shouldThrow<IllegalArgumentException> { ModelPreferences(intelligencePriority = 1.1) }
    }

    @Test
    fun `should serialize SamplingMessage`() {
        val message = SamplingMessage(
            role = Role.User,
            content = listOf(TextContent(text = "Summarize the latest release.")),
            meta = buildJsonObject { put("k", "v") },
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
              },
              "_meta": {
                "k": "v"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
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
                    costPriority = 0.25,
                    speedPriority = 0.6,
                    intelligencePriority = 1.0,
                ),
                systemPrompt = "Respond with concise bullet points.",
                includeContext = IncludeContext.AllServers,
                temperature = 0.8,
                stopSequences = listOf("END"),
                metadata = buildJsonObject { put("provider", "anthropic") },
                tools = listOf(Tool(name = "get_weather", inputSchema = ToolSchema())),
                toolChoice = ToolChoice(mode = ToolChoice.Mode.Required),
                task = TaskMetadata(ttl = 60_000L),
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
                  "costPriority": 0.25,
                  "speedPriority": 0.6,
                  "intelligencePriority": 1.0
                },
                "systemPrompt": "Respond with concise bullet points.",
                "includeContext": "allServers",
                "temperature": 0.8,
                "stopSequences": ["END"],
                "metadata": {
                  "provider": "anthropic"
                },
                "tools": [
                  {"name": "get_weather", "inputSchema": {"type": "object"}}
                ],
                "toolChoice": {"mode": "required"},
                "task": {"ttl": 60000},
                "_meta": {
                  "progressToken": "sample-1"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize all IncludeContext values`() {
        verifySerialization(IncludeContext.None, McpJson, "\"none\"")
        verifySerialization(IncludeContext.ThisServer, McpJson, "\"thisServer\"")
        verifySerialization(IncludeContext.AllServers, McpJson, "\"allServers\"")
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

        verifySerialization<ClientResult>(
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
    fun `should serialize all StopReason values`() {
        verifySerialization(StopReason.EndTurn, McpJson, "\"endTurn\"")
        verifySerialization(StopReason.StopSequence, McpJson, "\"stopSequence\"")
        verifySerialization(StopReason.MaxTokens, McpJson, "\"maxTokens\"")
        verifySerialization(StopReason.ToolUse, McpJson, "\"toolUse\"")
    }

    @Test
    fun `SamplingMessage rejects empty content`() {
        assertFailsWith<IllegalArgumentException> {
            SamplingMessage(role = Role.User, content = emptyList())
        }
    }

    @Test
    fun `CreateMessageResult rejects empty content`() {
        assertFailsWith<IllegalArgumentException> {
            CreateMessageResult(
                role = Role.Assistant,
                content = emptyList(),
                model = "test-model",
            )
        }
    }

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

    @Test
    fun `should serialize all ToolChoice modes`() {
        verifySerialization(ToolChoice(ToolChoice.Mode.Auto), McpJson, """{"mode": "auto"}""")
        verifySerialization(ToolChoice(ToolChoice.Mode.Required), McpJson, """{"mode": "required"}""")
        verifySerialization(ToolChoice(ToolChoice.Mode.None), McpJson, """{"mode": "none"}""")
    }
}
