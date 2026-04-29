package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

class ElicitationTest {

    // ── Form mode ───────────────────────────────────────────────────────

    @Test
    fun `should serialize form mode ElicitRequest`() {
        val request = ElicitRequest(
            ElicitRequestFormParams(
                message = "Provide repository details",
                requestedSchema = ElicitRequestParams.RequestedSchema(
                    properties = mapOf(
                        "owner" to StringSchema(description = "GitHub organization"),
                        "repository" to StringSchema(title = "Repository name"),
                        "private" to BooleanSchema(description = "Is the repository private?"),
                    ),
                    required = listOf("owner", "repository"),
                ),
                meta = RequestMeta(
                    buildJsonObject { put("progressToken", "token-42") },
                ),
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "elicitation/create",
              "params": {
                "message": "Provide repository details",
                "requestedSchema": {
                  "properties": {
                    "owner": {
                      "description": "GitHub organization",
                      "type": "string"
                    },
                    "repository": {
                      "title": "Repository name",
                      "type": "string"
                    },
                    "private": {
                      "description": "Is the repository private?",
                      "type": "boolean"
                    }
                  },
                  "required": ["owner", "repository"],
                  "type": "object"
                },
                "_meta": {
                  "progressToken": "token-42"
                },
                "mode": "form"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize form mode ElicitRequest with explicit mode`() {
        val json = """
            {
              "method": "elicitation/create",
              "params": {
                "mode": "form",
                "message": "Enter your name",
                "requestedSchema": {
                  "properties": {
                    "name": {"type": "string", "title": "Full name"}
                  },
                  "type": "object"
                }
              }
            }
        """.trimIndent()

        val request = verifyDeserialization<ElicitRequest>(McpJson, json)
        request.method shouldBe Method.Defined.ElicitationCreate
        request.message shouldBe "Enter your name"

        val params = request.params.shouldBeInstanceOf<ElicitRequestFormParams>()
        val nameSchema = params.requestedSchema.properties["name"].shouldBeInstanceOf<StringSchema>()
        nameSchema.title shouldBe "Full name"
    }

    @Test
    fun `should deserialize form mode ElicitRequest without mode field`() {
        val json = """
            {
              "method": "elicitation/create",
              "params": {
                "message": "Enter your name",
                "requestedSchema": {
                  "properties": {
                    "name": {"type": "string"}
                  },
                  "type": "object"
                }
              }
            }
        """.trimIndent()

        val request = McpJson.decodeFromString<ElicitRequest>(json)
        request.params.shouldBeInstanceOf<ElicitRequestFormParams>()
        request.params.message shouldBe "Enter your name"
    }

    // ── URL mode ────────────────────────────────────────────────────────

    @Test
    fun `should serialize URL mode ElicitRequest`() {
        val request = ElicitRequest(
            ElicitRequestURLParams(
                message = "Please provide your API key",
                elicitationId = "550e8400-e29b-41d4-a716-446655440000",
                url = "https://mcp.example.com/ui/set_api_key",
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "elicitation/create",
              "params": {
                "message": "Please provide your API key",
                "elicitationId": "550e8400-e29b-41d4-a716-446655440000",
                "url": "https://mcp.example.com/ui/set_api_key",
                "mode": "url"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize URL mode ElicitRequest`() {
        val json = """
            {
              "method": "elicitation/create",
              "params": {
                "mode": "url",
                "message": "Authorize access",
                "elicitationId": "id-123",
                "url": "https://example.com/auth"
              }
            }
        """.trimIndent()

        val request = verifyDeserialization<ElicitRequest>(McpJson, json)
        val params = request.params.shouldBeInstanceOf<ElicitRequestURLParams>()
        params.message shouldBe "Authorize access"
        params.elicitationId shouldBe "id-123"
        params.url shouldBe "https://example.com/auth"
    }

    // ── Deprecated compat ───────────────────────────────────────────────

    @Test
    fun `deprecated factory function should create ElicitRequestFormParams`() {
        val schema = ElicitRequestParams.RequestedSchema(
            properties = mapOf("name" to StringSchema()),
        )
        val params = ElicitRequestParams(message = "Test", requestedSchema = schema)
        params.shouldBeInstanceOf<ElicitRequestFormParams>()
        params.message shouldBe "Test"
        params.requestedSchema shouldBe schema
    }

    @Test
    fun `deprecated requestedSchema should return schema for form mode`() {
        val schema = ElicitRequestParams.RequestedSchema(
            properties = mapOf("x" to BooleanSchema()),
        )
        val request = ElicitRequest(ElicitRequestFormParams(message = "m", requestedSchema = schema))
        request.requestedSchema shouldBe schema
    }

    @Test
    fun `deprecated requestedSchema should return null for URL mode`() {
        val request = ElicitRequest(
            ElicitRequestURLParams(message = "m", elicitationId = "id", url = "https://example.com"),
        )
        request.requestedSchema.shouldBeNull()
    }

    // ── ElicitResult ────────────────────────────────────────────────────

    @Test
    fun `should serialize and deserialize accept result with content`() {
        val result = ElicitResult(
            action = ElicitResult.Action.Accept,
            content = buildJsonObject {
                put("repository", "kotlin-sdk")
                put("stars", 128)
                put("private", false)
            },
            meta = buildJsonObject {
                put("submittedAt", "2025-01-12T15:00:58Z")
            },
        )

        val json = McpJson.encodeToString(result)

        json shouldEqualJson """
            {
              "action": "accept",
              "content": {
                "repository": "kotlin-sdk",
                "stars": 128,
                "private": false
              },
              "_meta": {
                "submittedAt": "2025-01-12T15:00:58Z"
              }
            }
        """.trimIndent()

        val decoded = verifyDeserialization<ElicitResult>(McpJson, json)
        decoded.action shouldBe ElicitResult.Action.Accept

        val content = decoded.content.shouldNotBeNull()
        content["repository"]?.jsonPrimitive?.content shouldBe "kotlin-sdk"
        content["stars"]?.jsonPrimitive?.int shouldBe 128
        content["private"]?.jsonPrimitive?.boolean shouldBe false

        val meta = decoded.meta.shouldNotBeNull()
        meta["submittedAt"]?.jsonPrimitive?.content shouldBe "2025-01-12T15:00:58Z"
    }

    @Test
    fun `should deserialize decline result without content`() {
        val json = """
            {
              "action": "decline",
              "_meta": {
                "reason": "User skipped"
              }
            }
        """.trimIndent()

        val result = verifyDeserialization<ElicitResult>(McpJson, json)
        result.action shouldBe ElicitResult.Action.Decline
        result.content.shouldBeNull()
        result.meta?.get("reason")?.jsonPrimitive?.content shouldBe "User skipped"
    }

    @Test
    fun `should require content only for accept action`() {
        shouldThrow<IllegalArgumentException> {
            ElicitResult(
                action = ElicitResult.Action.Cancel,
                content = buildJsonObject { put("value", "ignored") },
            )
        }
    }
}
