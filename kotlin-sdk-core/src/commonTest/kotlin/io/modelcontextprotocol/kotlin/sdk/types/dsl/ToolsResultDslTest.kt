package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.buildCallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.buildListToolsResult
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test

/**
 * Tests for ToolsResult DSL builders.
 *
 * Verifies CallToolResult and ListToolsResult can be constructed via DSL,
 * covering minimal (required only), full (all fields), and edge cases.
 */
@OptIn(ExperimentalMcpApi::class)
class ToolsResultDslTest {

    // ========================================================================
    // CallToolResult Tests
    // ========================================================================

    @Test
    fun `CallToolResult should build minimal with single text content`() {
        val result = buildCallToolResult {
            textContent("Operation successful")
        }

        result.content shouldHaveSize 1
        (result.content[0] as TextContent).text shouldBe "Operation successful"
        result.isError.shouldBeNull()
        result.structuredContent.shouldBeNull()
        result.meta.shouldBeNull()
    }

    @Test
    fun `CallToolResult should build full with all content types and structured data`() {
        val result = buildCallToolResult {
            textContent("Database query completed")
            @Suppress("MaxLineLength")
            imageContent(
                data =
                "iVBORw0KGgoggg==",
                mimeType = "image/png",
            )
            audioContent(
                data = "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAAABmYWN0BAAAAAAAAABkYXRhAAAAAA==",
                mimeType = "audio/wav",
            )
            isError = false
            structuredContent {
                put("queryTime", 150)
                put("recordsAffected", 42)
                putJsonArray("columns") {
                    add("id")
                    add("name")
                    add("email")
                }
            }
            meta {
                put("source", "postgres")
                put("cached", true)
            }
        }

        result.content shouldHaveSize 3
        result.content[0].shouldBeInstanceOf<TextContent>()
        result.content[1].shouldBeInstanceOf<ImageContent>()
        result.content[2].shouldBeInstanceOf<AudioContent>()

        result.isError shouldBe false

        result.structuredContent shouldNotBeNull {
            get("queryTime")?.jsonPrimitive?.int shouldBe 150
            get("recordsAffected")?.jsonPrimitive?.int shouldBe 42
            get("columns")?.jsonArray?.map { it.jsonPrimitive.content }
                ?.let { it shouldContainAll listOf("id", "name", "email") }
        }

        result.meta shouldNotBeNull {
            get("source")?.jsonPrimitive?.content shouldBe "postgres"
            get("cached")?.jsonPrimitive?.boolean shouldBe true
        }
    }

    @Test
    fun `CallToolResult should support error status`() {
        val result = buildCallToolResult {
            textContent("Failed to connect to database")
            isError = true
        }

        result.isError shouldBe true
        (result.content[0] as TextContent).text shouldBe "Failed to connect to database"
    }

    @Test
    fun `CallToolResult should support error with structuredContent`() {
        val result = buildCallToolResult {
            textContent("Operation failed: timeout exceeded")
            isError = true
            structuredContent {
                put("errorCode", "TIMEOUT")
                put("retryAfter", 5000)
                put("message", "The operation timed out after 30 seconds")
            }
        }

        result.isError shouldBe true
        result.content shouldHaveSize 1
        result.structuredContent shouldNotBeNull {
            get("errorCode")?.jsonPrimitive?.content shouldBe "TIMEOUT"
            get("retryAfter")?.jsonPrimitive?.int shouldBe 5000
        }
    }

    @Test
    fun `CallToolResult should support error with multiple content items`() {
        val result = buildCallToolResult {
            textContent("Error occurred during processing")
            textContent("Stack trace: at line 42 in module.kt")
            textContent("Please contact support if this persists")
            isError = true
        }

        result.isError shouldBe true
        result.content shouldHaveSize 3
        (result.content[0] as TextContent).text shouldBe "Error occurred during processing"
        (result.content[2] as TextContent).text shouldBe "Please contact support if this persists"
    }

    @Test
    fun `CallToolResult should throw if no content provided`() {
        shouldThrow<IllegalArgumentException> {
            buildCallToolResult { }
        }
    }

    // ========================================================================
    // ListToolsResult Tests
    // ========================================================================

    @Test
    fun `ListToolsResult should build minimal with single tool`() {
        val result = buildListToolsResult {
            tool {
                name = "getCurrentTime"
                inputSchema {
                    // Empty schema for no parameters
                }
            }
        }

        result.tools shouldHaveSize 1
        result.tools[0].name shouldBe "getCurrentTime"
        result.nextCursor.shouldBeNull()
        result.meta.shouldBeNull()
    }

    @Test
    @Suppress("LongMethod")
    fun `ListToolsResult should build full with multiple tools and pagination`() {
        val result = buildListToolsResult {
            tool {
                name = "searchDatabase"
                description = "Search the database for records"
                title = "Database Search"
                inputSchema {
                    properties = buildJsonObject {
                        putJsonObject("query") {
                            put("type", "string")
                            put("description", "Search query")
                        }
                        putJsonObject("limit") {
                            put("type", "integer")
                            put("default", 10)
                        }
                    }
                    required = listOf("query")
                }
                outputSchema {
                    properties = buildJsonObject {
                        putJsonObject("results") {
                            put("type", "array")
                        }
                    }
                }
                annotations = ToolAnnotations(
                    readOnlyHint = true,
                    idempotentHint = true,
                )
            }

            tool {
                name = "updateRecord"
                description = "Update a database record"
                inputSchema {
                    properties = buildJsonObject {
                        putJsonObject("id") {
                            put("type", "integer")
                        }
                        putJsonObject("data") {
                            put("type", "object")
                        }
                    }
                    required = listOf("id", "data")
                }
                annotations = ToolAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                )
            }

            nextCursor = "eyJwYWdlIjogMn0="

            meta {
                put("serverVersion", "1.0.0")
                put("cached", false)
            }
        }

        result.tools shouldHaveSize 2

        result.tools[0].let { tool ->
            tool.name shouldBe "searchDatabase"
            tool.description shouldBe "Search the database for records"
            tool.title shouldBe "Database Search"
            tool.inputSchema shouldNotBeNull {
                required shouldBe listOf("query")
            }
            tool.annotations shouldNotBeNull {
                readOnlyHint shouldBe true
                idempotentHint shouldBe true
            }
        }

        result.tools[1].let { tool ->
            tool.name shouldBe "updateRecord"
            tool.annotations shouldNotBeNull {
                readOnlyHint shouldBe false
                destructiveHint shouldBe false
            }
        }

        result.nextCursor shouldBe "eyJwYWdlIjogMn0="

        result.meta shouldNotBeNull {
            get("serverVersion")?.jsonPrimitive?.content shouldBe "1.0.0"
            get("cached")?.jsonPrimitive?.boolean shouldBe false
        }
    }

    @Test
    fun `ListToolsResult should support tool with defs in schema`() {
        val result = buildListToolsResult {
            tool {
                name = "createUser"
                inputSchema {
                    properties = buildJsonObject {
                        putJsonObject("user") {
                            put("\$ref", "#/\$defs/User")
                        }
                    }
                    defs = buildJsonObject {
                        putJsonObject("User") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("name") {
                                    put("type", "string")
                                }
                                putJsonObject("email") {
                                    put("type", "string")
                                }
                            }
                        }
                    }
                }
            }
        }

        result.tools[0].inputSchema.defs shouldNotBeNull {
            get("User") shouldNotBeNull {}
        }
    }

    @Test
    fun `ListToolsResult should throw if no tools provided`() {
        shouldThrow<IllegalArgumentException> {
            buildListToolsResult { }
        }
    }

    @Test
    fun `ListToolsResult should support empty input schema`() {
        val result = buildListToolsResult {
            tool {
                name = "noArgs"
                inputSchema {
                    // No properties, no required fields
                }
            }
        }

        result.tools[0].inputSchema shouldNotBeNull {
            properties.shouldBeNull()
            required.shouldBeNull()
        }
    }
}
