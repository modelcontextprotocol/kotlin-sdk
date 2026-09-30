package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.buildCallToolRequest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class ToolsDslTest {
    @Test
    fun `buildCallToolRequest should build with name and arguments`() {
        val request = buildCallToolRequest {
            name = "test-tool"
            arguments {
                put("key", "value")
                put("count", 1)
            }
        }

        request.params.name shouldBe "test-tool"
        request.params.arguments shouldBe buildJsonObject {
            put("key", "value")
            put("count", 1)
        }
    }

    @Test
    fun `buildCallToolRequest should throw if name is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCallToolRequest { }
        }
    }
}
