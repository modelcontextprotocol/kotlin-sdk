package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ResourcesTest {

    @Test
    fun `should serialize Resource with minimal fields`() {
        val resource = Resource(
            uri = "file:///workspace/README.md",
            name = "README",
        )

        val json = McpJson.encodeToString(resource)

        json shouldEqualJson """
            {
              "uri": "file:///workspace/README.md",
              "name": "README"
            }
        """.trimIndent()
    }

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

        val json = McpJson.encodeToString(resource)

        json shouldEqualJson """
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
        """.trimIndent()
    }

    @Test
    fun `should deserialize Resource from JSON`() {
        val json = """
            {
              "uri": "file:///config/settings.json",
              "name": "config-settings",
              "description": "Configuration file",
              "mimeType": "application/json",
              "size": 1024,
              "title": "Settings",
              "annotations": {
                "priority": 0.5
              },
              "icons": [
                {"src": "https://example.com/settings.png"}
              ],
              "_meta": {
                "etag": "\"98765\""
              }
            }
        """.trimIndent()

        val resource = McpJson.decodeFromString<Resource>(json)

        assertEquals("file:///config/settings.json", resource.uri)
        assertEquals("config-settings", resource.name)
        assertEquals("Configuration file", resource.description)
        assertEquals("application/json", resource.mimeType)
        assertEquals(1024, resource.size)
        assertEquals("Settings", resource.title)
        assertEquals(0.5, resource.annotations?.priority)
        val meta = resource.meta
        assertNotNull(meta)
        assertEquals("\"98765\"", meta["etag"]?.jsonPrimitive?.content)
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

        val json = McpJson.encodeToString(template)

        json shouldEqualJson """
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
        """.trimIndent()
    }

    @Test
    fun `should deserialize ResourceTemplate from JSON`() {
        val json = """
            {
              "uriTemplate": "db://users/{id}",
              "name": "user-record",
              "description": "Database record template",
              "annotations": {
                "priority": 1.0
              }
            }
        """.trimIndent()

        val template = McpJson.decodeFromString<ResourceTemplate>(json)

        assertEquals("db://users/{id}", template.uriTemplate)
        assertEquals("user-record", template.name)
        assertEquals("Database record template", template.description)
        assertEquals(1.0, template.annotations?.priority)
        assertNull(template.meta)
    }

    @Test
    fun `should serialize and deserialize ResourceTemplateReference`() {
        val reference = ResourceTemplateReference(uri = "file:///workspace/{path}")

        val json = McpJson.encodeToString(reference)

        json shouldEqualJson """
            {
              "type": "ref/resource",
              "uri": "file:///workspace/{path}"
            }
        """.trimIndent()

        val decoded = McpJson.decodeFromString<Reference>(json)
        val templateReference = assertIs<ResourceTemplateReference>(decoded)
        assertEquals("file:///workspace/{path}", templateReference.uri)
        assertEquals(ReferenceType.ResourceTemplate, templateReference.type)
    }

    @Test
    fun `should serialize TextResourceContents`() {
        val contents = TextResourceContents(
            text = "Hello, MCP!",
            uri = "file:///workspace/hello.txt",
            mimeType = "text/plain",
            meta = buildJsonObject { put("encoding", "utf-8") },
        )

        val json = McpJson.encodeToString<ResourceContents>(contents)

        json shouldEqualJson """
            {
              "text": "Hello, MCP!",
              "uri": "file:///workspace/hello.txt",
              "mimeType": "text/plain",
              "_meta": {
                "encoding": "utf-8"
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize BlobResourceContents`() {
        val json = """
            {
              "blob": "YmluYXJ5ZGF0YQ==",
              "uri": "file:///workspace/logo.png",
              "mimeType": "image/png",
              "_meta": {
                "size": 128
              }
            }
        """.trimIndent()

        val contents = McpJson.decodeFromString<ResourceContents>(json)
        val blobContents = assertIs<BlobResourceContents>(contents)
        assertEquals("YmluYXJ5ZGF0YQ==", blobContents.blob)
        assertEquals("file:///workspace/logo.png", blobContents.uri)
        assertEquals("image/png", blobContents.mimeType)
        assertEquals(128, blobContents.meta?.get("size")?.jsonPrimitive?.int)
    }

    @Test
    fun `should serialize UnknownResourceContents`() {
        val contents = UnknownResourceContents(
            uri = "custom://resource/42",
            mimeType = "application/octet-stream",
        )

        val json = McpJson.encodeToString<ResourceContents>(contents)

        json shouldEqualJson """
            {
              "uri": "custom://resource/42",
              "mimeType": "application/octet-stream"
            }
        """.trimIndent()
    }

    @Test
    fun `ResourceContents should throw on non-object JSON`() {
        val exception = shouldThrow<SerializationException> {
            McpJson.decodeFromString<ResourceContents>("\"just a string\"")
        }

        exception.message shouldBe "Invalid response. JsonObject expected, got: \"just a string\""
    }

    @Test
    fun `should serialize ListResourcesRequest with cursor`() {
        val request = ListResourcesRequest(
            PaginatedRequestParams(
                cursor = "cursor-1",
                meta = RequestMeta(buildJsonObject { put("progressToken", "list-resources-1") }),
            ),
        )

        val json = McpJson.encodeToString(request)

        json shouldEqualJson """
            {
              "method": "resources/list",
              "params": {
                "cursor": "cursor-1",
                "_meta": {
                  "progressToken": "list-resources-1"
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize ListResourcesRequest without params`() {
        val request = ListResourcesRequest()

        val json = McpJson.encodeToString(request)

        json shouldEqualJson """
            {
              "method": "resources/list"
            }
        """.trimIndent()
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

        val json = McpJson.encodeToString(result)

        json shouldEqualJson """
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
        """.trimIndent()
    }

    @Test
    fun `should deserialize ListResourcesResult`() {
        // language=json
        val json = """
            {
              "resources": [
                {
                  "uri": "file:///workspace/docs/guide.md",
                  "name": "guide",
                  "description": "User guide"
                }
              ],
              "nextCursor": "cursor-next",
              "_meta": {
                "page": 2
              }
            }
        """.trimIndent()

        val result = McpJson.decodeFromString<ListResourcesResult>(json)

        assertEquals("cursor-next", result.nextCursor)
        val resources = result.resources
        assertEquals(1, resources.size)
        val resource = resources.first()
        assertEquals("file:///workspace/docs/guide.md", resource.uri)
        assertEquals("guide", resource.name)
        assertEquals("User guide", resource.description)
        assertEquals(2, result.meta?.get("page")?.jsonPrimitive?.int)
    }

    @Test
    fun `should serialize ReadResourceRequest`() {
        val request = ReadResourceRequest(
            ReadResourceRequestParams(
                uri = "file:///workspace/notes.txt",
                meta = RequestMeta(buildJsonObject { put("progressToken", "read-1") }),
            ),
        )

        val json = McpJson.encodeToString(request)

        json shouldEqualJson """
            {
              "method": "resources/read",
              "params": {
                "uri": "file:///workspace/notes.txt",
                "_meta": {
                  "progressToken": "read-1"
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize ReadResourceResult with mixed contents`() {
        // language=json
        val json = """
            {
              "contents": [
                {
                  "text": "Section 1",
                  "uri": "file:///workspace/report.txt",
                  "mimeType": "text/plain"
                },
                {
                  "blob": "aW1hZ2VEYXRh",
                  "uri": "file:///workspace/diagram.png",
                  "mimeType": "image/png"
                }
              ],
              "_meta": {
                "generatedAt": "2025-01-12T15:00:58Z"
              }
            }
        """.trimIndent()

        val result = McpJson.decodeFromString<ReadResourceResult>(json)

        val contents = result.contents
        assertEquals(2, contents.size)
        assertIs<TextResourceContents>(contents[0])
        assertIs<BlobResourceContents>(contents[1])
        val meta = result.meta
        assertNotNull(meta)
        assertEquals("2025-01-12T15:00:58Z", meta["generatedAt"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should serialize SubscribeRequest`() {
        val request = SubscribeRequest(
            SubscribeRequestParams(
                uri = "file:///workspace/todo.md",
                meta = RequestMeta(buildJsonObject { put("progressToken", 42) }),
            ),
        )

        val json = McpJson.encodeToString(request)

        json shouldEqualJson """
            {
              "method": "resources/subscribe",
              "params": {
                "uri": "file:///workspace/todo.md",
                "_meta": {
                  "progressToken": 42
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize UnsubscribeRequest`() {
        val request = UnsubscribeRequest(
            UnsubscribeRequestParams(
                uri = "file:///workspace/todo.md",
                meta = RequestMeta(buildJsonObject { put("progressToken", "unsubscribe-1") }),
            ),
        )

        val json = McpJson.encodeToString(request)

        json shouldEqualJson """
            {
              "method": "resources/unsubscribe",
              "params": {
                "uri": "file:///workspace/todo.md",
                "_meta": {
                  "progressToken": "unsubscribe-1"
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize ListResourceTemplatesRequest`() {
        val request = ListResourceTemplatesRequest(
            PaginatedRequestParams(
                cursor = "template-1",
                meta = RequestMeta(buildJsonObject { put("progressToken", "templates-1") }),
            ),
        )

        val json = McpJson.encodeToString(request)

        json shouldEqualJson """
            {
              "method": "resources/templates/list",
              "params": {
                "cursor": "template-1",
                "_meta": {
                  "progressToken": "templates-1"
                }
              }
            }
        """.trimIndent()
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

        val json = McpJson.encodeToString(result)

        json shouldEqualJson """
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
        """.trimIndent()
    }

    @Test
    fun `should deserialize ListResourceTemplatesResult`() {
        val json = """
            {
              "resourceTemplates": [
                {
                  "uriTemplate": "db://records/{id}",
                  "name": "record",
                  "description": "Database record",
                  "icons": [
                    {"src": "https://example.com/db.png"}
                  ]
                }
              ],
              "nextCursor": "cursor-next",
              "_meta": {
                "page": 1
              }
            }
        """.trimIndent()

        val result = McpJson.decodeFromString<ListResourceTemplatesResult>(json)

        assertEquals("cursor-next", result.nextCursor)
        val templates = result.resourceTemplates
        assertEquals(1, templates.size)
        val template = templates.first()
        assertEquals("db://records/{id}", template.uriTemplate)
        assertEquals("record", template.name)
        assertEquals("Database record", template.description)
        val icons = template.icons
        assertNotNull(icons)
        assertEquals("https://example.com/db.png", icons.first().src)
        assertEquals(1, result.meta?.get("page")?.jsonPrimitive?.int)
    }
}
