package io.modelcontextprotocol.kotlin.sdk

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test

class ToolSerializationTest {

    @Test
    fun `should serialize GetWeather tool`() {
        val tool = Tool(
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

        // see https://docs.anthropic.com/en/docs/build-with-claude/tool-use
        McpJson.encodeToString(tool) shouldEqualJson /* language=json */ """
            {
              "name": "get_weather",
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
              }
            }
        """
    }

}
