package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolsTest {

    @Test
    fun `should serialize Tool with annotations and schemas`() {
        val inputSchema = ToolSchema(
            properties = buildJsonObject {
                put(
                    "query",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Search query")
                    },
                )
            },
            required = listOf("query"),
        )
        val outputSchema = ToolSchema(
            properties = buildJsonObject {
                put(
                    "results",
                    buildJsonObject {
                        put("type", "array")
                    },
                )
            },
        )
        val tool = Tool(
            name = "web-search",
            inputSchema = inputSchema,
            description = "Search the web for information",
            outputSchema = outputSchema,
            title = "Web Search",
            annotations = ToolAnnotations(
                title = "Web Search (Preferred)",
                readOnlyHint = true,
                destructiveHint = false,
                idempotentHint = true,
                openWorldHint = true,
            ),
            icons = listOf(Icon(src = "https://example.com/search.png")),
            meta = buildJsonObject { put("category", "search") },
        )

        verifySerialization(
            tool,
            McpJson,
            """
            {
              "name": "web-search",
              "inputSchema": {
                "type": "object",
                "properties": {
                  "query": {
                    "type": "string",
                    "description": "Search query"
                  }
                },
                "required": ["query"]
              },
              "description": "Search the web for information",
              "outputSchema": {
                "type": "object",
                "properties": {
                  "results": {
                    "type": "array"
                  }
                }
              },
              "title": "Web Search",
              "annotations": {
                "title": "Web Search (Preferred)",
                "readOnlyHint": true,
                "destructiveHint": false,
                "idempotentHint": true,
                "openWorldHint": true
              },
              "icons": [
                {"src": "https://example.com/search.png"}
              ],
              "_meta": {
                "category": "search"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ToolSchema with schema dialect and defs`() {
        val schema = ToolSchema(
            schema = "https://json-schema.org/draft/2020-12/schema",
            properties = buildJsonObject {
                put("parent", buildJsonObject { put($$"$ref", $$"#/$defs/parentRequest") })
            },
            required = listOf("parent"),
            defs = buildJsonObject {
                put("parentRequest", buildJsonObject { put("type", "object") })
            },
        )

        verifySerialization(
            schema,
            McpJson,
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "parent": {"$ref": "#/$defs/parentRequest"}
              },
              "required": ["parent"],
              "$defs": {
                "parentRequest": {"type": "object"}
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize CallToolRequest with arguments`() {
        val request = CallToolRequest(
            CallToolRequestParams(
                name = "web-search",
                arguments = buildJsonObject { put("query", "MCP protocol") },
                meta = RequestMeta(
                    buildJsonObject { put("progressToken", "call-1") },
                ),
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "tools/call",
              "params": {
                "name": "web-search",
                "arguments": {
                  "query": "MCP protocol"
                },
                "_meta": {
                  "progressToken": "call-1"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize CallToolRequest with task augmentation`() {
        val request = CallToolRequest(
            CallToolRequestParams(
                name = "long-running",
                arguments = buildJsonObject { put("query", "MCP protocol") },
                task = TaskMetadata(ttl = 60_000L),
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "tools/call",
              "params": {
                "name": "long-running",
                "arguments": {
                  "query": "MCP protocol"
                },
                "task": {
                  "ttl": 60000
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(TaskMetadata(ttl = 60_000L), request.task)
    }

    @Test
    fun `should serialize CallToolResult with structured content`() {
        val result = CallToolResult(
            content = listOf(
                TextContent(text = "Found 3 relevant documents."),
            ),
            isError = false,
            structuredContent = buildJsonObject {
                put("count", 3)
                put("items", buildJsonObject { put("first", "doc.md") })
            },
            meta = buildJsonObject { put("elapsedMs", 1200) },
        )

        verifySerialization<ServerResult>(
            result,
            McpJson,
            """
            {
              "content": [
                {
                  "type": "text",
                  "text": "Found 3 relevant documents."
                }
              ],
              "isError": false,
              "structuredContent": {
                "count": 3,
                "items": {
                  "first": "doc.md"
                }
              },
              "_meta": {
                "elapsedMs": 1200
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ListToolsRequest without params`() {
        val request = ListToolsRequest()

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "tools/list"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ListToolsResult`() {
        val result = ListToolsResult(
            tools = listOf(
                Tool(name = "search", inputSchema = ToolSchema()),
                Tool(name = "summarize", inputSchema = ToolSchema()),
            ),
            nextCursor = "cursor-2",
            meta = buildJsonObject { put("page", 1) },
        )

        verifySerialization<ServerResult>(
            result,
            McpJson,
            """
            {
              "tools": [
                {
                  "name": "search",
                  "inputSchema": {
                    "type": "object"
                  }
                },
                {
                  "name": "summarize",
                  "inputSchema": {
                    "type": "object"
                  }
                }
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
    fun `should build success and error CallToolResult with text content`() {
        val meta = buildJsonObject { put("source", "toolkit") }

        CallToolResult.success("Operation complete", meta) shouldBe
            CallToolResult(content = listOf(TextContent("Operation complete")), isError = false, meta = meta)
        CallToolResult.error("Failed to connect", meta) shouldBe
            CallToolResult(content = listOf(TextContent("Failed to connect")), isError = true, meta = meta)
    }

    @Test
    fun `should serialize all TaskSupport values`() {
        verifySerialization(TaskSupport.Forbidden, McpJson, "\"forbidden\"")
        verifySerialization(TaskSupport.Optional, McpJson, "\"optional\"")
        verifySerialization(TaskSupport.Required, McpJson, "\"required\"")
    }

    @Test
    fun `should serialize Tool with execution`() {
        val tool = Tool(
            name = "long-running-task",
            inputSchema = ToolSchema(),
            description = "A tool that supports task-augmented execution",
            execution = ToolExecution(taskSupport = TaskSupport.Required),
        )

        verifySerialization(
            tool,
            McpJson,
            """
            {
              "name": "long-running-task",
              "inputSchema": {
                "type": "object"
              },
              "description": "A tool that supports task-augmented execution",
              "execution": {
                "taskSupport": "required"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize tool schema type even when defaults disabled`() {
        val tool = Tool(name = "t", inputSchema = ToolSchema(), outputSchema = ToolSchema())
        val json = Json(from = McpJson) { encodeDefaults = false }

        json.encodeToString(Tool.serializer(), tool) shouldEqualJson
            """{"name": "t", "inputSchema": {"type": "object"}, "outputSchema": {"type": "object"}}"""
    }
}
