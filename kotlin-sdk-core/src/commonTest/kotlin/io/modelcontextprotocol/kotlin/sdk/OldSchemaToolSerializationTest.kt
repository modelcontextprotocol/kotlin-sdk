@file:Suppress("DEPRECATION")

package io.modelcontextprotocol.kotlin.sdk

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class OldSchemaToolSerializationTest {

    // see https://docs.anthropic.com/en/docs/build-with-claude/tool-use
    /* language=json */
    private val getWeatherToolJson = """
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

    val getWeatherTool = Tool(
        name = "get_weather",
        title = "Get weather",
        description = "Get the current weather in a given location",
        annotations = null,
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                put(
                    "location",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("The city and state, e.g. San Francisco, CA"))
                    },
                )
            },
            required = listOf("location"),
        ),
        outputSchema = Tool.Output(
            properties = buildJsonObject {
                put(
                    "temperature",
                    buildJsonObject {
                        put("type", JsonPrimitive("number"))
                        put("description", JsonPrimitive("Temperature in celsius"))
                    },
                )
                put(
                    "conditions",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Weather conditions description"))
                    },
                )
                put(
                    "humidity",
                    buildJsonObject {
                        put("type", JsonPrimitive("number"))
                        put("description", JsonPrimitive("Humidity percentage"))
                    },
                )
            },
            required = listOf("temperature", "conditions", "humidity"),
        ),
        _meta = buildJsonObject {
            put("_for_test_only", JsonPrimitive(true))
        },
    )

    //region Serialize

    @Test
    fun `should serialize get_weather tool`() {
        McpJson.encodeToString(getWeatherTool) shouldEqualJson getWeatherToolJson
    }

    @Test
    fun `should always serialize default value`() {
        val json = Json(from = McpJson) {
            encodeDefaults = false
        }
        json.encodeToString(getWeatherTool) shouldEqualJson getWeatherToolJson
    }

    @Test
    fun `should serialize get_weather tool without optional properties`() {
        val weatherTool = createWeatherTool(name = "get_weather")
        val expectedJson = createWeatherToolJson(name = "get_weather")
        val actualJson = McpJson.encodeToString(weatherTool)

        actualJson shouldEqualJson expectedJson
    }

    @Test
    fun `should serialize get_weather tool with title optional property specified`() {
        val weatherTool = createWeatherTool(name = "get_weather", title = "Get weather")
        val expectedJson = createWeatherToolJson(name = "get_weather", title = "Get weather")
        val actualJson = McpJson.encodeToString(weatherTool)

        actualJson shouldEqualJson expectedJson
    }

    @Test
    fun `should serialize get_weather tool with outputSchema optional property specified`() {
        val weatherTool = createWeatherTool(
            name = "get_weather",
            outputSchema = Tool.Output(
                properties = buildJsonObject {
                    put(
                        "temperature",
                        buildJsonObject {
                            put("type", JsonPrimitive("number"))
                            put("description", JsonPrimitive("Temperature in celsius"))
                        },
                    )
                    put(
                        "conditions",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Weather conditions description"))
                        },
                    )
                    put(
                        "humidity",
                        buildJsonObject {
                            put("type", JsonPrimitive("number"))
                            put("description", JsonPrimitive("Humidity percentage"))
                        },
                    )
                },
                required = listOf("temperature", "conditions", "humidity"),
            ),
        )
        val expectedJson =
            createWeatherToolJson(
                name = "get_weather",
                outputSchema = """
            {
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
            }
                """.trimIndent(),
            )

        val actualJson = McpJson.encodeToString(weatherTool)

        actualJson shouldEqualJson expectedJson
    }

    @Test
    fun `should serialize get_weather tool with all properties specified`() {
        val weatherTool = createWeatherTool(
            name = "get_weather",
            title = "Get weather",
            outputSchema = Tool.Output(
                properties = buildJsonObject {
                    put(
                        "temperature",
                        buildJsonObject {
                            put("type", JsonPrimitive("number"))
                            put("description", JsonPrimitive("Temperature in celsius"))
                        },
                    )
                    put(
                        "conditions",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Weather conditions description"))
                        },
                    )
                    put(
                        "humidity",
                        buildJsonObject {
                            put("type", JsonPrimitive("number"))
                            put("description", JsonPrimitive("Humidity percentage"))
                        },
                    )
                },
                required = listOf("temperature", "conditions", "humidity"),
            ),
        )
        val expectedJson = createWeatherToolJson(
            name = "get_weather",
            title = "Get weather",
            outputSchema = """
            {
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
            }
            """.trimIndent(),
        )

        val actualJson = McpJson.encodeToString(weatherTool)

        actualJson shouldEqualJson expectedJson
    }

    //endregion Serialize

    //region Deserialize

    @Test
    fun `should deserialize get_weather tool`() {
        val actualTool = McpJson.decodeFromString<io.modelcontextprotocol.kotlin.sdk.types.Tool>(getWeatherToolJson)
        assertEquals(expected = getWeatherTool, actual = actualTool)
    }

    @Test
    fun `should deserialize get_weather tool without optional properties`() {
        val toolJson = createWeatherToolJson(name = "get_weather")
        val expectedTool = createWeatherTool(name = "get_weather")
        val actualTool = McpJson.decodeFromString<io.modelcontextprotocol.kotlin.sdk.types.Tool>(toolJson)

        assertEquals(expected = expectedTool, actual = actualTool)
    }

    @Test
    fun `should deserialize get_weather tool with title properties specified`() {
        val toolJson = createWeatherToolJson(name = "get_weather", title = "Get weather")
        val expectedTool = createWeatherTool(name = "get_weather", title = "Get weather")

        val actualTool = McpJson.decodeFromString<io.modelcontextprotocol.kotlin.sdk.types.Tool>(toolJson)

        assertEquals(expected = expectedTool, actual = actualTool)
    }

    @Test
    fun `should deserialize get_weather tool with outputSchema optional property specified`() {
        val toolJson =
            createWeatherToolJson(
                name = "get_weather",
                outputSchema = """
            {
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
            }
                """.trimIndent(),
            )

        val expectedTool = createWeatherTool(
            name = "get_weather",
            outputSchema = Tool.Output(
                properties = buildJsonObject {
                    put(
                        "temperature",
                        buildJsonObject {
                            put("type", JsonPrimitive("number"))
                            put("description", JsonPrimitive("Temperature in celsius"))
                        },
                    )
                    put(
                        "conditions",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Weather conditions description"))
                        },
                    )
                    put(
                        "humidity",
                        buildJsonObject {
                            put("type", JsonPrimitive("number"))
                            put("description", JsonPrimitive("Humidity percentage"))
                        },
                    )
                },
                required = listOf("temperature", "conditions", "humidity"),
            ),
        )

        val actualTool = McpJson.decodeFromString<io.modelcontextprotocol.kotlin.sdk.types.Tool>(toolJson)

        assertEquals(expected = expectedTool, actual = actualTool)
    }

    @Test
    fun `should deserialize get_weather tool with all properties specified`() {
        val toolJson = createWeatherToolJson(
            name = "get_weather",
            title = "Get weather",
            outputSchema = """
            {
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
            }
            """.trimIndent(),
        )

        val expectedTool = createWeatherTool(
            name = "get_weather",
            title = "Get weather",
            outputSchema = Tool.Output(
                properties = buildJsonObject {
                    put(
                        "temperature",
                        buildJsonObject {
                            put("type", JsonPrimitive("number"))
                            put("description", JsonPrimitive("Temperature in celsius"))
                        },
                    )
                    put(
                        "conditions",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Weather conditions description"))
                        },
                    )
                    put(
                        "humidity",
                        buildJsonObject {
                            put("type", JsonPrimitive("number"))
                            put("description", JsonPrimitive("Humidity percentage"))
                        },
                    )
                },
                required = listOf("temperature", "conditions", "humidity"),
            ),
        )

        val actualTool = McpJson.decodeFromString<io.modelcontextprotocol.kotlin.sdk.types.Tool>(toolJson)

        assertEquals(expected = expectedTool, actual = actualTool)
    }

    //endregion Deserialize

    //region Private Methods

    private fun createWeatherToolJson(
        name: String = "get_weather",
        title: String? = null,
        outputSchema: String? = null,
        meta: String? = null,
    ): String {
        val stringBuilder = StringBuilder()

        stringBuilder
            .appendLine("{")
            .append("  \"name\": \"$name\"")

        if (title != null) {
            stringBuilder
                .appendLine(",")
                .append("  \"title\": \"$title\"")
        }

        stringBuilder
            .appendLine(",")
            .append("  \"description\": \"Get the current weather in a given location\"")
            .appendLine(",")
            .append(
                """
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
                """.trimIndent(),
            )

        if (outputSchema != null) {
            stringBuilder
                .appendLine(",")
                .append(
                    """
                    "outputSchema": $outputSchema
                    """.trimIndent(),
                )
        }

        stringBuilder
            .appendLine(",")
            .append(
                """
                "_meta": ${meta ?: "{}"}
                """.trimIndent(),
            )

        stringBuilder
            .appendLine()
            .appendLine("}")

        return stringBuilder.toString().trimIndent()
    }

    private fun createWeatherTool(
        name: String = "get_weather",
        title: String? = null,
        outputSchema: ToolSchema? = null,
        meta: JsonObject? = null,
    ): io.modelcontextprotocol.kotlin.sdk.types.Tool = Tool(
        name = name,
        title = title,
        description = "Get the current weather in a given location",
        annotations = null,
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                put(
                    "location",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("The city and state, e.g. San Francisco, CA"))
                    },
                )
            },
            required = listOf("location"),
        ),
        outputSchema = outputSchema,
        _meta = meta ?: EmptyJsonObject,
    )

    //endregion Private Methods
}
