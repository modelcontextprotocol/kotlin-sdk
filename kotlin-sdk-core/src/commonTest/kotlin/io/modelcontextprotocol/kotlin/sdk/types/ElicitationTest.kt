package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ElicitationTest {

    @Test
    @Suppress("LongMethod")
    fun `should serialize ElicitRequest with requested schema`() {
        val request = ElicitRequest(
            ElicitRequestParams(
                message = "Provide repository details",
                requestedSchema = ElicitRequestParams.RequestedSchema(
                    properties = buildJsonObject {
                        put(
                            "owner",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "GitHub organization")
                            },
                        )
                        put(
                            "repository",
                            buildJsonObject {
                                put("type", "string")
                                put("title", "Repository name")
                            },
                        )
                        put(
                            "private",
                            buildJsonObject {
                                put("type", "boolean")
                                put("description", "Is the repository private?")
                            },
                        )
                    },
                    required = listOf("owner", "repository"),
                ),
                meta = RequestMeta(
                    buildJsonObject {
                        put("progressToken", "token-42")
                    },
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
                      "type": "string",
                      "description": "GitHub organization"
                    },
                    "repository": {
                      "type": "string",
                      "title": "Repository name"
                    },
                    "private": {
                      "type": "boolean",
                      "description": "Is the repository private?"
                    }
                  },
                  "required": ["owner", "repository"],
                  "type": "object"
                },
                "_meta": {
                  "progressToken": "token-42"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize ElicitRequest from JSON`() {
        val json = """
            {
              "method": "elicitation/create",
              "params": {
                "message": "Tell us about the repository",
                "requestedSchema": {
                  "properties": {
                    "name": {
                      "type": "string",
                      "title": "Repository name"
                    },
                    "stars": {
                      "type": "number",
                      "description": "GitHub stars"
                    }
                  },
                  "type": "object"
                }
              }
            }
        """.trimIndent()

        val request = verifyDeserialization<ElicitRequest>(McpJson, json)
        assertEquals(Method.Defined.ElicitationCreate, request.method)

        val params = request.params
        assertEquals("Tell us about the repository", params.message)
        assertNull(params.meta)

        val schema = params.requestedSchema
        assertEquals("object", schema.type)
        assertNull(schema.required)

        val nameDefinition = schema.properties["name"]?.jsonObject
        assertNotNull(nameDefinition)
        assertEquals("string", nameDefinition["type"]?.jsonPrimitive?.content)
        assertEquals("Repository name", nameDefinition["title"]?.jsonPrimitive?.content)

        val starsDefinition = schema.properties["stars"]?.jsonObject
        assertNotNull(starsDefinition)
        assertEquals("number", starsDefinition["type"]?.jsonPrimitive?.content)
        assertEquals("GitHub stars", starsDefinition["description"]?.jsonPrimitive?.content)
    }

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
        assertEquals(ElicitResult.Action.Accept, decoded.action)

        val content = decoded.content
        assertNotNull(content)
        assertEquals("kotlin-sdk", content["repository"]?.jsonPrimitive?.content)
        assertEquals(128, content["stars"]?.jsonPrimitive?.int)
        assertEquals(false, content["private"]?.jsonPrimitive?.boolean)

        val meta = decoded.meta
        assertNotNull(meta)
        assertEquals("2025-01-12T15:00:58Z", meta["submittedAt"]?.jsonPrimitive?.content)
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
        assertEquals(ElicitResult.Action.Decline, result.action)
        assertNull(result.content)
        assertEquals("User skipped", result.meta?.get("reason")?.jsonPrimitive?.content)
    }

    @Test
    fun `should require content only for accept action`() {
        assertFailsWith<IllegalArgumentException> {
            ElicitResult(
                action = ElicitResult.Action.Cancel,
                content = buildJsonObject { put("value", "ignored") },
            )
        }
    }
}
