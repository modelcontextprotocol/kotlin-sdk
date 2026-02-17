package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
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
            content = TextContent(text = "Summarize the latest release."),
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
    fun `should serialize CreateMessageRequest with all fields`() {
        val request = CreateMessageRequest(
            CreateMessageRequestParams(
                maxTokens = 512,
                messages = listOf(
                    SamplingMessage(
                        role = Role.User,
                        content = TextContent(text = "You are a helpful assistant."),
                    ),
                    SamplingMessage(
                        role = Role.User,
                        content = TextContent(text = "Provide a short summary."),
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
        val content = assertIs<TextContent>(message.content)
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
        val text = assertIs<TextContent>(result.content)
        assertEquals("Summary complete.", text.text)
        assertEquals("gpt-4o", result.model)
        assertEquals(StopReason.StopSequence, result.stopReason)
        val meta = result.meta
        assertNotNull(meta)
        assertEquals(1200.5, meta["latencyMs"]?.jsonPrimitive?.double)
    }
}
