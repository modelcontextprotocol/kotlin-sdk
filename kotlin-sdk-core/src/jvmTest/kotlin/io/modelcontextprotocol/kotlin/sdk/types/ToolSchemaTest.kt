package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.schema.Description
import kotlinx.schema.generator.core.SchemaGeneratorService
import kotlinx.schema.json.JsonSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * Integration test for Tool schema functionality.
 *
 * Specific tests for individual extension functions are in:
 * - [JsonObjectAsToolSchemaTest]
 * - [JsonSchemaAsToolSchemaTest]
 * - [FunctionCallingSchemaAsToolSchemaTest]
 */
class ToolSchemaTest {

    private val schemaGenerator = requireNotNull(
        SchemaGeneratorService.getGenerator(KClass::class, JsonSchema::class),
    )

    data class SearchRequest(
        @property:Description("Search query")
        val query: String,
    )

    data class SearchResult(val results: List<String>)

    @Test
    fun `should serialize Tool with annotations and schemas`() {
        val searchRequestSchema = schemaGenerator.generateSchema(SearchRequest::class)
        val searchResultSchema = schemaGenerator.generateSchema(SearchResult::class)

        val inputSchema = searchRequestSchema.asToolSchema()

        val outputSchema = searchResultSchema.asToolSchema()

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
                    "type": "array",
                    "items": {
                      "type": "string"
                    }
                  }
                },
                "required": ["results"]
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
}
