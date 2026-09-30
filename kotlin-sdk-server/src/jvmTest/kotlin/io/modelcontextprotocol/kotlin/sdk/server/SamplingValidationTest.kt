package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.SamplingMessage
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolResultContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolUseContent
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test

/**
 * SEP-1577 tool_use / tool_result rules of [validateSamplingMessages]. Capability enforcement and the
 * end-to-end `Server.createMessage` flow are covered in the `integration-test` module.
 */
class SamplingValidationTest {

    private fun toolUse(id: String) = ToolUseContent(id = id, name = "t", input = JsonObject(emptyMap()))
    private fun toolResult(id: String) = ToolResultContent(toolUseId = id, content = emptyList())

    @Test
    fun `validate empty message list is valid`() {
        shouldNotThrowAny { validateSamplingMessages(emptyList()) }
    }

    @Test
    fun `validate text-only conversation is valid`() {
        shouldNotThrowAny {
            validateSamplingMessages(
                listOf(
                    SamplingMessage(Role.User, TextContent("hi")),
                    SamplingMessage(Role.Assistant, TextContent("hello")),
                ),
            )
        }
    }

    @Test
    fun `validate matched tool_use and tool_result at boundary is valid`() {
        shouldNotThrowAny {
            validateSamplingMessages(
                listOf(
                    SamplingMessage(Role.Assistant, listOf(TextContent("using tool"), toolUse("c1"))),
                    SamplingMessage(Role.User, toolResult("c1")),
                ),
            )
        }
    }

    @Test
    fun `validate orphan tool_result with no previous message fails`() {
        shouldThrow<IllegalArgumentException> {
            validateSamplingMessages(
                listOf(SamplingMessage(Role.User, toolResult("missing"))),
            )
        }.message shouldBe "tool_result blocks are not matching any tool_use from the previous message"
    }

    @Test
    fun `validate tool_result mixed with text in last message fails`() {
        shouldThrow<IllegalArgumentException> {
            validateSamplingMessages(
                listOf(
                    SamplingMessage(Role.Assistant, toolUse("c1")),
                    SamplingMessage(Role.User, listOf(toolResult("c1"), TextContent("extra"))),
                ),
            )
        }.message shouldBe "The last message must contain only tool_result content if any is present"
    }

    @Test
    fun `validate tool_result ids must match tool_use ids in previous message`() {
        shouldThrow<IllegalArgumentException> {
            validateSamplingMessages(
                listOf(
                    SamplingMessage(Role.Assistant, toolUse("c1")),
                    SamplingMessage(Role.User, toolResult("wrong_id")),
                ),
            )
        }.message shouldBe "ids of tool_result blocks and tool_use blocks from previous message do not match"
    }

    @Test
    fun `validate tool_use requires explicit tool_result in last message`() {
        shouldThrow<IllegalArgumentException> {
            validateSamplingMessages(
                listOf(
                    SamplingMessage(Role.Assistant, toolUse("c1")),
                    SamplingMessage(Role.User, TextContent("missing result")),
                ),
            )
        }.message shouldBe "tool_use blocks from previous message must be followed by matching tool_result blocks"
    }
}
