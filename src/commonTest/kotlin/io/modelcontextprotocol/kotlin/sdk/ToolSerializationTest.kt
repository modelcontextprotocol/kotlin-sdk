package io.modelcontextprotocol.kotlin.sdk

import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolSerializationTest {

    // see https://docs.anthropic.com/en/docs/build-with-claude/tool-use
    private val getWeatherToolJson = buildJsonObject {
        put("name", JsonPrimitive("get_weather"))
        put("description", JsonPrimitive("Get the current weather in a given location"))
        put("inputSchema", buildJsonObject {
            put("properties", buildJsonObject {
                put("location", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("The city and state, e.g. San Francisco, CA"))
                })
            })
            put("required", JsonArray(listOf(JsonPrimitive("location"))))
            put("type", JsonPrimitive("object"))
        })
        put("outputSchema", buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {
                put("temperature", buildJsonObject {
                    put("type", JsonPrimitive("number"))
                    put("description", JsonPrimitive("Temperature in celsius"))
                })
                put("conditions", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Weather conditions description"))
                })
                put("humidity", buildJsonObject {
                    put("type", JsonPrimitive("number"))
                    put("description", JsonPrimitive("Humidity percentage"))
                })
            })
            put(
                "required",
                JsonArray(listOf(JsonPrimitive("temperature"), JsonPrimitive("conditions"), JsonPrimitive("humidity")))
            )
        })
    }

    val getWeatherTool = Tool(
        name = "get_weather",
        description = "Get the current weather in a given location",
        annotations = null,
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                put("location", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("The city and state, e.g. San Francisco, CA"))
                })
            },
            required = listOf("location")
        ),
        outputSchema = Tool.Output(
            properties = buildJsonObject {
                put("temperature", buildJsonObject {
                    put("type", JsonPrimitive("number"))
                    put("description", JsonPrimitive("Temperature in celsius"))
                })
                put("conditions", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Weather conditions description"))
                })
                put("humidity", buildJsonObject {
                    put("type", JsonPrimitive("number"))
                    put("description", JsonPrimitive("Humidity percentage"))
                })
            },
            required = listOf("temperature", "conditions", "humidity")
        )
    )

    @Test
    fun `should serialize get_weather tool`() {
        val actual = McpJson.encodeToJsonElement(getWeatherTool)

        assertEquals(
            getWeatherToolJson,
            actual,
            "Expected $actual to be equal to $getWeatherToolJson"
        )
    }

    @Test
    fun `should deserialize get_weather tool`() {
        val tool = McpJson.decodeFromJsonElement<Tool>(getWeatherToolJson)
        assertEquals(expected = getWeatherTool, actual = tool)
    }

    @Test
    fun `should always serialize default value`() {
        val json = Json(from = McpJson) {
            encodeDefaults = false
        }
        val actual = json.encodeToJsonElement(getWeatherTool)
        assertEquals(
            getWeatherToolJson,
            actual,
            "Expected $actual to be equal to $getWeatherToolJson"
        )
    }
}
