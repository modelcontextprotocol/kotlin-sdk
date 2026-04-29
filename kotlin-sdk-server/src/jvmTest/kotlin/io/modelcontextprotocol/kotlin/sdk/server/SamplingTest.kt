package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.SamplingMessage
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolResultContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolUseContent
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Unit tests for [validateSamplingMessages]: pure-function checks of the SEP-1577
 * last-two-messages tool_use / tool_result rules.
 *
 * End-to-end and capability-enforcement tests for `Server.createMessage` live in the
 * `integration-test` module under the same package.
 */
class SamplingTest {

    private fun toolUse(id: String) = ToolUseContent(id = id, name = "t", input = JsonObject(emptyMap()))
    private fun toolResult(id: String) = ToolResultContent(toolUseId = id, content = emptyList())

    @Test
    fun `validate empty message list is valid`() {
        assertDoesNotThrow { validateSamplingMessages(emptyList()) }
    }

    @Test
    fun `validate text-only conversation is valid`() {
        assertDoesNotThrow {
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
        assertDoesNotThrow {
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
        assertFailsWith<IllegalArgumentException> {
            validateSamplingMessages(
                listOf(SamplingMessage(Role.User, toolResult("missing"))),
            )
        }
    }

    @Test
    fun `validate tool_result mixed with text in last message fails`() {
        assertFailsWith<IllegalArgumentException> {
            validateSamplingMessages(
                listOf(
                    SamplingMessage(Role.Assistant, toolUse("c1")),
                    SamplingMessage(Role.User, listOf(toolResult("c1"), TextContent("extra"))),
                ),
            )
        }
    }

    @Test
    fun `validate tool_result ids must match tool_use ids in previous message`() {
        assertFailsWith<IllegalArgumentException> {
            validateSamplingMessages(
                listOf(
                    SamplingMessage(Role.Assistant, toolUse("c1")),
                    SamplingMessage(Role.User, toolResult("wrong_id")),
                ),
            )
        }
    }
}
