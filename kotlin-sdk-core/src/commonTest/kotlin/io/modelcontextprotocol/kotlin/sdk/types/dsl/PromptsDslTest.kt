package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListPromptsRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildGetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildListPromptsRequest
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class PromptsDslTest {

    @Test
    fun `buildGetPromptRequest should create request with name and arguments`() {
        val request = buildGetPromptRequest {
            name = "test-prompt"
            arguments = mapOf("key" to "value")
        }

        request.params.name shouldBe "test-prompt"
        request.params.arguments shouldBe mapOf("key" to "value")
    }

    @Test
    fun `buildListPromptsRequest should create request with cursor`() {
        val request = buildListPromptsRequest {
            cursor = "next-page"
        }

        request.params shouldNotBeNull {
            cursor shouldBe "next-page"
        }
    }

    @Test
    fun `buildGetPromptRequest should throw if name is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildGetPromptRequest { }
        }
    }

    @Test
    fun `buildListPromptsRequest should create request without params if empty`() {
        val request = buildListPromptsRequest { }
        request.params shouldBe null
    }

    @Test
    fun `GetPromptRequest should create request with name and arguments`() {
        val request = GetPromptRequest {
            name = "test-prompt"
            arguments = mapOf("key" to "value")
        }

        request.params.name shouldBe "test-prompt"
        request.params.arguments shouldBe mapOf("key" to "value")
    }

    @Test
    fun `ListPromptsRequest should create request with cursor`() {
        val request = ListPromptsRequest {
            cursor = "next-page"
        }

        request.params shouldNotBeNull {
            cursor shouldBe "next-page"
        }
    }

    @Test
    fun `GetPromptRequest should throw if name is missing`() {
        shouldThrow<IllegalArgumentException> {
            GetPromptRequest { }
        }
    }

    @Test
    fun `ListPromptsRequest should create request without params if empty`() {
        val request = ListPromptsRequest { }
        request.params shouldBe null
    }
}
