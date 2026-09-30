package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

class ResourcesTest {

    @Test
    fun `should serialize Resource with all fields`() {
        val resource = Resource(
            uri = "file:///workspace/CHANGELOG.md",
            name = "CHANGELOG",
            description = "Changelog for recent releases",
            mimeType = "text/markdown",
            size = 4096,
            title = "Project Changelog",
            annotations = Annotations(priority = 0.8, audience = listOf(Role.Assistant)),
            icons = listOf(
                Icon(src = "https://example.com/changelog.png"),
            ),
            meta = buildJsonObject { put("etag", "abc123") },
        )

        verifySerialization(
            resource,
            McpJson,
            """
            {
              "uri": "file:///workspace/CHANGELOG.md",
              "name": "CHANGELOG",
              "description": "Changelog for recent releases",
              "mimeType": "text/markdown",
              "size": 4096,
              "title": "Project Changelog",
              "annotations": {
                "priority": 0.8,
                "audience": ["assistant"]
              },
              "icons": [
                {"src": "https://example.com/changelog.png"}
              ],
              "_meta": {
                "etag": "abc123"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ResourceTemplate with meta`() {
        val template = ResourceTemplate(
            uriTemplate = "file:///workspace/{path}",
            name = "workspace-file",
            description = "Workspace file template",
            mimeType = "text/plain",
            title = "Workspace File",
            annotations = Annotations(
                priority = 0.6,
                audience = listOf(Role.User, Role.Assistant),
            ),
            icons = listOf(Icon(src = "https://example.com/file.svg", theme = Icon.Theme.Light)),
            meta = buildJsonObject { put("requiresAuth", true) },
        )

        verifySerialization(
            template,
            McpJson,
            """
            {
              "uriTemplate": "file:///workspace/{path}",
              "name": "workspace-file",
              "description": "Workspace file template",
              "mimeType": "text/plain",
              "title": "Workspace File",
              "annotations": {
                "priority": 0.6,
                "audience": ["user", "assistant"]
              },
              "icons": [
                {
                  "src": "https://example.com/file.svg",
                  "theme": "light"
                }
              ],
              "_meta": {
                "requiresAuth": true
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ListResourcesResult`() {
        val result = ListResourcesResult(
            resources = listOf(
                Resource(uri = "file:///workspace/README.md", name = "README"),
                Resource(uri = "file:///workspace/CONTRIBUTING.md", name = "CONTRIBUTING"),
            ),
            nextCursor = "cursor-2",
            meta = buildJsonObject { put("page", 1) },
        )

        verifySerialization<ServerResult>(
            result,
            McpJson,
            """
            {
              "resources": [
                {"uri": "file:///workspace/README.md", "name": "README"},
                {"uri": "file:///workspace/CONTRIBUTING.md", "name": "CONTRIBUTING"}
              ],
              "nextCursor": "cursor-2",
              "_meta": {
                "page": 1
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should dispatch ReadResourceResult contents by shape`() {
        val json = """
            {
              "contents": [
                {"text": "Section 1", "uri": "file:///workspace/report.txt", "mimeType": "text/plain"},
                {
                  "blob": "aW1hZ2VEYXRh",
                  "uri": "file:///workspace/diagram.png",
                  "mimeType": "image/png",
                  "_meta": {"size": 128}
                },
                {"uri": "custom://resource/42", "mimeType": "application/octet-stream"}
              ],
              "_meta": {"generatedAt": "2025-01-12T15:00:58Z"}
            }
        """.trimIndent()

        val result = verifyDeserialization<ServerResult>(McpJson, json).shouldBeInstanceOf<ReadResourceResult>()

        result.contents.map { it::class } shouldBe listOf(
            TextResourceContents::class,
            BlobResourceContents::class,
            UnknownResourceContents::class,
        )
    }

    @Test
    fun `should serialize ListResourceTemplatesResult`() {
        val result = ListResourceTemplatesResult(
            resourceTemplates = listOf(
                ResourceTemplate(uriTemplate = "file:///workspace/{path}", name = "workspace-file"),
            ),
            nextCursor = "cursor-templates-2",
            meta = buildJsonObject { put("page", 3) },
        )

        verifySerialization<ServerResult>(
            result,
            McpJson,
            """
            {
              "resourceTemplates": [
                {
                  "uriTemplate": "file:///workspace/{path}",
                  "name": "workspace-file"
                }
              ],
              "nextCursor": "cursor-templates-2",
              "_meta": {
                "page": 3
              }
            }
            """.trimIndent(),
        )
    }
}
