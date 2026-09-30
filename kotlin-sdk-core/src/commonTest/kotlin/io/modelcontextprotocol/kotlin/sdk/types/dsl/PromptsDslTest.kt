package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.buildGetPromptRequest
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
    fun `buildGetPromptRequest should throw if name is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildGetPromptRequest { }
        }
    }
}
