package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.Annotations
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.assistantAudio
import io.modelcontextprotocol.kotlin.sdk.types.assistantImage
import io.modelcontextprotocol.kotlin.sdk.types.buildCreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.userText
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

/**
 * Tests for MediaContent DSL builders (TextContent, ImageContent, AudioContent).
 *
 * Verifies content can be constructed via DSL within sampling messages,
 * covering minimal (required fields), full (all fields), validation, and edge cases.
 */
@OptIn(ExperimentalMcpApi::class)
class ContentDslTest {

    // ========================================================================
    // TextContent Tests
    // ========================================================================

    @Test
    fun `userText should build minimal with text only`() {
        val request = buildCreateMessageRequest {
            maxTokens = 100
            messages {
                userText {
                    text = "Hello, world!"
                }
            }
        }

        (request.params.messages[0].content as TextContent).shouldNotBeNull {
            text shouldBe "Hello, world!"
            annotations.shouldBeNull()
            meta.shouldBeNull()
        }
    }

    @Test
    fun `userText should build full with all fields and nested meta`() {
        val request = buildCreateMessageRequest {
            maxTokens = 100
            messages {
                userText {
                    text = "Analyze the quarterly sales report for Q4 2025"
                    annotations(
                        audience = listOf(Role.User, Role.Assistant),
                        priority = 0.85,
                        lastModified = "2025-02-17T10:30:00Z",
                    )
                    meta {
                        put("source", "dashboard")
                        put("userId", "user-123")
                        put("sessionId", 42)
                        put("analyzed", true)
                    }
                }
            }
        }

        (request.params.messages[0].content as TextContent).shouldNotBeNull {
            text shouldBe "Analyze the quarterly sales report for Q4 2025"
            annotations shouldNotBeNull {
                audience shouldBe listOf(Role.User, Role.Assistant)
                priority shouldBe 0.85
                lastModified shouldBe "2025-02-17T10:30:00Z"
            }
            meta shouldNotBeNull {
                get("source")?.jsonPrimitive?.content shouldBe "dashboard"
                get("userId")?.jsonPrimitive?.content shouldBe "user-123"
                get("sessionId")?.jsonPrimitive?.content shouldBe "42"
                get("analyzed")?.jsonPrimitive?.content shouldBe "true"
            }
        }
    }

    @Test
    fun `userText should handle unicode and special characters`() {
        val request = buildCreateMessageRequest {
            maxTokens = 100
            messages {
                userText {
                    text = "Hello 🌍! Ça va? Москва 北京 مرحبا"
                }
            }
        }

        (request.params.messages[0].content as TextContent).text shouldBe "Hello 🌍! Ça va? Москва 北京 مرحبا"
    }

    @Test
    fun `userText should handle empty string`() {
        val request = buildCreateMessageRequest {
            maxTokens = 100
            messages {
                userText {
                    text = ""
                }
            }
        }

        (request.params.messages[0].content as TextContent).text shouldBe ""
    }

    @Test
    fun `userText should throw if text is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCreateMessageRequest {
                maxTokens = 100
                messages {
                    userText { }
                }
            }
        }
    }

    // ========================================================================
    // ImageContent Tests
    // ========================================================================

    @Test
    fun `assistantImage should build minimal with data and mimeType`() {
        val request = buildCreateMessageRequest {
            maxTokens = 100
            messages {
                assistantImage {
                    data =
                        "iVBORw0KGgoggg=="
                    mimeType = "image/png"
                }
            }
        }

        (request.params.messages[0].content as ImageContent).shouldNotBeNull {
            data shouldBe
                "iVBORw0KGgoggg=="
            mimeType shouldBe "image/png"
            annotations.shouldBeNull()
            meta.shouldBeNull()
        }
    }

    @Test
    fun `assistantImage should build full with all fields and image metadata`() {
        val request = buildCreateMessageRequest {
            maxTokens = 100
            messages {
                assistantImage {
                    data = "iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAAFklEQVR42mNk+M9QzzCCgAEYjRZnRwEA"
                    mimeType = "image/jpeg"
                    annotations(
                        audience = listOf(Role.Assistant),
                        priority = 0.92,
                        lastModified = "2025-02-17T11:45:30Z",
                    )
                    meta {
                        put("width", 1920)
                        put("height", 1080)
                        put("format", "jpeg")
                        put("compression", "lossy")
                        put("quality", 85)
                    }
                }
            }
        }

        (request.params.messages[0].content as ImageContent).shouldNotBeNull {
            mimeType shouldBe "image/jpeg"
            annotations shouldNotBeNull {
                audience shouldBe listOf(Role.Assistant)
                priority shouldBe 0.92
                lastModified shouldBe "2025-02-17T11:45:30Z"
            }
            meta shouldNotBeNull {
                get("width")?.jsonPrimitive?.content shouldBe "1920"
                get("height")?.jsonPrimitive?.content shouldBe "1080"
                get("format")?.jsonPrimitive?.content shouldBe "jpeg"
                get("compression")?.jsonPrimitive?.content shouldBe "lossy"
                get("quality")?.jsonPrimitive?.content shouldBe "85"
            }
        }
    }

    @Test
    fun `assistantImage should accept various MIME types`() {
        val mimeTypes = listOf("image/png", "image/jpeg", "image/webp", "image/gif", "image/svg+xml")

        mimeTypes.forEach { mime ->
            val request = buildCreateMessageRequest {
                maxTokens = 100
                messages {
                    assistantImage {
                        data = "base64data"
                        mimeType = mime
                    }
                }
            }
            (request.params.messages[0].content as ImageContent).mimeType shouldBe mime
        }
    }

    @Test
    fun `assistantImage should throw if data is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCreateMessageRequest {
                maxTokens = 100
                messages {
                    assistantImage {
                        mimeType = "image/png"
                    }
                }
            }
        }
    }

    @Test
    fun `assistantImage should throw if mimeType is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCreateMessageRequest {
                maxTokens = 100
                messages {
                    assistantImage {
                        data = "base64data"
                    }
                }
            }
        }
    }

    // ========================================================================
    // AudioContent Tests
    // ========================================================================

    @Test
    fun `assistantAudio should build minimal with data and mimeType`() {
        val request = buildCreateMessageRequest {
            maxTokens = 100
            messages {
                assistantAudio {
                    data = "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAAABmYWN0BAAAAAAAAABkYXRhAAAAAA=="
                    mimeType = "audio/wav"
                }
            }
        }

        (request.params.messages[0].content as AudioContent).shouldNotBeNull {
            data shouldBe "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAAABmYWN0BAAAAAAAAABkYXRhAAAAAA=="
            mimeType shouldBe "audio/wav"
            annotations.shouldBeNull()
            meta.shouldBeNull()
        }
    }

    @Test
    fun `assistantAudio should build full with all fields and audio metadata`() {
        val request = buildCreateMessageRequest {
            maxTokens = 100
            messages {
                assistantAudio {
                    data = "//uQxAAAAAAAAAAAAAAAAAAAAAAAWGluZwAAAA8AAAACAAADIADMzMzMzMzMzMzMzMzMzMzMzMzMzMzM"
                    mimeType = "audio/mpeg"
                    annotations(
                        audience = listOf(Role.User),
                        priority = 0.75,
                        lastModified = "2025-02-17T12:00:00Z",
                    )
                    meta {
                        put("duration", 180)
                        put("bitrate", 128)
                        put("codec", "mp3")
                        put("sampleRate", 44100)
                        put("channels", 2)
                    }
                }
            }
        }

        (request.params.messages[0].content as AudioContent).shouldNotBeNull {
            mimeType shouldBe "audio/mpeg"
            annotations shouldNotBeNull {
                audience shouldBe listOf(Role.User)
                priority shouldBe 0.75
                lastModified shouldBe "2025-02-17T12:00:00Z"
            }
            meta shouldNotBeNull {
                get("duration")?.jsonPrimitive?.content shouldBe "180"
                get("bitrate")?.jsonPrimitive?.content shouldBe "128"
                get("codec")?.jsonPrimitive?.content shouldBe "mp3"
                get("sampleRate")?.jsonPrimitive?.content shouldBe "44100"
                get("channels")?.jsonPrimitive?.content shouldBe "2"
            }
        }
    }

    @Test
    fun `assistantAudio should throw if data is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCreateMessageRequest {
                maxTokens = 100
                messages {
                    assistantAudio {
                        mimeType = "audio/wav"
                    }
                }
            }
        }
    }

    @Test
    fun `assistantAudio should throw if mimeType is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCreateMessageRequest {
                maxTokens = 100
                messages {
                    assistantAudio {
                        data = "base64audio"
                    }
                }
            }
        }
    }

    // ========================================================================
    // Annotations Edge Cases
    // ========================================================================

    @Test
    fun `annotations should support boundary priority values`() {
        // Priority = 0.0 (minimum)
        val requestMin = buildCreateMessageRequest {
            maxTokens = 100
            messages {
                userText {
                    text = "Min priority"
                    annotations(priority = 0.0)
                }
            }
        }
        (requestMin.params.messages[0].content as TextContent).annotations?.priority shouldBe 0.0

        // Priority = 1.0 (maximum)
        val requestMax = buildCreateMessageRequest {
            maxTokens = 100
            messages {
                userText {
                    text = "Max priority"
                    annotations(priority = 1.0)
                }
            }
        }
        (requestMax.params.messages[0].content as TextContent).annotations?.priority shouldBe 1.0
    }

    @Test
    fun `annotations should support direct object construction`() {
        val request = buildCreateMessageRequest {
            maxTokens = 100
            messages {
                userText {
                    text = "With annotations object"
                    annotations(Annotations(priority = 0.5, audience = listOf(Role.User)))
                }
            }
        }

        (request.params.messages[0].content as TextContent).annotations shouldNotBeNull {
            priority shouldBe 0.5
            audience shouldBe listOf(Role.User)
            lastModified.shouldBeNull()
        }
    }
}
