package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.Annotations
import io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.buildListResourceTemplatesResult
import io.modelcontextprotocol.kotlin.sdk.types.buildListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.types.buildReadResourceResult
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
}
