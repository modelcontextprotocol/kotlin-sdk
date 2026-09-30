package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

class PromptsTest {

    @Test
    fun `should serialize Prompt with arguments and meta`() {
        val prompt = Prompt(
            name = "summarize_update",
            title = "Summarize Update",
            description = "Summarize the latest repository changes.",
            arguments = listOf(
                PromptArgument(
                    name = "summaryLength",
                    description = "Approximate length of the summary",
                    required = false,
                    title = "Summary length",
                ),
            ),
            icons = listOf(
                Icon(src = "https://example.com/icon.png"),
                Icon(src = "https://example.com/icon-dark.svg", theme = Icon.Theme.Dark),
            ),
            meta = buildJsonObject { put("category", "status-report") },
        )

        verifySerialization(
            prompt,
            McpJson,
            """
            {
              "name": "summarize_update",
              "description": "Summarize the latest repository changes.",
              "arguments": [
                {
                  "name": "summaryLength",
                  "description": "Approximate length of the summary",
                  "required": false,
                  "title": "Summary length"
                }
              ],
              "title": "Summarize Update",
              "icons": [
                {
                  "src": "https://example.com/icon.png"
                },
                {
                  "src": "https://example.com/icon-dark.svg",
                  "theme": "dark"
                }
              ],
              "_meta": {
                "category": "status-report"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize PromptMessage with resource content`() {
        val json = """
            {
              "role": "user",
              "content": {
                "type": "resource",
                "resource": {
                  "uri": "file:///workspace/README.md",
                  "mimeType": "text/markdown",
                  "text": "# Project Overview"
                }
              }
            }
        """.trimIndent()

        val message = verifyDeserialization<PromptMessage>(McpJson, json)

        message.content.shouldBeInstanceOf<EmbeddedResource>().resource.shouldBeInstanceOf<TextResourceContents>()
    }

    @Test
    fun `should serialize GetPromptRequest with arguments and meta`() {
        val request = GetPromptRequest(
            GetPromptRequestParams(
                name = "generate_release_notes",
                arguments = mapOf("version" to "1.2.3"),
                meta = RequestMeta(
                    buildJsonObject { put("progressToken", "get-prompt-1") },
                ),
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "prompts/get",
              "params": {
                "name": "generate_release_notes",
                "arguments": {
                  "version": "1.2.3"
                },
                "_meta": {
                  "progressToken": "get-prompt-1"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize GetPromptResult with messages and meta`() {
        val result = GetPromptResult(
            messages = listOf(
                PromptMessage(
                    role = Role.User,
                    content = TextContent(text = "Use concise language suitable for executives."),
                ),
                PromptMessage(
                    role = Role.Assistant,
                    content = TextContent(text = "Here is the summary you requested."),
                ),
            ),
            description = "Executive summary response template.",
            meta = buildJsonObject { put("generatedAt", "2025-01-12T15:00:58Z") },
        )

        verifySerialization<ServerResult>(
            result,
            McpJson,
            """
            {
              "messages": [
                {
                  "role": "user",
                  "content": {
                    "type": "text",
                    "text": "Use concise language suitable for executives."
                  }
                },
                {
                  "role": "assistant",
                  "content": {
                    "type": "text",
                    "text": "Here is the summary you requested."
                  }
                }
              ],
              "description": "Executive summary response template.",
              "_meta": {
                "generatedAt": "2025-01-12T15:00:58Z"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ListPromptsRequest with pagination params`() {
        val request = ListPromptsRequest(
            PaginatedRequestParams(
                cursor = "cursor-123",
                meta = RequestMeta(
                    buildJsonObject { put("progressToken", "list-prompts-1") },
                ),
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "prompts/list",
              "params": {
                "cursor": "cursor-123",
                "_meta": {
                  "progressToken": "list-prompts-1"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ListPromptsResult with next cursor and meta`() {
        val result = ListPromptsResult(
            prompts = listOf(
                Prompt(name = "morning-briefing"),
                Prompt(name = "incident-response"),
            ),
            nextCursor = "cursor-2",
            meta = buildJsonObject { put("page", 1) },
        )

        verifySerialization<ServerResult>(
            result,
            McpJson,
            """
            {
              "prompts": [
                {"name": "morning-briefing"},
                {"name": "incident-response"}
              ],
              "nextCursor": "cursor-2",
              "_meta": {
                "page": 1
              }
            }
            """.trimIndent(),
        )
    }
}
