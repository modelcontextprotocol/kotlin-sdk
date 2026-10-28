package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.IncludeContext
import io.modelcontextprotocol.kotlin.sdk.types.ModelHint
import io.modelcontextprotocol.kotlin.sdk.types.ModelPreferences
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.SamplingMessage
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.assistant
import io.modelcontextprotocol.kotlin.sdk.types.assistantAudio
import io.modelcontextprotocol.kotlin.sdk.types.assistantText
import io.modelcontextprotocol.kotlin.sdk.types.buildCreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.user
import io.modelcontextprotocol.kotlin.sdk.types.userImage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class SamplingDslTest {
    @Test
    fun `buildCreateMessageRequest should build with all fields`() {
        val request = buildCreateMessageRequest {
            maxTokens = 1000
            systemPrompt = "System prompt"
            context = IncludeContext.ThisServer
            temperature = 0.5
            stopSequences = listOf("STOP")
            messages {
                user { "Hello" }
                assistant { "Hi" }
                assistantText { text = "How can I help?" }
                userImage {
                    data = "aW1n"
                    mimeType = "image/png"
                }
                assistantAudio {
                    data = "YXVk"
                    mimeType = "audio/wav"
                }
            }
            preferences(hints = listOf("claude"), cost = 0.1, speed = 0.2, intelligence = 0.9)
            metadata { put("metaKey", "metaValue") }
        }

        request.params shouldBe CreateMessageRequestParams(
            maxTokens = 1000,
            messages = listOf(
                SamplingMessage(Role.User, listOf(TextContent("Hello"))),
                SamplingMessage(Role.Assistant, listOf(TextContent("Hi"))),
                SamplingMessage(Role.Assistant, listOf(TextContent("How can I help?"))),
                SamplingMessage(Role.User, listOf(ImageContent("aW1n", "image/png"))),
                SamplingMessage(Role.Assistant, listOf(AudioContent("YXVk", "audio/wav"))),
            ),
            modelPreferences = ModelPreferences(
                hints = listOf(ModelHint("claude")),
                costPriority = 0.1,
                speedPriority = 0.2,
                intelligencePriority = 0.9,
            ),
            systemPrompt = "System prompt",
            includeContext = IncludeContext.ThisServer,
            temperature = 0.5,
            stopSequences = listOf("STOP"),
            metadata = buildJsonObject { put("metaKey", "metaValue") },
        )
    }

    @Test
    fun `buildCreateMessageRequest should support direct assignments`() {
        val messages = listOf(SamplingMessage(Role.User, listOf(TextContent("Hello"))))
        val preferences = ModelPreferences(costPriority = 0.1)
        val request = buildCreateMessageRequest {
            maxTokens = 100
            messages(messages)
            preferences(preferences)
            context = IncludeContext.AllServers
        }

        request.params.messages shouldBe messages
        request.params.modelPreferences shouldBe preferences
        request.params.includeContext shouldBe IncludeContext.AllServers
    }

    @Test
    fun `buildCreateMessageRequest should throw if maxTokens is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCreateMessageRequest {
                messages { user { "Hi" } }
            }
        }
    }

    @Test
    fun `buildCreateMessageRequest should throw if messages are missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCreateMessageRequest {
                maxTokens = 100
            }
        }
    }
}
