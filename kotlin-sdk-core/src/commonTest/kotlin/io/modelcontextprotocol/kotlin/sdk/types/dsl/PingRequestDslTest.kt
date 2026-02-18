package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class PingRequestDslTest {
    @Test
    fun `buildPingRequest should create request with meta containing all field types`() {
        val request = PingRequest {
            meta {
                progressToken("token-123")
                put("string", "value")
                put("number", 42)
                put("boolean", true)
                put("null", null)
                putJsonObject("obj") {
                    put("key", "val")
                }
                putJsonArray("arr") {
                    add("item")
                }
            }
        }

        request.params.shouldNotBeNull {
            meta.shouldNotBeNull {
                json["progressToken"]?.jsonPrimitive?.content shouldBe "token-123"
                json["string"]?.jsonPrimitive?.content shouldBe "value"
                json["number"]?.jsonPrimitive?.int shouldBe 42
                json["boolean"]?.jsonPrimitive?.boolean shouldBe true
                json["null"] shouldBe kotlinx.serialization.json.JsonNull
                json["obj"]?.shouldNotBeNull()
                json["arr"]?.shouldNotBeNull()
            }
        }
    }

    @Test
    fun `RequestMeta DSL should support numeric progress tokens`() {
        val requestInt = PingRequest {
            meta { progressToken(123) }
        }
        requestInt.params?.meta?.json
            ?.get("progressToken")
            ?.jsonPrimitive?.int shouldBe 123

        val requestLong = PingRequest {
            meta { progressToken(456L) }
        }
        requestLong.params?.meta?.json
            ?.get("progressToken")
            ?.jsonPrimitive?.int shouldBe 456
    }

    @Test
    fun `buildPingRequest should create request without params if meta is empty`() {
        val request = PingRequest { }
        request.params shouldBe null
    }
}
