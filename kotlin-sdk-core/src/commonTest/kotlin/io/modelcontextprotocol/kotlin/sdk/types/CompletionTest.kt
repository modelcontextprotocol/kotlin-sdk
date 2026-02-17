package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CompletionTest {

    @Test
    fun `should serialize CompleteRequest with minimal fields`() {
        val request = CompleteRequest(
            CompleteRequestParams(
                argument = CompleteRequestParams.Argument(
                    name = "name",
                    value = "A",
                ),
                ref = PromptReference(name = "greeting"),
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "completion/complete",
              "params": {
                "argument": {
                  "name": "name",
                  "value": "A"
                },
                "ref": {
                  "type": "ref/prompt",
                  "name": "greeting"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize CompleteRequest with context and meta`() {
        val request = CompleteRequest(
            CompleteRequestParams(
                argument = CompleteRequestParams.Argument(
                    name = "repo",
                    value = "mcp",
                ),
                ref = ResourceTemplateReference(uri = "github://repos/{owner}/{repo}"),
                context = CompleteRequestParams.Context(
                    arguments = mapOf(
                        "owner" to "modelcontextprotocol",
                        "language" to "kotlin",
                    ),
                ),
                meta = RequestMeta(
                    buildJsonObject {
                        put("progressToken", "token-123")
                    },
                ),
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "completion/complete",
              "params": {
                "argument": {
                  "name": "repo",
                  "value": "mcp"
                },
                "ref": {
                  "type": "ref/resource",
                  "uri": "github://repos/{owner}/{repo}"
                },
                "context": {
                  "arguments": {
                    "owner": "modelcontextprotocol",
                    "language": "kotlin"
                  }
                },
                "_meta": {
                  "progressToken": "token-123"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize CompleteRequest from JSON`() {
        val json = """
            {
              "method": "completion/complete",
              "params": {
                "argument": {
                  "name": "file",
                  "value": "mai"
                },
                "ref": {
                  "type": "ref/resource",
                  "uri": "file:///{path}"
                },
                "context": {
                  "arguments": {
                    "path": "src/main"
                  }
                },
                "_meta": {
                  "progressToken": 42
                }
              }
            }
        """.trimIndent()

        val request = verifyDeserialization<CompleteRequest>(McpJson, json)
        assertEquals(Method.Defined.CompletionComplete, request.method)

        val params = request.params
        assertEquals("file", params.argument.name)
        assertEquals("mai", params.argument.value)
        val ref = params.ref
        assertIs<ResourceTemplateReference>(ref)
        assertEquals("file:///{path}", ref.uri)
        assertNotNull(params.context)
        assertEquals(mapOf("path" to "src/main"), params.context.arguments)
        assertEquals(ProgressToken(42), params.meta?.progressToken)
    }

    @Test
    fun `should serialize CompleteResult with all fields`() {
        val result = CompleteResult(
            completion = CompleteResult.Completion(
                values = listOf("src/main/kotlin/App.kt", "src/main/kotlin/Main.kt"),
                total = 25,
                hasMore = true,
            ),
            meta = buildJsonObject {
                put("source", "cache")
            },
        )

        verifySerialization(
            result,
            McpJson,
            """
            {
              "completion": {
                "values": [
                  "src/main/kotlin/App.kt",
                  "src/main/kotlin/Main.kt"
                ],
                "total": 25,
                "hasMore": true
              },
              "_meta": {
                "source": "cache"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize CompleteResult from JSON`() {
        val json = """
            {
              "completion": {
                "values": ["README.md", "CONTRIBUTING.md"],
                "total": 2,
                "hasMore": false
              },
              "_meta": {
                "fetchedAt": "2025-01-12T15:00:58Z"
              }
            }
        """.trimIndent()

        val result = verifyDeserialization<CompleteResult>(McpJson, json)
        assertEquals(listOf("README.md", "CONTRIBUTING.md"), result.completion.values)
        assertEquals(2, result.completion.total)
        assertEquals(false, result.completion.hasMore)
        assertNotNull(result.meta)
        assertEquals(
            "2025-01-12T15:00:58Z",
            result.meta["fetchedAt"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `should allow omitting optional Total and HasMore`() {
        val result = CompleteResult(
            completion = CompleteResult.Completion(values = listOf("foo")),
            meta = null,
        )

        val json = McpJson.encodeToString(result)
        json shouldEqualJson """
            {
              "completion": {
                "values": ["foo"]
              }
            }
        """.trimIndent()

        val decoded = verifyDeserialization<CompleteResult>(McpJson, json)
        assertEquals(listOf("foo"), decoded.completion.values)
        assertNull(decoded.completion.total)
        assertNull(decoded.completion.hasMore)
        assertNull(decoded.meta)
    }

    @Test
    fun `should enforce maximum of 100 completion values`() {
        val values = (1..101).map { "entry-$it" }

        assertFailsWith<IllegalArgumentException> {
            CompleteResult.Completion(values = values)
        }
    }
}
