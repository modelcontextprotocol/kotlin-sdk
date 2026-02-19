package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.Annotations
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.IncludeContext
import io.modelcontextprotocol.kotlin.sdk.types.ModelPreferences
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.SamplingMessage
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.assistant
import io.modelcontextprotocol.kotlin.sdk.types.assistantAudio
import io.modelcontextprotocol.kotlin.sdk.types.assistantImage
import io.modelcontextprotocol.kotlin.sdk.types.buildCreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import io.modelcontextprotocol.kotlin.sdk.types.user
import io.modelcontextprotocol.kotlin.sdk.types.userAudio
import io.modelcontextprotocol.kotlin.sdk.types.userImage
import io.modelcontextprotocol.kotlin.sdk.types.userText
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class SamplingDslTest {

    @Test
    @Suppress("LongMethod")
    fun `buildCreateMessageRequest should build with all fields`() {
        val request = buildCreateMessageRequest {
            maxTokens = 1000
            systemPrompt = "System prompt"
            temperature = 0.5
            stopSequences = listOf("STOP")
            messages {
                user { "Hello" }
                assistant { "Hi" }
                userText {
                    text = "Text with annotations"
                    annotations(
                        audience = listOf(Role.User),
                        priority = 1.0,
                        lastModified = "2025-01-10T00:00:00Z",
                    )
                    meta {
                        put("key", "value")
                    }
                }
                assistantImage {
                    data = "base64image"
                    mimeType = "image/png"
                    annotations(Annotations(priority = 0.5))
                }
                assistantAudio {
                    data = "base64audio"
                    mimeType = "audio/wav"
                }
            }
            preferences(hints = listOf("hint"), intelligence = 0.9)
            metadata {
                put("metaKey", "metaValue")
            }
        }

        request.params.maxTokens shouldBe 1000
        request.params.systemPrompt shouldBe "System prompt"
        request.params.temperature shouldBe 0.5
        request.params.stopSequences shouldBe listOf("STOP")
        request.params.messages shouldHaveSize 5

        (request.params.messages[2].content as TextContent).shouldNotBeNull {
            text shouldBe "Text with annotations"
            annotations shouldNotBeNull {
                audience shouldBe listOf(Role.User)
                priority shouldBe 1.0
                lastModified shouldBe "2025-01-10T00:00:00Z"
            }
            meta?.get("key")?.jsonPrimitive?.content shouldBe "value"
        }

        (request.params.messages[3].content as ImageContent).shouldNotBeNull {
            data shouldBe "base64image"
            mimeType shouldBe "image/png"
            annotations?.priority shouldBe 0.5
        }

        (request.params.messages[4].content as AudioContent).shouldNotBeNull {
            data shouldBe "base64audio"
            mimeType shouldBe "audio/wav"
        }

        request.params.modelPreferences shouldNotBeNull {
            intelligencePriority shouldBe 0.9
        }
        request.params.metadata shouldNotBeNull {
            get("metaKey")?.jsonPrimitive?.content shouldBe "metaValue"
        }
    }

    @Test
    fun `buildCreateMessageRequest should support direct assignments`() {
        val messages = listOf(SamplingMessage(Role.User, TextContent("Hello")))
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
    fun `SamplingMessageBuilder should support direct content assignment`() {
        val content = TextContent("Direct")
        val request = buildCreateMessageRequest {
            maxTokens = 100
            messages {
                user(content)
                assistant(content)
            }
        }
        request.params.messages[0].content shouldBe content
        request.params.messages[1].content shouldBe content
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

    @Test
    fun `TextContentBuilder should throw if text is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCreateMessageRequest {
                maxTokens = 100
                messages {
                    userText { }
                }
            }
        }
    }

    @Test
    fun `ImageContentBuilder should throw if data or mimeType is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCreateMessageRequest {
                maxTokens = 100
                messages {
                    userImage { data = "abc" }
                }
            }
        }
        shouldThrow<IllegalArgumentException> {
            buildCreateMessageRequest {
                maxTokens = 100
                messages {
                    userImage { mimeType = "image/png" }
                }
            }
        }
    }

    @Test
    fun `AudioContentBuilder should throw if data or mimeType is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCreateMessageRequest {
                maxTokens = 100
                messages {
                    userAudio { data = "abc" }
                }
            }
        }
        shouldThrow<IllegalArgumentException> {
            buildCreateMessageRequest {
                maxTokens = 100
                messages {
                    userAudio { mimeType = "audio/wav" }
                }
            }
        }
    }

    @Test
    @Suppress("LongMethod")
    fun `CreateMessageRequest should build with all fields`() {
        val request = CreateMessageRequest {
            maxTokens = 1000
            systemPrompt = "System prompt"
            temperature = 0.5
            stopSequences = listOf("STOP")
            messages {
                user { "Hello" }
                assistant { "Hi" }
                userText {
                    text = "Text with annotations"
                    annotations(
                        audience = listOf(Role.User),
                        priority = 1.0,
                        lastModified = "2025-01-10T00:00:00Z",
                    )
                    meta {
                        put("key", "value")
                    }
                }
                assistantImage {
                    data = "base64image"
                    mimeType = "image/png"
                    annotations(Annotations(priority = 0.5))
                }
                assistantAudio {
                    data = "base64audio"
                    mimeType = "audio/wav"
                }
            }
            preferences(hints = listOf("hint"), intelligence = 0.9)
            metadata {
                put("metaKey", "metaValue")
            }
        }

        request.params.maxTokens shouldBe 1000
        request.params.systemPrompt shouldBe "System prompt"
        request.params.temperature shouldBe 0.5
        request.params.stopSequences shouldBe listOf("STOP")
        request.params.messages shouldHaveSize 5

        (request.params.messages[2].content as TextContent).shouldNotBeNull {
            text shouldBe "Text with annotations"
            annotations shouldNotBeNull {
                audience shouldBe listOf(Role.User)
                priority shouldBe 1.0
                lastModified shouldBe "2025-01-10T00:00:00Z"
            }
            meta?.get("key")?.jsonPrimitive?.content shouldBe "value"
        }

        (request.params.messages[3].content as ImageContent).shouldNotBeNull {
            data shouldBe "base64image"
            mimeType shouldBe "image/png"
            annotations?.priority shouldBe 0.5
        }

        (request.params.messages[4].content as AudioContent).shouldNotBeNull {
            data shouldBe "base64audio"
            mimeType shouldBe "audio/wav"
        }

        request.params.modelPreferences shouldNotBeNull {
            intelligencePriority shouldBe 0.9
        }
        request.params.metadata shouldNotBeNull {
            get("metaKey")?.jsonPrimitive?.content shouldBe "metaValue"
        }
    }

    @Test
    fun `CreateMessageRequest should support direct assignments`() {
        val messages = listOf(SamplingMessage(Role.User, TextContent("Hello")))
        val preferences = ModelPreferences(costPriority = 0.1)
        val request = CreateMessageRequest {
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
    fun `CreateMessageRequest SamplingMessageBuilder should support direct content assignment`() {
        val content = TextContent("Direct")
        val request = CreateMessageRequest {
            maxTokens = 100
            messages {
                user(content)
                assistant(content)
            }
        }
        request.params.messages[0].content shouldBe content
        request.params.messages[1].content shouldBe content
    }

    @Test
    fun `CreateMessageRequest should throw if maxTokens is missing`() {
        shouldThrow<IllegalArgumentException> {
            CreateMessageRequest {
                messages { user { "Hi" } }
            }
        }
    }

    @Test
    fun `CreateMessageRequest should throw if messages are missing`() {
        shouldThrow<IllegalArgumentException> {
            CreateMessageRequest {
                maxTokens = 100
            }
        }
    }

    @Test
    fun `CreateMessageRequest TextContentBuilder should throw if text is missing`() {
        shouldThrow<IllegalArgumentException> {
            CreateMessageRequest {
                maxTokens = 100
                messages {
                    userText { }
                }
            }
        }
    }

    @Test
    fun `CreateMessageRequest ImageContentBuilder should throw if data or mimeType is missing`() {
        shouldThrow<IllegalArgumentException> {
            CreateMessageRequest {
                maxTokens = 100
                messages {
                    userImage { data = "abc" }
                }
            }
        }
        shouldThrow<IllegalArgumentException> {
            CreateMessageRequest {
                maxTokens = 100
                messages {
                    userImage { mimeType = "image/png" }
                }
            }
        }
    }

    @Test
    fun `CreateMessageRequest AudioContentBuilder should throw if data or mimeType is missing`() {
        shouldThrow<IllegalArgumentException> {
            CreateMessageRequest {
                maxTokens = 100
                messages {
                    userAudio { data = "abc" }
                }
            }
        }
        shouldThrow<IllegalArgumentException> {
            CreateMessageRequest {
                maxTokens = 100
                messages {
                    userAudio { mimeType = "audio/wav" }
                }
            }
        }
    }
}
