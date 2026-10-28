package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.throwables.shouldThrow
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
    fun `should reject unknown reference type`() {
        shouldThrow<SerializationException> {
            McpJson.decodeFromString<Reference>("""{"type": "ref/unknown", "name": "x"}""")
        }
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

        verifySerialization<ServerResult>(
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
    fun `should enforce maximum of 100 completion values`() {
        val values = (1..101).map { "entry-$it" }

        assertFailsWith<IllegalArgumentException> {
            CompleteResult.Completion(values = values)
        }
    }
}
