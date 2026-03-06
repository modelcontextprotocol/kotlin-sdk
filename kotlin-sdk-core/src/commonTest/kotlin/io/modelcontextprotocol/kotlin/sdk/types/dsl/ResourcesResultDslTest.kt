package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.Annotations
import io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.buildListResourceTemplatesResult
import io.modelcontextprotocol.kotlin.sdk.types.buildListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.types.buildReadResourceResult
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

/**
 * Tests for ResourcesResult DSL builders.
 *
 * Verifies resource result types can be constructed via DSL.
 */
@OptIn(ExperimentalMcpApi::class)
class ResourcesResultDslTest {

    @Test
    fun `ListResourcesResult should build minimal with single resource`() {
        val result = buildListResourcesResult {
            resource {
                uri = "file:///path/to/file.txt"
                name = "file.txt"
            }
        }

        result.resources shouldHaveSize 1
        result.resources[0].uri shouldBe "file:///path/to/file.txt"
        result.resources[0].name shouldBe "file.txt"
    }

    @Test
    fun `ListResourcesResult should build full with multiple resources`() {
        val result = buildListResourcesResult {
            resource {
                uri = "file:///docs/readme.md"
                name = "readme.md"
                description = "Project documentation"
                mimeType = "text/markdown"
                size = 2048
                title = "README"
                annotations = Annotations(
                    audience = listOf(Role.User),
                    priority = 0.9,
                )
            }

            resource {
                uri = "db://users/123"
                name = "user-123"
                description = "User profile data"
                mimeType = "application/json"
            }

            nextCursor = "next-page"
            meta {
                put("cached", true)
            }
        }

        result.resources shouldHaveSize 2
        result.nextCursor shouldBe "next-page"
    }

    @Test
    fun `ReadResourceResult should build with text content`() {
        val result = buildReadResourceResult {
            textContent(
                uri = "file:///docs/readme.md",
                text = "# Project README\n\nWelcome!",
                mimeType = "text/markdown",
            )
        }

        result.contents shouldHaveSize 1
        (result.contents[0] as TextResourceContents).let {
            it.uri shouldBe "file:///docs/readme.md"
            it.text shouldBe "# Project README\n\nWelcome!"
            it.mimeType shouldBe "text/markdown"
        }
    }

    @Test
    fun `ReadResourceResult should build with blob content`() {
        val result = buildReadResourceResult {
            blobContent(
                uri = "file:///images/logo.png",
                blob = "iVBORw0KGgoggg==",
                mimeType = "image/png",
            )
        }

        result.contents shouldHaveSize 1
        (result.contents[0] as BlobResourceContents).let {
            it.uri shouldBe "file:///images/logo.png"
            it.mimeType shouldBe "image/png"
        }
    }

    @Test
    fun `ListResourceTemplatesResult should build with templates`() {
        val result = buildListResourceTemplatesResult {
            template {
                uriTemplate = "file:///{path}"
                name = "file-template"
                description = "Access any file"
                mimeType = "text/plain"
            }

            template {
                uriTemplate = "db://users/{userId}"
                name = "user-db-template"
            }

            nextCursor = "next"
        }

        result.resourceTemplates shouldHaveSize 2
        result.resourceTemplates[0].uriTemplate shouldBe "file:///{path}"
    }

    @Test
    fun `ListResourcesResult should throw if no resources`() {
        shouldThrow<IllegalArgumentException> {
            buildListResourcesResult { }
        }
    }

    @Test
    fun `ReadResourceResult should throw if no contents`() {
        shouldThrow<IllegalArgumentException> {
            buildReadResourceResult { }
        }
    }

    @Test
    fun `ListResourcesResult should build full with all optional fields`() {
        val result = buildListResourcesResult {
            resource {
                uri = "file:///docs/api.md"
                name = "api-documentation"
                description = "Complete API documentation"
                mimeType = "text/markdown"
                size = 4096
                title = "API Reference"
                annotations = Annotations(
                    audience = listOf(Role.User, Role.Assistant),
                    priority = 0.8,
                )
            }
            nextCursor = "page-2"
            meta {
                put("totalResources", 150)
                put("cached", true)
            }
        }

        result.resources shouldHaveSize 1
        result.resources[0].let { resource ->
            resource.uri shouldBe "file:///docs/api.md"
            resource.name shouldBe "api-documentation"
            resource.description shouldBe "Complete API documentation"
            resource.mimeType shouldBe "text/markdown"
            resource.size shouldBe 4096
            resource.title shouldBe "API Reference"
            resource.annotations shouldNotBeNull {
                audience shouldBe listOf(Role.User, Role.Assistant)
                priority shouldBe 0.8
            }
        }
        result.nextCursor shouldBe "page-2"
        result.meta shouldNotBeNull {}
    }

    @Test
    fun `ReadResourceResult should build with multiple content items`() {
        val result = buildReadResourceResult {
            textContent(
                uri = "file:///docs/part1.md",
                text = "# Part 1\n\nIntroduction",
                mimeType = "text/markdown",
            )
            textContent(
                uri = "file:///docs/part2.md",
                text = "# Part 2\n\nDetails",
                mimeType = "text/markdown",
            )
            blobContent(
                uri = "file:///images/diagram.png",
                blob = "iVBORw0KGgoggg==",
                mimeType = "image/png",
            )
        }

        result.contents shouldHaveSize 3
        (result.contents[0] as TextResourceContents).uri shouldBe "file:///docs/part1.md"
        (result.contents[1] as TextResourceContents).uri shouldBe "file:///docs/part2.md"
        (result.contents[2] as BlobResourceContents).uri shouldBe "file:///images/diagram.png"
    }

    @Test
    fun `ReadResourceResult should support meta field`() {
        val result = buildReadResourceResult {
            textContent(
                uri = "file:///data.json",
                text = """{"key": "value"}""",
                mimeType = "application/json",
            )
            meta {
                put("source", "database")
                put("lastModified", 1707317000000L)
                put("cached", false)
            }
        }

        result.meta shouldNotBeNull {
            get("source")?.jsonPrimitive?.content shouldBe "database"
            get("cached")?.jsonPrimitive?.boolean shouldBe false
        }
    }

    @Test
    fun `ListResourceTemplatesResult should support pagination with nextCursor`() {
        val result = buildListResourceTemplatesResult {
            template {
                uriTemplate = "db://users/{userId}"
                name = "user-template"
            }
            template {
                uriTemplate = "db://posts/{postId}"
                name = "post-template"
            }
            nextCursor = "template-page-2"
        }

        result.resourceTemplates shouldHaveSize 2
        result.nextCursor shouldBe "template-page-2"
    }

    @Test
    fun `ListResourceTemplatesResult should support meta field`() {
        val result = buildListResourceTemplatesResult {
            template {
                uriTemplate = "api://{endpoint}"
                name = "api-template"
            }
            meta {
                put("version", "2.0")
                put("totalTemplates", 25)
            }
        }

        result.meta shouldNotBeNull {
            get("version")?.jsonPrimitive?.content shouldBe "2.0"
        }
    }

    @Test
    fun `ListResourcesResult should support resources with minimal and full fields together`() {
        val result = buildListResourcesResult {
            resource {
                uri = "simple://resource1"
                name = "minimal"
            }
            resource {
                uri = "complex://resource2"
                name = "complete"
                description = "Full resource"
                mimeType = "application/json"
                size = 2048
                title = "Complete Resource"
            }
        }

        result.resources shouldHaveSize 2
        result.resources[0].description.shouldBeNull()
        result.resources[1].description shouldBe "Full resource"
    }
}
