package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

class ContentTest {

    @Test
    fun `should serialize and deserialize TextContent with annotations`() {
        val content = TextContent(
            text = "Need help?",
            annotations = Annotations(audience = listOf(Role.User)),
            meta = buildJsonObject { put("origin", "assistant") },
        )

        verifySerialization(
            content,
            McpJson,
            """
            {
              "type": "text",
              "text": "Need help?",
              "annotations": {
                "audience": ["user"]
              },
              "_meta": {
                "origin": "assistant"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize ImageContent from JSON`() {
        val json = """
            {
              "type": "image",
              "data": "Zm9vYmFy",
              "mimeType": "image/jpeg",
              "annotations": {"priority": 0.6}
            }
        """.trimIndent()

        verifyDeserialization<MediaContent>(McpJson, json).shouldBeInstanceOf<ImageContent>()
    }

    @Test
    fun `should deserialize AudioContent from JSON`() {
        val json = """
            {
              "type": "audio",
              "data": "YmF6",
              "mimeType": "audio/mpeg",
              "_meta": {"speaker": "user"}
            }
        """.trimIndent()

        verifyDeserialization<MediaContent>(McpJson, json).shouldBeInstanceOf<AudioContent>()
    }

    @Test
    fun `should serialize ResourceLink with all fields`() {
        val resource = ResourceLink(
            name = "README",
            uri = "file:///workspace/README.md",
            title = "Workspace README",
            size = 2048,
            mimeType = "text/markdown",
            description = "Primary documentation",
            icons = listOf(Icon(src = "https://example.com/icon.png")),
            annotations = Annotations(priority = 0.75),
            meta = buildJsonObject { put("etag", "1234") },
        )

        verifySerialization(
            resource,
            McpJson,
            """
            {
              "type": "resource_link",
              "name": "README",
              "uri": "file:///workspace/README.md",
              "title": "Workspace README",
              "size": 2048,
              "mimeType": "text/markdown",
              "description": "Primary documentation",
              "icons": [
                {
                  "src": "https://example.com/icon.png"
                }
              ],
              "annotations": {
                "priority": 0.75
              },
              "_meta": {
                "etag": "1234"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize EmbeddedResource with text contents`() {
        val embedded = EmbeddedResource(
            resource = TextResourceContents(
                text = "fun main() = println(\"Hello\")",
                uri = "file:///workspace/Main.kt",
                mimeType = "text/x-kotlin",
                meta = buildJsonObject { put("languageId", "kotlin") },
            ),
            annotations = Annotations(audience = listOf(Role.Assistant)),
            meta = buildJsonObject { put("source", "analysis") },
        )

        verifySerialization(
            embedded,
            McpJson,
            """
            {
              "type": "resource",
              "resource": {
                "text": "fun main() = println(\"Hello\")",
                "uri": "file:///workspace/Main.kt",
                "mimeType": "text/x-kotlin",
                "_meta": {
                  "languageId": "kotlin"
                }
              },
              "annotations": {
                "audience": ["assistant"]
              },
              "_meta": {
                "source": "analysis"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize EmbeddedResource with blob contents`() {
        val json = """
            {
              "type": "resource",
              "resource": {
                "blob": "YmFzZTY0",
                "uri": "file:///workspace/archive.bin",
                "mimeType": "application/octet-stream"
              },
              "_meta": {"encoding": "base64"}
            }
        """.trimIndent()

        val embedded = verifyDeserialization<ContentBlock>(McpJson, json).shouldBeInstanceOf<EmbeddedResource>()
        embedded.resource.shouldBeInstanceOf<BlobResourceContents>()
    }

    @Test
    fun `should decode heterogeneous content blocks`() {
        val json = """
            [
              {"type": "text", "text": "Hello"},
              {"type": "image", "data": "AA==", "mimeType": "image/png"},
              {"type": "audio", "data": "AA==", "mimeType": "audio/wav"},
              {"type": "resource_link", "name": "log", "uri": "file:///tmp/log.txt"},
              {"type": "resource", "resource": {"text": "line1", "uri": "file:///tmp/log.txt"}}
            ]
        """.trimIndent()

        val content = verifyDeserialization<List<ContentBlock>>(McpJson, json)

        content.map { it::class } shouldBe listOf(
            TextContent::class,
            ImageContent::class,
            AudioContent::class,
            ResourceLink::class,
            EmbeddedResource::class,
        )
    }

    @Test
    fun `should round-trip every sampling content type through SamplingMessageContent`() {
        val cases = listOf<Pair<SamplingMessageContent, String>>(
            TextContent("hi") to """{"type": "text", "text": "hi"}""",
            ImageContent(data = "AA==", mimeType = "image/png") to
                """{"type": "image", "data": "AA==", "mimeType": "image/png"}""",
            AudioContent(data = "AA==", mimeType = "audio/wav") to
                """{"type": "audio", "data": "AA==", "mimeType": "audio/wav"}""",
            ToolUseContent(
                id = "call_1",
                name = "get_weather",
                input = buildJsonObject { put("location", "London") },
                meta = buildJsonObject { put("cacheControl", "ephemeral") },
            ) to """
                {
                  "type": "tool_use",
                  "id": "call_1",
                  "name": "get_weather",
                  "input": {"location": "London"},
                  "_meta": {"cacheControl": "ephemeral"}
                }
            """.trimIndent(),
            ToolResultContent(
                toolUseId = "call_1",
                content = listOf(TextContent("see file"), ResourceLink(name = "log", uri = "file:///tmp/a.log")),
                structuredContent = buildJsonObject { put("temp", 20) },
                isError = true,
            ) to """
                {
                  "type": "tool_result",
                  "toolUseId": "call_1",
                  "content": [
                    {"type": "text", "text": "see file"},
                    {"type": "resource_link", "name": "log", "uri": "file:///tmp/a.log"}
                  ],
                  "structuredContent": {"temp": 20},
                  "isError": true
                }
            """.trimIndent(),
        )

        cases.forEach { (content, json) ->
            withClue(json) {
                verifySerialization(content, McpJson, json)
            }
        }
    }

    @Test
    fun `should reject unknown sampling content type`() {
        shouldThrow<SerializationException> {
            McpJson.decodeFromString<SamplingMessageContent>("""{"type": "bogus", "x": 1}""")
        }
    }
}
