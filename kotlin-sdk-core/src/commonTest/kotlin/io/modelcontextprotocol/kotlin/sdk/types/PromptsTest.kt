package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PromptsTest {

    @Test
    fun `should serialize PromptArgument with minimal fields`() {
        val argument = PromptArgument(name = "repository")

        verifySerialization(
            argument,
            McpJson,
            """
            {
              "name": "repository"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize PromptArgument with all fields`() {
        val argument = PromptArgument(
            name = "language",
            description = "Programming language to use",
            required = true,
            title = "Language",
        )

        verifySerialization(
            argument,
            McpJson,
            """
            {
              "name": "language",
              "description": "Programming language to use",
              "required": true,
              "title": "Language"
            }
            """.trimIndent(),
        )
    }

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
    fun `should deserialize Prompt from JSON`() {
        val json = """
            {
              "name": "code_review",
              "description": "Generate a succinct code review.",
              "title": "Code Review",
              "arguments": [
                {
                  "name": "filePath",
                  "description": "Path to the file to review",
                  "required": true
                },
                {
                  "name": "severity",
                  "title": "Issue severity"
                }
              ],
              "icons": [
                {"src": "https://example.com/review.png"}
              ],
              "_meta": {
                "category": "quality"
              }
            }
        """.trimIndent()

        val prompt = verifyDeserialization<Prompt>(McpJson, json)

        assertEquals("code_review", prompt.name)
        assertEquals("Generate a succinct code review.", prompt.description)
        assertEquals("Code Review", prompt.title)
        val arguments = prompt.arguments
        assertNotNull(arguments)
        assertEquals(2, arguments.size)
        assertEquals("filePath", arguments[0].name)
        assertEquals(true, arguments[0].required)
        assertEquals("severity", arguments[1].name)
        assertEquals("Issue severity", arguments[1].title)
        val meta = prompt.meta
        assertNotNull(meta)
        assertEquals("quality", meta["category"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should serialize PromptMessage with text content`() {
        val message = PromptMessage(
            role = Role.Assistant,
            content = TextContent(text = "Provide a concise summary of the changes."),
        )

        verifySerialization(
            message,
            McpJson,
            """
            {
              "role": "assistant",
              "content": {
                "type": "text",
                "text": "Provide a concise summary of the changes."
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

        assertEquals(Role.User, message.role)
        val content = message.content
        assertIs<EmbeddedResource>(content)
        val resource = assertIs<TextResourceContents>(content.resource)
        assertEquals("file:///workspace/README.md", resource.uri)
        assertEquals("# Project Overview", resource.text)
    }

    @Test
    fun `should serialize and deserialize PromptReference`() {
        val reference = PromptReference(name = "daily-summary", title = "Daily Summary")

        val json = McpJson.encodeToString(reference)
        json shouldEqualJson """
            {
              "type": "ref/prompt",
              "name": "daily-summary",
              "title": "Daily Summary"
            }
        """.trimIndent()

        val decoded = verifyDeserialization<Reference>(McpJson, json)
        val promptReference = assertIs<PromptReference>(decoded)
        assertEquals("daily-summary", promptReference.name)
        assertEquals("Daily Summary", promptReference.title)
        assertEquals(ReferenceType.Prompt, promptReference.type)
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
    fun `should deserialize GetPromptRequest from JSON`() {
        val json = """
            {
              "method": "prompts/get",
              "params": {
                "name": "draft-response",
                "arguments": {
                  "tone": "confident",
                  "audience": "executive"
                },
                "_meta": {
                  "progressToken": 88
                }
              }
            }
        """.trimIndent()

        val request = verifyDeserialization<GetPromptRequest>(McpJson, json)

        assertEquals(Method.Defined.PromptsGet, request.method)
        val params = request.params
        assertEquals("draft-response", params.name)
        val args = params.arguments
        assertNotNull(args)
        assertEquals("confident", args["tone"])
        assertEquals("executive", args["audience"])
        assertEquals(ProgressToken(88), params.meta?.progressToken)
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

        verifySerialization(
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
    fun `should deserialize GetPromptResult from JSON`() {
        val json = """
            {
              "messages": [
                {
                  "role": "user",
                  "content": {
                    "type": "text",
                    "text": "Summarize today's standup."
                  }
                }
              ],
              "description": "Collect information from the team."
            }
        """.trimIndent()

        val result = verifyDeserialization<GetPromptResult>(McpJson, json)

        val messages = result.messages
        assertEquals(1, messages.size)
        assertEquals(Role.User, messages.first().role)
        val textContent = assertIs<TextContent>(messages.first().content)
        assertEquals("Summarize today's standup.", textContent.text)
        assertEquals("Collect information from the team.", result.description)
        assertNull(result.meta)
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
    fun `should serialize ListPromptsRequest without params`() {
        val request = ListPromptsRequest()

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "prompts/list"
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

        verifySerialization(
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

    @Test
    fun `should deserialize ListPromptsResult from JSON`() {
        val json = """
            {
              "prompts": [
                {
                  "name": "weekly-update",
                  "description": "Summarize weekly activities.",
                  "arguments": [
                    {
                      "name": "team",
                      "title": "Team name"
                    }
                  ]
                }
              ],
              "nextCursor": "cursor-next",
              "_meta": {
                "page": 2,
                "latencyMs": 12.5
              }
            }
        """.trimIndent()

        val result = verifyDeserialization<ListPromptsResult>(McpJson, json)

        val prompts = result.prompts
        assertEquals(1, prompts.size)
        val prompt = prompts.first()
        assertEquals("weekly-update", prompt.name)
        assertEquals("Summarize weekly activities.", prompt.description)
        val args = prompt.arguments
        assertNotNull(args)
        assertEquals("team", args.first().name)
        assertEquals("Team name", args.first().title)
        assertEquals("cursor-next", result.nextCursor)
        val meta = result.meta
        assertNotNull(meta)
        assertEquals(2, meta["page"]?.jsonPrimitive?.int)
        assertEquals(12.5, meta["latencyMs"]?.jsonPrimitive?.double)
    }
}
