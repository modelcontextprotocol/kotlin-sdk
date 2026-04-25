package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.types.SamplingMessage
import io.modelcontextprotocol.kotlin.sdk.types.ToolResultContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolUseContent

/**
 * Validates tool_use / tool_result rules on the last two messages of [messages].
 *
 * Only the boundary between the previous message and the final message matters, because
 * earlier turns were already validated when they were appended; the sole freshly-built
 * portion of a sampling request is its tail.
 *
 * Rules enforced:
 *
 * 1. If the last message contains any `tool_result` block, it MUST contain only
 *    `tool_result` blocks (no mixing with text/image/audio/tool_use).
 * 2. If the last message contains any `tool_result`, the previous message MUST contain
 *    matching `tool_use` blocks.
 * 3. If the previous message contains `tool_use` blocks, the last message's
 *    `tool_result` ids MUST form exactly the same set.
 *
 * On the first violation throws [IllegalArgumentException]. No-op when there are fewer
 * than two messages or no tool_use / tool_result blocks are involved.
 */
internal fun validateSamplingMessages(messages: List<SamplingMessage>) {
    if (messages.isEmpty()) return

    val last = messages.last().content
    val hasToolResult = last.any { it is ToolResultContent }

    val previous = messages.getOrNull(messages.size - 2)?.content.orEmpty()
    val hasPreviousToolUse = previous.any { it is ToolUseContent }

    if (hasToolResult) {
        require(last.all { it is ToolResultContent }) {
            "The last message must contain only tool_result content if any is present"
        }
        require(hasPreviousToolUse) {
            "tool_result blocks are not matching any tool_use from the previous message"
        }
    }

    if (hasPreviousToolUse) {
        val toolUseIds = previous.filterIsInstance<ToolUseContent>().map { it.id }.toSet()
        val toolResultIds = last.filterIsInstance<ToolResultContent>().map { it.toolUseId }.toSet()
        require(toolUseIds == toolResultIds) {
            "ids of tool_result blocks and tool_use blocks from previous message do not match"
        }
    }
}
