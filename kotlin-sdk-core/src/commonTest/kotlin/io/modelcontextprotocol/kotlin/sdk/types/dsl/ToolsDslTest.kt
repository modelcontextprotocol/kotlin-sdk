package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildCallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
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
        request.params.arguments shouldNotBeNull {
            get("key")?.jsonPrimitive?.content shouldBe "value"
            get("count")?.jsonPrimitive?.int shouldBe 1
        }
    }

    @Test
    fun `buildListToolsRequest should build with cursor`() {
        val request = buildListToolsRequest {
            cursor = "tool-cursor"
        }

        request.params shouldNotBeNull {
            cursor shouldBe "tool-cursor"
        }
    }

    @Test
    fun `buildCallToolRequest should support direct arguments assignment`() {
        val args = buildJsonObject { put("key", "value") }
        val request = buildCallToolRequest {
            name = "test-tool"
            arguments(args)
        }
        request.params.arguments shouldBe args
    }

    @Test
    fun `buildCallToolRequest should throw if name is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCallToolRequest { }
        }
    }

    @Test
    fun `buildListToolsRequest should build without params if empty`() {
        val request = buildListToolsRequest { }
        request.params shouldBe null
    }

    @Test
    fun `CallToolRequest should build with name and arguments`() {
        val request = CallToolRequest {
            name = "test-tool"
            arguments {
                put("key", "value")
                put("count", 1)
            }
        }

        request.params.name shouldBe "test-tool"
        request.params.arguments shouldNotBeNull {
            get("key")?.jsonPrimitive?.content shouldBe "value"
            get("count")?.jsonPrimitive?.int shouldBe 1
        }
    }

    @Test
    fun `ListToolsRequest should build with cursor`() {
        val request = ListToolsRequest {
            cursor = "tool-cursor"
        }

        request.params shouldNotBeNull {
            cursor shouldBe "tool-cursor"
        }
    }

    @Test
    fun `CallToolRequest should support direct arguments assignment`() {
        val args = buildJsonObject { put("key", "value") }
        val request = CallToolRequest {
            name = "test-tool"
            arguments(args)
        }
        request.params.arguments shouldBe args
    }

    @Test
    fun `CallToolRequest should throw if name is missing`() {
        shouldThrow<IllegalArgumentException> {
            CallToolRequest { }
        }
    }

    @Test
    fun `ListToolsRequest should build without params if empty`() {
        val request = ListToolsRequest { }
        request.params shouldBe null
    }
}
