package io.modelcontextprotocol.kotlin.sdk

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolSerializationTest {

    // see https://docs.anthropic.com/en/docs/build-with-claude/tool-use
    /* language=json */
    private val getWeatherToolJson = """
        {
          "name": "get_weather",
          "description": "Get the current weather in a given location",
          "input_schema": {
            "type": "object",
            "properties": {
              "location": {
                "type": "string",
                "description": "The city and state, e.g. San Francisco, CA"
              }
            },
            "required": ["location"]
          }
        }
    """.trimIndent()

    val getWeatherTool = Tool(
        name = "get_weather",
        description = "Get the current weather in a given location",
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                put("location", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("The city and state, e.g. San Francisco, CA"))
                })
            },
            required = listOf("location")
        )
    )

    @Test
    fun `should serialize get_weather tool`() {
        McpJson.encodeToString(getWeatherTool) shouldEqualJson getWeatherToolJson
    }

    @Test
    fun `should deserialize get_weather tool`() {
        val tool = McpJson.decodeFromString<Tool>(getWeatherToolJson)
        assertEquals(expected = getWeatherTool, actual = tool)
    }

    @Test
    fun `should always serialize default value`() {
        val json = Json(from = McpJson) {
            encodeDefaults = false
        }
        json.encodeToString(getWeatherTool) shouldEqualJson getWeatherToolJson
    }
}
