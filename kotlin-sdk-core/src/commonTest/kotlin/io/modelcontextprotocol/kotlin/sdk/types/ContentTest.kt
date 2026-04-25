package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class ContentTest {

    private val samplingContentSerializer = ListSerializer(SamplingMessageContent.serializer())

    @Test
    fun `should serialize TextContent with minimal fields`() {
        val content = TextContent(text = "Hello, MCP!")

        verifySerialization(
            content,
            McpJson,
            """
            {
              "type": "text",
              "text": "Hello, MCP!"
            }
            """.trimIndent(),
        )
    }

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
    fun `should serialize ImageContent`() {
        val content = ImageContent(
            data = "aW1hZ2VEYXRh",
            mimeType = "image/png",
        )

        verifySerialization(
            content,
            McpJson,
            """
            {
              "type": "image",
              "data": "aW1hZ2VEYXRh",
              "mimeType": "image/png"
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

        val content = verifyDeserialization<MediaContent>(McpJson, json) as ImageContent
        assertEquals("Zm9vYmFy", content.data)
        assertEquals("image/jpeg", content.mimeType)
        assertEquals(0.6, content.annotations?.priority)
        assertNull(content.meta)
    }

    @Test
    fun `should serialize AudioContent`() {
        val content = AudioContent(
            data = "YXVkaW9kYXRh",
            mimeType = "audio/wav",
            meta = buildJsonObject { put("durationMs", 1200) },
        )

        verifySerialization(
            content,
            McpJson,
            """
            {
              "type": "audio",
              "data": "YXVkaW9kYXRh",
              "mimeType": "audio/wav",
              "_meta": {
                "durationMs": 1200
              }
            }
            """.trimIndent(),
        )
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

        val content = verifyDeserialization<MediaContent>(McpJson, json) as AudioContent
        assertEquals("audio/mpeg", content.mimeType)
        assertEquals("YmF6", content.data)
        assertEquals("user", content.meta?.get("speaker")?.jsonPrimitive?.content)
    }

    @Test
    fun `should serialize ResourceLink with minimal fields`() {
        val resource = ResourceLink(
            name = "README",
            uri = "file:///workspace/README.md",
        )

        verifySerialization(
            resource,
            McpJson,
            """
            {
              "type": "resource_link",
              "name": "README",
              "uri": "file:///workspace/README.md"
            }
            """.trimIndent(),
        )
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
    fun `should deserialize ResourceLink from JSON`() {
        val json = """
            {
              "type": "resource_link",
              "name": "config",
              "uri": "file:///config.yaml",
              "size": 512,
              "annotations": { "lastModified": "2025-01-12T15:00:58Z" }
            }
        """.trimIndent()

        val resource = verifyDeserialization<ContentBlock>(McpJson, json) as ResourceLink
        assertEquals("config", resource.name)
        assertEquals("file:///config.yaml", resource.uri)
        assertEquals(512, resource.size)
        assertEquals("2025-01-12T15:00:58Z", resource.annotations?.lastModified)
        assertNull(resource.meta)
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

        val embedded = verifyDeserialization<ContentBlock>(McpJson, json) as EmbeddedResource
        assertEquals(ContentTypes.EMBEDDED_RESOURCE, embedded.type)
        assertNull(embedded.annotations)
        assertEquals("base64", embedded.meta?.get("encoding")?.jsonPrimitive?.content)

        val resource = embedded.resource
        assertIs<BlobResourceContents>(resource)
        assertEquals("YmFzZTY0", resource.blob)
        assertEquals("file:///workspace/archive.bin", resource.uri)
        assertEquals("application/octet-stream", resource.mimeType)
    }

    @Test
    fun `should decode heterogeneous content blocks`() {
        val json = """
            [
              {
                "type": "text",
                "text": "Hello"
              },
              {
                "type": "resource_link",
                "name": "log",
                "uri": "file:///tmp/log.txt"
              },
              {
                "type": "resource",
                "resource": {
                  "text": "line1",
                  "uri": "file:///tmp/log.txt"
                }
              }
            ]
        """.trimIndent()

        val content = verifyDeserialization<List<ContentBlock>>(McpJson, json)
        assertEquals(3, content.size)
        assertIs<TextContent>(content[0])
        assertIs<ResourceLink>(content[1])
        assertIs<EmbeddedResource>(content[2])
    }

    // ============================================================================
    // SamplingMessageContent hierarchy (SEP-1577)
    // ============================================================================

    @Test
    fun `TextContent implements SamplingMessageContent`() {
        val c: SamplingMessageContent = TextContent("hi")
        c.shouldBeInstanceOf<TextContent>()
    }

    @Test
    fun `ImageContent implements SamplingMessageContent`() {
        val c: SamplingMessageContent = ImageContent(data = "AA==", mimeType = "image/png")
        c.shouldBeInstanceOf<ImageContent>()
    }

    @Test
    fun `AudioContent implements SamplingMessageContent`() {
        val c: SamplingMessageContent = AudioContent(data = "AA==", mimeType = "audio/wav")
        c.shouldBeInstanceOf<AudioContent>()
    }

    // ============================================================================
    // ToolUseContent
    // ============================================================================

    @Test
    fun `ToolUseContent round-trips through McpJson`() {
        val input = buildJsonObject { put("location", JsonPrimitive("London")) }
        val original = ToolUseContent(id = "call_1", name = "get_weather", input = input)
        val encoded = McpJson.encodeToString(ToolUseContent.serializer(), original)
        val decoded = McpJson.decodeFromString(ToolUseContent.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `ToolUseContent serialises with type discriminator tool_use`() {
        val c = ToolUseContent(id = "x", name = "n", input = JsonObject(emptyMap()))
        val json = McpJson.encodeToString(ToolUseContent.serializer(), c)
        check("\"type\":\"tool_use\"" in json) { "missing discriminator in: $json" }
    }

    @Test
    fun `ToolUseContent preserves meta when present`() {
        val meta = buildJsonObject { put("cacheControl", JsonPrimitive("ephemeral")) }
        val c = ToolUseContent(id = "x", name = "n", input = JsonObject(emptyMap()), meta = meta)
        val json = McpJson.encodeToString(ToolUseContent.serializer(), c)
        val decoded = McpJson.decodeFromString(ToolUseContent.serializer(), json)
        decoded.meta shouldBe meta
    }

    // ============================================================================
    // ToolResultContent
    // ============================================================================

    @Test
    fun `ToolResultContent round-trips with text content block`() {
        val original = ToolResultContent(
            toolUseId = "call_1",
            content = listOf(TextContent("20°C sunny")),
        )
        val encoded = McpJson.encodeToString(ToolResultContent.serializer(), original)
        val decoded = McpJson.decodeFromString(ToolResultContent.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `ToolResultContent round-trips with mixed content including resource_link`() {
        val original = ToolResultContent(
            toolUseId = "call_2",
            content = listOf(
                TextContent("see file"),
                ResourceLink(name = "log", uri = "file:///tmp/a.log"),
            ),
        )
        val encoded = McpJson.encodeToString(ToolResultContent.serializer(), original)
        val decoded = McpJson.decodeFromString(ToolResultContent.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `ToolResultContent serialises with discriminator and preserves structuredContent and isError`() {
        val original = ToolResultContent(
            toolUseId = "call_3",
            content = emptyList(),
            structuredContent = buildJsonObject { put("temp", JsonPrimitive(20)) },
            isError = true,
        )
        val json = McpJson.encodeToString(ToolResultContent.serializer(), original)
        check("\"type\":\"tool_result\"" in json) { "missing discriminator in: $json" }
        val decoded = McpJson.decodeFromString(ToolResultContent.serializer(), json)
        decoded shouldBe original
    }

    // ============================================================================
    // SamplingMessageContentPolymorphicSerializer
    // ============================================================================

    @Test
    fun `SamplingMessageContent polymorphic serializer decodes text`() {
        val json = """[{"type":"text","text":"hi"}]"""
        val list = McpJson.decodeFromString(samplingContentSerializer, json)
        list[0].shouldBeInstanceOf<TextContent>().text shouldBe "hi"
    }

    @Test
    fun `SamplingMessageContent polymorphic serializer decodes tool_use`() {
        val json = """[{"type":"tool_use","id":"a","name":"n","input":{}}]"""
        val list = McpJson.decodeFromString(samplingContentSerializer, json)
        val use = list[0].shouldBeInstanceOf<ToolUseContent>()
        use.id shouldBe "a"
        use.name shouldBe "n"
    }

    @Test
    fun `SamplingMessageContent polymorphic serializer decodes tool_result`() {
        val json = """[{"type":"tool_result","toolUseId":"a","content":[]}]"""
        val list = McpJson.decodeFromString(samplingContentSerializer, json)
        list[0].shouldBeInstanceOf<ToolResultContent>().toolUseId shouldBe "a"
    }

    @Test
    fun `SamplingMessageContent polymorphic serializer throws on unknown discriminator`() {
        val json = """[{"type":"bogus","x":1}]"""
        assertFailsWith<SerializationException> {
            McpJson.decodeFromString(samplingContentSerializer, json)
        }
    }
}
