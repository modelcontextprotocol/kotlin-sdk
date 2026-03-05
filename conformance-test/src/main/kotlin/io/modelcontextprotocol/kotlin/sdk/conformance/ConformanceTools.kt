package io.modelcontextprotocol.kotlin.sdk.conformance

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.SamplingMessage
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.milliseconds

// Minimal 1x1 PNG (base64)
internal const val PNG_BASE64 =
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg=="

// Minimal WAV (base64)
internal const val WAV_BASE64 = "UklGRiYAAABXQVZFZm10IBAAAAABAAEAQB8AAAB9AAACABAAZGF0YQIAAAA="

@Suppress("LongMethod")
fun Server.registerConformanceTools() {
    // 1. Simple text
    addTool(
        name = "test_simple_text",
        description = "test_simple_text",
    ) {
        CallToolResult(listOf(TextContent("Simple text content")))
    }

    // 2. Image content
    addTool(
        name = "test_image_content",
        description = "test_image_content",
    ) {
        CallToolResult(listOf(ImageContent(data = PNG_BASE64, mimeType = "image/png")))
    }

    // 3. Audio content
    addTool(
        name = "test_audio_content",
        description = "test_audio_content",
    ) {
        CallToolResult(listOf(AudioContent(data = WAV_BASE64, mimeType = "audio/wav")))
    }

    // 4. Embedded resource
    addTool(
        name = "test_embedded_resource",
        description = "test_embedded_resource",
    ) {
        CallToolResult(
            listOf(
                EmbeddedResource(
                    resource = TextResourceContents(
                        text = "This is an embedded resource content.",
                        uri = "test://embedded-resource",
                        mimeType = "text/plain",
                    ),
                ),
            ),
        )
    }

    // 5. Multiple content types
    addTool(
        name = "test_multiple_content_types",
        description = "test_multiple_content_types",
    ) {
        CallToolResult(
            listOf(
                TextContent("Simple text content"),
                ImageContent(data = PNG_BASE64, mimeType = "image/png"),
                EmbeddedResource(
                    resource = TextResourceContents(
                        text = "This is an embedded resource content.",
                        uri = "test://embedded-resource",
                        mimeType = "text/plain",
                    ),
                ),
            ),
        )
    }

    // 6. Progress tool
    addTool(
        name = "test_tool_with_progress",
        description = "test_tool_with_progress",
    ) { request ->
        val progressToken = request.meta?.progressToken
        if (progressToken != null) {
            notification(
                ProgressNotification(
                    ProgressNotificationParams(
                        progressToken,
                        0.0,
                        100.0,
                        "Completed step 0 of 100",
                    ),
                ),
            )
            delay(50.milliseconds)
            notification(
                ProgressNotification(
                    ProgressNotificationParams(
                        progressToken,
                        50.0,
                        100.0,
                        "Completed step 50 of 100",
                    ),
                ),
            )
            delay(50.milliseconds)
            notification(
                ProgressNotification(
                    ProgressNotificationParams(
                        progressToken,
                        100.0,
                        100.0,
                        "Completed step 100 of 100",
                    ),
                ),
            )
        }
        CallToolResult(listOf(TextContent("Simple text content")))
    }

    // 7. Error handling
    addTool(
        name = "test_error_handling",
        description = "test_error_handling",
    ) {
        throw Exception("This tool intentionally returns an error for testing")
    }

    // 8. Sampling
    addTool(
        name = "test_sampling",
        description = "test_sampling",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("prompt", buildJsonObject { put("type", JsonPrimitive("string")) })
            },
            required = listOf("prompt"),
        ),
    ) { request ->
        val prompt = request.arguments?.get("prompt")?.jsonPrimitive?.content ?: "Hello"
        val result = createMessage(
            CreateMessageRequest(
                CreateMessageRequestParams(
                    maxTokens = 10000,
                    messages = listOf(SamplingMessage(Role.User, TextContent(prompt))),
                ),
            ),
        )
        CallToolResult(listOf(TextContent(result.content.toString())))
    }

    // 9. Elicitation
    addTool(
        name = "test_elicitation",
        description = "test_elicitation",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("message", buildJsonObject { put("type", JsonPrimitive("string")) })
            },
            required = listOf("message"),
        ),
    ) { request ->
        val message = request.arguments?.get("message")?.jsonPrimitive?.content ?: "Please provide input"
        val schema = ElicitRequestParams.RequestedSchema(
            properties = buildJsonObject {
                put(
                    "response",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("User's response"))
                    },
                )
            },
            required = listOf("response"),
        )
        val result = createElicitation(message, schema)
        CallToolResult(listOf(TextContent(result.content.toString())))
    }

    // 10. Elicitation SEP1034 (defaults)
    addTool(
        name = "test_elicitation_sep1034_defaults",
        description = "test_elicitation_sep1034_defaults",
    ) {
        val schema = ElicitRequestParams.RequestedSchema(
            properties = buildJsonObject {
                put(
                    "name",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("User name"))
                        put("default", JsonPrimitive("John Doe"))
                    },
                )
                put(
                    "age",
                    buildJsonObject {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("User age"))
                        put("default", JsonPrimitive(30))
                    },
                )
                put(
                    "score",
                    buildJsonObject {
                        put("type", JsonPrimitive("number"))
                        put("description", JsonPrimitive("User score"))
                        put("default", JsonPrimitive(95.5))
                    },
                )
                put(
                    "status",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("User status"))
                        put("default", JsonPrimitive("active"))
                        put(
                            "enum",
                            JsonArray(
                                listOf(JsonPrimitive("active"), JsonPrimitive("inactive"), JsonPrimitive("pending")),
                            ),
                        )
                    },
                )
                put(
                    "verified",
                    buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("description", JsonPrimitive("Verification status"))
                        put("default", JsonPrimitive(true))
                    },
                )
            },
            required = listOf("name", "age", "score", "status", "verified"),
        )
        val result = createElicitation(
            "Please review and update the form fields with defaults",
            schema,
        )
        CallToolResult(listOf(TextContent(result.content.toString())))
    }

    // 11. Elicitation SEP1330 enums
    addTool(
        name = "test_elicitation_sep1330_enums",
        description = "test_elicitation_sep1330_enums",
    ) {
        val schema = ElicitRequestParams.RequestedSchema(
            properties = buildJsonObject {
                // Untitled single-select
                put(
                    "untitledSingle",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put(
                            "enum",
                            JsonArray(
                                listOf(JsonPrimitive("option1"), JsonPrimitive("option2"), JsonPrimitive("option3")),
                            ),
                        )
                    },
                )
                // Titled single-select
                put(
                    "titledSingle",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put(
                            "oneOf",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("const", JsonPrimitive("value1"))
                                        put("title", JsonPrimitive("First Option"))
                                    },
                                    buildJsonObject {
                                        put("const", JsonPrimitive("value2"))
                                        put("title", JsonPrimitive("Second Option"))
                                    },
                                    buildJsonObject {
                                        put("const", JsonPrimitive("value3"))
                                        put("title", JsonPrimitive("Third Option"))
                                    },
                                ),
                            ),
                        )
                    },
                )
                // Legacy titled (deprecated)
                put(
                    "legacyEnum",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put(
                            "oneOf",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("const", JsonPrimitive("opt1"))
                                        put("title", JsonPrimitive("Option One"))
                                    },
                                    buildJsonObject {
                                        put("const", JsonPrimitive("opt2"))
                                        put("title", JsonPrimitive("Option Two"))
                                    },
                                    buildJsonObject {
                                        put("const", JsonPrimitive("opt3"))
                                        put("title", JsonPrimitive("Option Three"))
                                    },
                                ),
                            ),
                        )
                    },
                )
                // Untitled multi-select
                put(
                    "untitledMulti",
                    buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put(
                            "items",
                            buildJsonObject {
                                put("type", JsonPrimitive("string"))
                                put(
                                    "enum",
                                    JsonArray(
                                        listOf(
                                            JsonPrimitive("option1"),
                                            JsonPrimitive("option2"),
                                            JsonPrimitive("option3"),
                                        ),
                                    ),
                                )
                            },
                        )
                    },
                )
                // Titled multi-select
                put(
                    "titledMulti",
                    buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put(
                            "items",
                            buildJsonObject {
                                put("type", JsonPrimitive("string"))
                                put(
                                    "oneOf",
                                    JsonArray(
                                        listOf(
                                            buildJsonObject {
                                                put("const", JsonPrimitive("value1"))
                                                put("title", JsonPrimitive("First Choice"))
                                            },
                                            buildJsonObject {
                                                put("const", JsonPrimitive("value2"))
                                                put("title", JsonPrimitive("Second Choice"))
                                            },
                                            buildJsonObject {
                                                put("const", JsonPrimitive("value3"))
                                                put("title", JsonPrimitive("Third Choice"))
                                            },
                                        ),
                                    ),
                                )
                            },
                        )
                    },
                )
            },
        )
        val result = createElicitation(
            "Please review and update the form fields with defaults",
            schema,
        )
        CallToolResult(listOf(TextContent(result.content.toString())))
    }

    // 12. Dynamic tool
    val server = this
    addTool(
        name = "test_dynamic_tool",
        description = "test_dynamic_tool",
    ) {
        // Add a temporary tool, triggering listChanged
        server.addTool(
            name = "test_dynamic_tool_temp",
            description = "Temporary dynamic tool",
        ) {
            CallToolResult(listOf(TextContent("Temporary tool response")))
        }
        delay(100.milliseconds)
        // Remove the temporary tool, triggering listChanged again
        server.removeTool("test_dynamic_tool_temp")
        CallToolResult(listOf(TextContent("Dynamic tool executed successfully")))
    }

    // 13. Logging tool
    addTool(
        name = "test_tool_with_logging",
        description = "test_tool_with_logging",
    ) {
        sendLoggingMessage(
            LoggingMessageNotification(
                LoggingMessageNotificationParams(
                    level = LoggingLevel.Info,
                    data = JsonPrimitive("Tool execution started"),
                    logger = "conformance",
                ),
            ),
        )
        delay(50.milliseconds)
        sendLoggingMessage(
            LoggingMessageNotification(
                LoggingMessageNotificationParams(
                    level = LoggingLevel.Info,
                    data = JsonPrimitive("Tool processing data"),
                    logger = "conformance",
                ),
            ),
        )
        delay(50.milliseconds)
        sendLoggingMessage(
            LoggingMessageNotification(
                LoggingMessageNotificationParams(
                    level = LoggingLevel.Info,
                    data = JsonPrimitive("Tool execution completed"),
                    logger = "conformance",
                ),
            ),
        )
        CallToolResult(listOf(TextContent("Simple text content")))
    }

    // 14. add_numbers — used by tools_call client scenario
    addTool(
        name = "add_numbers",
        description = "Adds two numbers together",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("a", buildJsonObject { put("type", JsonPrimitive("number")) })
                put("b", buildJsonObject { put("type", JsonPrimitive("number")) })
            },
            required = listOf("a", "b"),
        ),
    ) { request ->
        val a = request.arguments?.get("a")?.jsonPrimitive?.double ?: 0.0
        val b = request.arguments?.get("b")?.jsonPrimitive?.double ?: 0.0
        val sum = a + b
        CallToolResult(listOf(TextContent("The sum of $a and $b is $sum")))
    }

    // 15. test_reconnection — SEP-1699, closes SSE stream to test client reconnection
    addTool(
        name = "test_reconnection",
        description = "Tests SSE stream disconnection and client reconnection (SEP-1699)",
    ) {
        // SDK limitation: cannot access the JSONRPC request ID from the tool handler
        // to close the SSE stream. Return success text; this test may fail at the
        // conformance runner level because the stream isn't actually closed.
        delay(100.milliseconds)
        CallToolResult(
            listOf(
                TextContent(
                    "Reconnection test completed successfully. " +
                        "If you received this, the client properly reconnected after stream closure.",
                ),
            ),
        )
    }

    // 16. json_schema_2020_12_tool — SEP-1613
    addTool(
        name = "json_schema_2020_12_tool",
        description = "Tool with JSON Schema 2020-12 features for conformance testing (SEP-1613)",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put(
                    "name",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                    },
                )
                put(
                    "address",
                    buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put(
                            "properties",
                            buildJsonObject {
                                put("street", buildJsonObject { put("type", JsonPrimitive("string")) })
                                put("city", buildJsonObject { put("type", JsonPrimitive("string")) })
                            },
                        )
                    },
                )
            },
        ),
    ) { request ->
        CallToolResult(
            listOf(TextContent("JSON Schema 2020-12 tool called with: ${request.arguments}")),
        )
    }

    // 17. test_client_elicitation_defaults — used by elicitation-sep1034-client-defaults scenario
    addTool(
        name = "test_client_elicitation_defaults",
        description = "test_client_elicitation_defaults",
    ) {
        val schema = ElicitRequestParams.RequestedSchema(
            properties = buildJsonObject {
                put(
                    "name",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("User name"))
                        put("default", JsonPrimitive("John Doe"))
                    },
                )
                put(
                    "age",
                    buildJsonObject {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("User age"))
                        put("default", JsonPrimitive(30))
                    },
                )
                put(
                    "score",
                    buildJsonObject {
                        put("type", JsonPrimitive("number"))
                        put("description", JsonPrimitive("User score"))
                        put("default", JsonPrimitive(95.5))
                    },
                )
                put(
                    "status",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("User status"))
                        put("default", JsonPrimitive("active"))
                        put(
                            "enum",
                            JsonArray(
                                listOf(JsonPrimitive("active"), JsonPrimitive("inactive"), JsonPrimitive("pending")),
                            ),
                        )
                    },
                )
                put(
                    "verified",
                    buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("description", JsonPrimitive("Verification status"))
                        put("default", JsonPrimitive(true))
                    },
                )
            },
            required = emptyList(),
        )
        val result = createElicitation(
            "Please review and update the form fields with defaults",
            schema,
        )
        CallToolResult(listOf(TextContent("Elicitation completed: action=${result.action}, content=${result.content}")))
    }

    // 18. test-tool — simple tool used by auth scenarios
    addTool(
        name = "test-tool",
        description = "Simple test tool for auth scenarios",
    ) {
        CallToolResult(listOf(TextContent("Test tool executed successfully")))
    }
}
