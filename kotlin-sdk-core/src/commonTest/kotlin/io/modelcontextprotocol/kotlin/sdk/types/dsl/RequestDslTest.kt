package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequest
import io.modelcontextprotocol.kotlin.sdk.types.ProgressToken
import io.modelcontextprotocol.kotlin.sdk.types.buildListPromptsRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildPingRequest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class RequestDslTest {
    @Test
    fun `meta should store every value type`() {
        val request = buildListToolsRequest {
            meta {
                progressToken("progress-1")
                put("string", "value")
                put("number", 42)
                put("boolean", true)
                put("null", null)
                putJsonObject("object") { put("key", "value") }
                putJsonArray("array") { add("item") }
            }
        }

        request.params?.meta?.json shouldBe buildJsonObject {
            put("progressToken", "progress-1")
            put("string", "value")
            put("number", 42)
            put("boolean", true)
            put("null", JsonNull)
            putJsonObject("object") { put("key", "value") }
            putJsonArray("array") { add("item") }
        }
    }

    @Test
    fun `meta progressToken should accept numeric values`() {
        buildListToolsRequest { meta { progressToken(42) } }.params?.meta?.progressToken shouldBe ProgressToken(42)
        buildListToolsRequest { meta { progressToken(999L) } }.params?.meta?.progressToken shouldBe ProgressToken(999L)
    }

    @Test
    fun `paginated builders should set cursor and omit empty params`() {
        val builders = listOf<(String?) -> PaginatedRequest>(
            { value -> buildListToolsRequest { cursor = value } },
            { value -> buildListPromptsRequest { cursor = value } },
            { value -> buildListResourcesRequest { cursor = value } },
            { value -> buildListResourceTemplatesRequest { cursor = value } },
        )

        builders.forEach { build ->
            val empty = build(null)
            withClue(empty.method) {
                empty.params shouldBe null
                build("next-page").params?.cursor shouldBe "next-page"
            }
        }
    }

    @Test
    fun `ping and roots builders should include params only when meta is set`() {
        buildPingRequest { }.params shouldBe null
        buildListRootsRequest { }.params shouldBe null
        buildPingRequest { meta { put("key", "value") } }.params?.meta?.get("key") shouldBe JsonPrimitive("value")
        buildListRootsRequest { meta { put("key", "value") } }.params?.meta?.get("key") shouldBe JsonPrimitive("value")
    }
}
