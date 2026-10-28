package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.Annotations
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.SamplingMessageBuilder
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.assistantImage
import io.modelcontextprotocol.kotlin.sdk.types.buildCreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.userAudio
import io.modelcontextprotocol.kotlin.sdk.types.userImage
import io.modelcontextprotocol.kotlin.sdk.types.userText
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class ContentDslTest {
    @Test
    fun `content builders should forward all fields`() {
        val expectedAnnotations = Annotations(listOf(Role.User), 0.5, "2025-01-10T00:00:00Z")
        val expectedMeta = buildJsonObject { put("source", "test") }

        val request = buildCreateMessageRequest {
            maxTokens = 100
            messages {
                userText {
                    text = "Hello"
                    annotations(audience = listOf(Role.User), priority = 0.5, lastModified = "2025-01-10T00:00:00Z")
                    meta { put("source", "test") }
                }
                assistantImage {
                    data = "aW1n"
                    mimeType = "image/png"
                    annotations(expectedAnnotations)
                    meta { put("source", "test") }
                }
                userAudio {
                    data = "YXVk"
                    mimeType = "audio/wav"
                    annotations(expectedAnnotations)
                    meta { put("source", "test") }
                }
            }
        }

        val messages = request.params.messages
        messages.map { it.role } shouldBe listOf(Role.User, Role.Assistant, Role.User)
        messages.map { it.content.single() } shouldBe listOf(
            TextContent("Hello", expectedAnnotations, expectedMeta),
            ImageContent("aW1n", "image/png", expectedAnnotations, expectedMeta),
            AudioContent("YXVk", "audio/wav", expectedAnnotations, expectedMeta),
        )
    }

    @Test
    fun `content builders should reject missing required fields`() {
        val cases = listOf<Pair<String, SamplingMessageBuilder.() -> Unit>>(
            "text" to { userText { } },
            "data" to { userImage { mimeType = "image/png" } },
            "mimeType" to { userImage { data = "aW1n" } },
            "data" to { userAudio { mimeType = "audio/wav" } },
            "mimeType" to { userAudio { data = "YXVk" } },
        )

        cases.forEachIndexed { index, (field, content) ->
            withClue("case $index: missing '$field'") {
                shouldThrow<IllegalArgumentException> {
                    buildCreateMessageRequest {
                        maxTokens = 100
                        messages(content)
                    }
                }.message shouldContain "'$field'"
            }
        }
    }
}
