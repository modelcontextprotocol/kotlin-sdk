package io.modelcontextprotocol.kotlin.sdk.conformance

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.BooleanSchema
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.DoubleSchema
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource
import io.modelcontextprotocol.kotlin.sdk.types.EnumOption
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.IntegerSchema
import io.modelcontextprotocol.kotlin.sdk.types.LegacyTitledEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.SamplingMessage
import io.modelcontextprotocol.kotlin.sdk.types.StringSchema
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.TitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.TitledSingleSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledSingleSelectEnumSchema
import kotlinx.coroutines.delay
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

private val logger = KotlinLogging.logger {}

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
                    messages = listOf(SamplingMessage(Role.User, listOf(TextContent(prompt)))),
                ),
            ),
        )
        CallToolResult(listOf(TextContent(result.content.joinToString("\n") { it.toString() })))
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
            properties = mapOf(
                "username" to StringSchema(description = "User's response"),
                "email" to StringSchema(description = "User's email address"),
            ),
            required = listOf("username", "email"),
        )
        val result = createElicitation(message, schema)
        CallToolResult(listOf(TextContent("User response: <action: ${result.action}, content: ${result.content}>")))
    }

    // 10. Elicitation SEP1034 (defaults)
    addTool(
        name = "test_elicitation_sep1034_defaults",
        description = "test_elicitation_sep1034_defaults",
    ) {
        val schema = ElicitRequestParams.RequestedSchema(
            properties = mapOf(
                "name" to StringSchema(description = "User name", default = "John Doe"),
                "age" to IntegerSchema(description = "User age", default = 30),
                "score" to DoubleSchema(description = "User score", default = 95.5),
                "status" to UntitledSingleSelectEnumSchema(
                    description = "User status",
                    enumValues = listOf("active", "inactive", "pending"),
                    default = "active",
                ),
                "verified" to BooleanSchema(description = "Verification status", default = true),
            ),
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
            properties = mapOf(
                // Untitled single-select
                "untitledSingle" to UntitledSingleSelectEnumSchema(
                    enumValues = listOf("option1", "option2", "option3"),
                ),
                // Titled single-select
                "titledSingle" to TitledSingleSelectEnumSchema(
                    oneOf = listOf(
                        EnumOption(const = "value1", title = "First Option"),
                        EnumOption(const = "value2", title = "Second Option"),
                        EnumOption(const = "value3", title = "Third Option"),
                    ),
                ),
                // Legacy titled (deprecated) — uses enum + enumNames
                @Suppress("DEPRECATION")
                "legacyEnum"
                    to LegacyTitledEnumSchema(
                        enumValues = listOf("opt1", "opt2", "opt3"),
                        enumNames = listOf("Option One", "Option Two", "Option Three"),
                    ),
                // Untitled multi-select
                "untitledMulti" to UntitledMultiSelectEnumSchema(
                    items = UntitledMultiSelectEnumSchema.Items(
                        enumValues = listOf("option1", "option2", "option3"),
                    ),
                ),
                // Titled multi-select
                "titledMulti" to TitledMultiSelectEnumSchema(
                    items = TitledMultiSelectEnumSchema.Items(
                        anyOf = listOf(
                            EnumOption(const = "value1", title = "First Choice"),
                            EnumOption(const = "value2", title = "Second Choice"),
                            EnumOption(const = "value3", title = "Third Choice"),
                        ),
                    ),
                ),
            ),
        )
        val result = createElicitation(
            "Please review and update the form fields with defaults",
            schema,
        )
        CallToolResult(listOf(TextContent("Elicitation completed: action=${result.action}, content=${result.content}")))
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
        logger.debug { "[test_tool_with_logging] Sending message 1" }
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
        logger.debug { "[test_tool_with_logging] Sending message #2" }
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
        logger.debug { "[test_tool_with_logging] Sending message 3" }
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
            properties = mapOf(
                "name" to StringSchema(description = "User name", default = "John Doe"),
                "age" to IntegerSchema(description = "User age", default = 30),
                "score" to DoubleSchema(description = "User score", default = 95.5),
                "status" to UntitledSingleSelectEnumSchema(
                    description = "User status",
                    enumValues = listOf("active", "inactive", "pending"),
                    default = "active",
                ),
                "verified" to BooleanSchema(description = "Verification status", default = true),
            ),
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
