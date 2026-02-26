package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.test.Test

/**
 * Tests for RequestMeta and PaginatedRequest DSL builders.
 *
 * Verifies meta and cursor can be constructed via DSL within paginated requests,
 * covering minimal (no params), full (all field types), variants, and edge cases.
 */
@OptIn(ExperimentalMcpApi::class)
class RequestDslTest {
    @Test
    fun `request should build minimal without any params`() {
        val request = ListToolsRequest { }

        request.params.shouldBeNull()
        request.method shouldBe Method.Defined.ToolsList
    }

    @Test
    fun `request should build full with cursor and meta containing all field types`() {
        val request = ListToolsRequest {
            cursor = "next-page-eyJvZmZzZXQiOjEwMH0"
            meta {
                // ProgressToken
                progressToken("progress-abc123")

                // String fields
                put("requestId", "req-456")
                put("source", "api-gateway")

                // Number fields
                put("priority", 5)
                put("retryCount", 3L)
                put("timeout", 30000)

                // Boolean fields
                put("urgent", true)
                put("cached", false)

                // Null field
                put("optional", null)

                // Nested JsonObject
                putJsonObject("context") {
                    put("userId", "user-789")
                    put("sessionId", "sess-abc")
                    put("authenticated", true)
                    put("permissions", 7)
                }

                // JsonArray
                putJsonArray("tags") {
                    add("production")
                    add("critical")
                    add("monitored")
                }
            }
        }

        request.params shouldNotBeNull {
            cursor shouldBe "next-page-eyJvZmZzZXQiOjEwMH0"
            meta shouldNotBeNull {
                // ProgressToken
                progressToken shouldNotBeNull {
                    shouldBeInstanceOf<RequestId.StringId>()
                    value shouldBe "progress-abc123"
                }

                // String fields
                get("requestId")?.jsonPrimitive?.content shouldBe "req-456"
                get("source")?.jsonPrimitive?.content shouldBe "api-gateway"

                // Number fields
                get("priority")?.jsonPrimitive?.int shouldBe 5
                get("retryCount")?.jsonPrimitive?.long shouldBe 3L
                get("timeout")?.jsonPrimitive?.int shouldBe 30000

                // Boolean fields
                get("urgent")?.jsonPrimitive?.boolean shouldBe true
                get("cached")?.jsonPrimitive?.boolean shouldBe false

                // Null field
                get("optional") shouldBe JsonNull

                // Nested JsonObject
                get("context") shouldNotBeNull {
                    jsonObject["userId"]?.jsonPrimitive?.content shouldBe "user-789"
                    jsonObject["sessionId"]?.jsonPrimitive?.content shouldBe "sess-abc"
                    jsonObject["authenticated"]?.jsonPrimitive?.boolean shouldBe true
                    jsonObject["permissions"]?.jsonPrimitive?.int shouldBe 7
                }

                // JsonArray
                get("tags") shouldNotBeNull {
                    jsonArray.map { it.jsonPrimitive.content } shouldContainAll listOf(
                        "production",
                        "critical",
                        "monitored",
                    )
                }
            }
        }
    }

    @Test
    fun `meta progressToken should support String Int and Long types`() {
        // String progressToken
        val stringRequest = ListToolsRequest {
            meta { progressToken("token-abc") }
        }
        stringRequest.params?.meta?.progressToken shouldNotBeNull {
            shouldBeInstanceOf<RequestId.StringId>()
            value shouldBe "token-abc"
        }

        // Int progressToken
        val intRequest = ListToolsRequest {
            meta { progressToken(42) }
        }
        intRequest.params?.meta?.progressToken shouldNotBeNull {
            shouldBeInstanceOf<RequestId.NumberId>()
            value shouldBe 42L
        }

        // Long progressToken
        val longRequest = ListToolsRequest {
            meta { progressToken(999L) }
        }
        longRequest.params?.meta?.progressToken shouldNotBeNull {
            shouldBeInstanceOf<RequestId.NumberId>()
            value shouldBe 999L
        }
    }

    @Test
    fun `meta should support custom fields without progressToken`() {
        val request = ListToolsRequest {
            meta {
                put("requestId", "req-123")
                put("source", "cli")
                // No progressToken
            }
        }

        request.params shouldNotBeNull {
            meta shouldNotBeNull {
                progressToken.shouldBeNull()
                get("requestId")?.jsonPrimitive?.content shouldBe "req-123"
                get("source")?.jsonPrimitive?.content shouldBe "cli"
            }
        }
    }

    @Test
    fun `cursor should work without meta`() {
        val request = ListToolsRequest {
            cursor = "page-2-cursor"
        }

        request.params shouldNotBeNull {
            cursor shouldBe "page-2-cursor"
            meta.shouldBeNull()
        }
    }

    @Test
    fun `cursor should handle empty string`() {
        val request = ListToolsRequest {
            cursor = ""
        }

        request.params shouldNotBeNull {
            cursor shouldBe ""
        }
    }

    @Test
    fun `meta should handle special characters in keys`() {
        val request = ListToolsRequest {
            meta {
                put("key-with-dashes", "value1")
                put("key.with.dots", "value2")
                put("key_with_underscores", "value3")
                put("keyWithCamelCase", "value4")
            }
        }

        request.params?.meta shouldNotBeNull {
            get("key-with-dashes")?.jsonPrimitive?.content shouldBe "value1"
            get("key.with.dots")?.jsonPrimitive?.content shouldBe "value2"
            get("key_with_underscores")?.jsonPrimitive?.content shouldBe "value3"
            get("keyWithCamelCase")?.jsonPrimitive?.content shouldBe "value4"
        }
    }

    @Test
    fun `meta should overwrite when progressToken set multiple times`() {
        val request = ListToolsRequest {
            meta {
                progressToken("first")
                progressToken(123) // Different type
                progressToken("second") // Back to string, should be final
            }
        }

        request.params?.meta?.progressToken shouldNotBeNull {
            shouldBeInstanceOf<RequestId.StringId>()
            value shouldBe "second"
        }
    }

    @Test
    fun `meta should overwrite when custom field set multiple times`() {
        val request = ListToolsRequest {
            meta {
                put("key", "first")
                put("key", 123) // Different type
                put("key", "third") // Back to string, should be final
            }
        }

        request.params?.meta shouldNotBeNull {
            get("key")?.jsonPrimitive?.content shouldBe "third"
        }
    }

    @Test
    fun `meta should handle very long cursor strings`() {
        val longCursor = "x".repeat(1000)
        val request = ListToolsRequest {
            cursor = longCursor
        }

        request.params?.cursor shouldBe longCursor
    }
}
