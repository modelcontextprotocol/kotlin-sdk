package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ContentTest {

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
}
