package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ToolsTest {

    @Test
    fun `should serialize Tool with minimal fields`() {
        val tool = Tool(
            name = "search",
            inputSchema = ToolSchema(),
        )

        val json = McpJson.encodeToString(tool)

        json shouldEqualJson """
            {
              "name": "search",
              "inputSchema": {
                "type": "object"
              }
            }
        """.trimIndent()
    }

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

        val json = McpJson.encodeToString(tool)

        json shouldEqualJson """
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
        """.trimIndent()
    }

    @Test
    fun `should deserialize Tool from JSON`() {
        val json = """
            {
              "name": "translate",
              "inputSchema": {
                "type": "object",
                "properties": {
                  "text": {"type": "string"},
                  "targetLanguage": {"type": "string"}
                },
                "required": ["text", "targetLanguage"]
              },
              "annotations": {
                "title": "Translate Text",
                "readOnlyHint": true
              },
              "_meta": {
                "category": "language"
              }
            }
        """.trimIndent()

        val tool = McpJson.decodeFromString<Tool>(json)

        assertEquals("translate", tool.name)
        assertEquals("Translate Text", tool.annotations?.title)
        assertEquals(true, tool.annotations?.readOnlyHint)
        val schema = tool.inputSchema
        val properties = schema.properties
        assertNotNull(properties)
        assertNotNull(properties["text"])
        assertEquals(listOf("text", "targetLanguage"), schema.required)
        assertEquals("language", tool.meta?.get("category")?.jsonPrimitive?.content)
    }

    @Test
    fun `should serialize ToolSchema with defs`() {
        val tool = toolWithDefs()

        val json = McpJson.encodeToString(tool)

        json shouldEqualJson toolWithDefsJson()
    }

    @Test
    fun `should deserialize ToolSchema with defs`() {
        val json = $$"""
            {
              "name": "create-page",
              "inputSchema": {
                "type": "object",
                "$defs": {
                  "parentRequest": {
                    "type": "object",
                    "properties": {
                      "page_id": {
                        "type": "string"
                      }
                    }
                  }
                },
                "properties": {
                  "parent": {
                    "$ref": "#/$defs/parentRequest"
                  }
                },
                "required": ["parent"]
              }
            }
        """.trimIndent()

        val tool = McpJson.decodeFromString<Tool>(json)

        val schema = tool.inputSchema
        val defs = schema.defs
        assertNotNull(defs)
        val parentRequest = defs["parentRequest"]?.jsonObject
        assertNotNull(parentRequest)
        assertEquals("object", parentRequest["type"]?.jsonPrimitive?.content)
        assertEquals(
            $$"#/$defs/parentRequest",
            schema.properties?.get("parent")?.jsonObject?.get($$"$ref")?.jsonPrimitive?.content,
        )
        assertEquals(listOf("parent"), schema.required)
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

        val json = McpJson.encodeToString(request)

        json shouldEqualJson """
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
        """.trimIndent()
    }

    @Test
    fun `should deserialize CallToolRequest`() {
        val json = """
            {
              "method": "tools/call",
              "params": {
                "name": "analyze-code",
                "arguments": {
                  "path": "src/main.kt"
                },
                "_meta": {
                  "progressToken": 77
                }
              }
            }
        """.trimIndent()

        val request = McpJson.decodeFromString<CallToolRequest>(json)

        assertEquals(Method.Defined.ToolsCall, request.method)
        val params = request.params
        assertEquals("analyze-code", params.name)
        assertEquals("src/main.kt", params.arguments?.get("path")?.jsonPrimitive?.content)
        assertEquals(ProgressToken(77), params.meta?.progressToken)
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

        val json = McpJson.encodeToString(result)

        json shouldEqualJson """
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
        """.trimIndent()
    }

    @Test
    fun `should deserialize CallToolResult`() {
        val json = """
            {
              "content": [
                {
                  "type": "text",
                  "text": "Unable to reach server."
                }
              ],
              "isError": true,
              "structuredContent": {
                "errorCode": "NETWORK",
                "retryable": true
              },
              "_meta": {
                "requestId": "req-9"
              }
            }
        """.trimIndent()

        val result = McpJson.decodeFromString<CallToolResult>(json)

        assertEquals(true, result.isError)
        val text = assertIs<TextContent>(result.content.first())
        assertEquals("Unable to reach server.", text.text)
        val structured = result.structuredContent
        assertNotNull(structured)
        assertEquals("NETWORK", structured["errorCode"]?.jsonPrimitive?.content)
        assertEquals("req-9", result.meta?.get("requestId")?.jsonPrimitive?.content)
    }

    @Test
    fun `should serialize ListToolsRequest with cursor`() {
        val request = ListToolsRequest(
            PaginatedRequestParams(
                cursor = "cursor-1",
                meta = RequestMeta(buildJsonObject { put("progressToken", "tools-list-1") }),
            ),
        )

        val json = McpJson.encodeToString(request)

        json shouldEqualJson """
            {
              "method": "tools/list",
              "params": {
                "cursor": "cursor-1",
                "_meta": {
                  "progressToken": "tools-list-1"
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize ListToolsRequest without params`() {
        val request = ListToolsRequest()

        val json = McpJson.encodeToString(request)

        json shouldEqualJson """
            {
              "method": "tools/list"
            }
        """.trimIndent()
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

        val json = McpJson.encodeToString(result)

        json shouldEqualJson """
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
        """.trimIndent()
    }

    @Test
    fun `should deserialize ListToolsResult`() {
        val json = """
            {
              "tools": [
                {
                  "name": "search",
                  "inputSchema": {
                    "type": "object"
                  },
                  "description": "Search the workspace"
                }
              ],
              "nextCursor": "cursor-next",
              "_meta": {
                "page": 3
              }
            }
        """.trimIndent()

        val result = McpJson.decodeFromString<ListToolsResult>(json)

        assertEquals("cursor-next", result.nextCursor)
        val tools = result.tools
        assertEquals(1, tools.size)
        val tool = tools.first()
        assertEquals("search", tool.name)
        assertEquals("Search the workspace", tool.description)
        assertEquals("object", tool.inputSchema.type)
        assertEquals(3, result.meta?.get("page")?.jsonPrimitive?.int)
    }

    @Test
    fun `should build success CallToolResult with text content`() {
        val meta = buildJsonObject { put("source", "toolkit") }

        val result = CallToolResult.success("Operation complete", meta)

        assertEquals(false, result.isError)
        val text = assertIs<TextContent>(result.content.single())
        assertEquals("Operation complete", text.text)
        assertEquals("toolkit", result.meta?.get("source")?.jsonPrimitive?.content)
        assertEquals(null, result.structuredContent)
    }

    @Test
    fun `should build error CallToolResult with text content`() {
        val meta = buildJsonObject { put("code", "ERR42") }

        val result = CallToolResult.error("Failed to connect", meta)

        assertEquals(true, result.isError)
        val text = assertIs<TextContent>(result.content.single())
        assertEquals("Failed to connect", text.text)
        assertEquals("ERR42", result.meta?.get("code")?.jsonPrimitive?.content)
        assertEquals(null, result.structuredContent)
    }

    @Test
    fun `should serialize tool schema type even when defaults disabled`() {
        val tool = weatherTool()

        val customJson = Json(from = McpJson) {
            encodeDefaults = false
        }
        val encoded = customJson.encodeToString(Tool.serializer(), tool)
        val element = Json.parseToJsonElement(encoded).jsonObject

        val inputSchema = element["inputSchema"]?.jsonObject
        val outputSchema = element["outputSchema"]?.jsonObject

        assertNotNull(inputSchema)
        assertEquals("object", inputSchema["type"]?.jsonPrimitive?.content)
        assertNotNull(outputSchema)
        assertEquals("object", outputSchema["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should deserialize complex tool definition`() {
        val expected = weatherTool()
        val actual = McpJson.decodeFromString<Tool>(weatherToolJson())

        assertEquals(expected, actual)
    }

    private fun weatherTool(): Tool = Tool(
        name = "get_weather",
        title = "Get weather",
        description = "Get the current weather in a given location",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put(
                    "location",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "The city and state, e.g. San Francisco, CA")
                    },
                )
            },
            required = listOf("location"),
        ),
        outputSchema = ToolSchema(
            properties = buildJsonObject {
                put(
                    "temperature",
                    buildJsonObject {
                        put("type", "number")
                        put("description", "Temperature in celsius")
                    },
                )
                put(
                    "conditions",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Weather conditions description")
                    },
                )
                put(
                    "humidity",
                    buildJsonObject {
                        put("type", "number")
                        put("description", "Humidity percentage")
                    },
                )
            },
            required = listOf("temperature", "conditions", "humidity"),
        ),
        meta = buildJsonObject { put("_for_test_only", true) },
    )

    private fun weatherToolJson(): String = """
        {
          "name": "get_weather",
          "title": "Get weather",
          "description": "Get the current weather in a given location",
          "inputSchema": {
            "type": "object",
            "properties": {
              "location": {
                "type": "string",
                "description": "The city and state, e.g. San Francisco, CA"
              }
            },
            "required": ["location"]
          },
          "outputSchema": {
            "type": "object",
            "properties": {
              "temperature": {
                "type": "number",
                "description": "Temperature in celsius"
              },
              "conditions": {
                "type": "string",
                "description": "Weather conditions description"
              },
              "humidity": {
                "type": "number",
                "description": "Humidity percentage"
              }
            },
            "required": ["temperature", "conditions", "humidity"]
          },
          "_meta": {
            "_for_test_only": true
          }
        }
    """.trimIndent()

    private fun toolWithDefs(): Tool = Tool(
        name = "create-page",
        inputSchema = toolSchemaWithDefs(),
    )

    private fun toolSchemaWithDefs(): ToolSchema = ToolSchema(
        properties = buildJsonObject {
            put(
                "parent",
                buildJsonObject {
                    put($$"$ref", $$"#/$defs/parentRequest")
                },
            )
        },
        required = listOf("parent"),
        defs = buildJsonObject {
            put(
                "parentRequest",
                buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "page_id",
                                buildJsonObject {
                                    put("type", "string")
                                },
                            )
                        },
                    )
                },
            )
        },
    )

    private fun toolWithDefsJson(): String = $$"""
        {
          "name": "create-page",
          "inputSchema": {
            "type": "object",
            "$defs": {
              "parentRequest": {
                "type": "object",
                "properties": {
                  "page_id": {
                    "type": "string"
                  }
                }
              }
            },
            "properties": {
              "parent": {
                "$ref": "#/$defs/parentRequest"
              }
            },
            "required": ["parent"]
          }
        }
    """.trimIndent()
}
