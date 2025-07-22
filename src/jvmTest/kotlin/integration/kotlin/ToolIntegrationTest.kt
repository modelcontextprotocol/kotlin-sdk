package integration.kotlin

import integration.utils.TestUtils.assertCallToolResult
import integration.utils.TestUtils.assertJsonProperty
import integration.utils.TestUtils.assertTextContent
import integration.utils.TestUtils.runTest
import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolIntegrationTest : KotlinTestBase() {

    override val port = 3006
    private val testToolName = "echo"
    private val testToolDescription = "A simple echo tool that returns the input text"
    private val complexToolName = "calculator"
    private val complexToolDescription = "A calculator tool that performs operations on numbers"
    private val errorToolName = "error-tool"
    private val errorToolDescription = "A tool that demonstrates error handling"
    private val multiContentToolName = "multi-content"
    private val multiContentToolDescription = "A tool that returns multiple content types"

    override fun configureServerCapabilities(): ServerCapabilities {
        return ServerCapabilities(
            tools = ServerCapabilities.Tools(
                listChanged = true
            )
        )
    }

    override fun configureServer() {
        setupEchoTool()
        setupCalculatorTool()
        setupErrorHandlingTool()
        setupMultiContentTool()
    }

    private fun setupEchoTool() {
        server.addTool(
            name = testToolName,
            description = testToolDescription,
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "The text to echo back")
                    })
                },
                required = listOf("text")
            )
        ) { request ->
            val text = (request.arguments["text"] as? JsonPrimitive)?.content ?: "No text provided"

            CallToolResult(
                content = listOf(TextContent(text = "Echo: $text")),
                structuredContent = buildJsonObject {
                    put("result", text)
                }
            )
        }
    }

    private fun setupCalculatorTool() {
        server.addTool(
            name = complexToolName,
            description = complexToolDescription,
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("operation", buildJsonObject {
                        put("type", "string")
                        put("description", "The operation to perform (add, subtract, multiply, divide)")
                        put("enum", buildJsonArray {
                            add("add")
                            add("subtract")
                            add("multiply")
                            add("divide")
                        })
                    })
                    put("a", buildJsonObject {
                        put("type", "number")
                        put("description", "First operand")
                    })
                    put("b", buildJsonObject {
                        put("type", "number")
                        put("description", "Second operand")
                    })
                    put("precision", buildJsonObject {
                        put("type", "integer")
                        put("description", "Number of decimal places (optional)")
                        put("default", 2)
                    })
                    put("showSteps", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether to show calculation steps")
                        put("default", false)
                    })
                    put("tags", buildJsonObject {
                        put("type", "array")
                        put("description", "Optional tags for the calculation")
                        put("items", buildJsonObject {
                            put("type", "string")
                        })
                    })
                },
                required = listOf("operation", "a", "b")
            )
        ) { request ->
            val operation = (request.arguments["operation"] as? JsonPrimitive)?.content ?: "add"
            val a = (request.arguments["a"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
            val b = (request.arguments["b"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
            val precision = (request.arguments["precision"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 2
            val showSteps = (request.arguments["showSteps"] as? JsonPrimitive)?.content?.toBoolean() ?: false
            val tags = (request.arguments["tags"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.content
            } ?: emptyList()

            val result = when (operation) {
                "add" -> a + b
                "subtract" -> a - b
                "multiply" -> a * b
                "divide" -> if (b != 0.0) a / b else Double.POSITIVE_INFINITY
                else -> 0.0
            }

            val formattedResult = "%.${precision}f".format(result)

            val textContent = if (showSteps) {
                "Operation: $operation\nA: $a\nB: $b\nResult: $formattedResult\nTags: ${tags.joinToString(", ")}"
            } else {
                "Result: $formattedResult"
            }

            CallToolResult(
                content = listOf(TextContent(text = textContent)),
                structuredContent = buildJsonObject {
                    put("operation", operation)
                    put("a", a)
                    put("b", b)
                    put("result", result)
                    put("formattedResult", formattedResult)
                    put("precision", precision)
                    put("tags", buildJsonArray { tags.forEach { add(it) } })
                }
            )
        }
    }

    private fun setupErrorHandlingTool() {
        server.addTool(
            name = errorToolName,
            description = errorToolDescription,
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("errorType", buildJsonObject {
                        put("type", "string")
                        put("description", "Type of error to simulate (none, exception, error)")
                        put("enum", buildJsonArray {
                            add("none")
                            add("exception")
                            add("error")
                        })
                    })
                    put("message", buildJsonObject {
                        put("type", "string")
                        put("description", "Custom error message")
                        put("default", "An error occurred")
                    })
                },
                required = listOf("errorType")
            )
        ) { request ->
            val errorType = (request.arguments["errorType"] as? JsonPrimitive)?.content ?: "none"
            val message = (request.arguments["message"] as? JsonPrimitive)?.content ?: "An error occurred"

            when (errorType) {
                "exception" -> throw IllegalArgumentException(message)
                "error" -> CallToolResult(
                    content = listOf(TextContent(text = "Error: $message")),
                    structuredContent = buildJsonObject {
                        put("error", true)
                        put("message", message)
                    }
                )

                else -> CallToolResult(
                    content = listOf(TextContent(text = "No error occurred")),
                    structuredContent = buildJsonObject {
                        put("error", false)
                        put("message", "Success")
                    }
                )
            }
        }
    }

    private fun setupMultiContentTool() {
        server.addTool(
            name = multiContentToolName,
            description = multiContentToolDescription,
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "Text to include in the response")
                    })
                    put("includeImage", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether to include an image in the response")
                        put("default", true)
                    })
                },
                required = listOf("text")
            )
        ) { request ->
            val text = (request.arguments["text"] as? JsonPrimitive)?.content ?: "Default text"
            val includeImage = (request.arguments["includeImage"] as? JsonPrimitive)?.content?.toBoolean() ?: true

            val content = mutableListOf<PromptMessageContent>(
                TextContent(text = "Text content: $text")
            )

            if (includeImage) {
                content.add(
                    ImageContent(
                        data = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
                        mimeType = "image/png"
                    )
                )
            }

            CallToolResult(
                content = content,
                structuredContent = buildJsonObject {
                    put("text", text)
                    put("includeImage", includeImage)
                }
            )
        }
    }


    @Test
    fun testListTools() = runTest {
        val result = client.listTools()

        assertNotNull(result, "List utils result should not be null")
        assertTrue(result.tools.isNotEmpty(), "Tools list should not be empty")

        val testTool = result.tools.find { it.name == testToolName }
        assertNotNull(testTool, "Test tool should be in the list")
        assertEquals(testToolDescription, testTool.description, "Tool description should match")
    }

    @Test
    fun testCallTool() = runTest {
        val testText = "Hello, world!"
        val arguments = mapOf("text" to testText)

        val result = client.callTool(testToolName, arguments)

        val toolResult = assertCallToolResult(result)
        assertTextContent(toolResult.content.firstOrNull(), "Echo: $testText")

        val structuredContent = toolResult.structuredContent as JsonObject
        assertJsonProperty(structuredContent, "result", testText)
    }

    @Test
    fun testComplexInputSchemaTool() {
        runTest {
            val toolsList = client.listTools()
            assertNotNull(toolsList, "Tools list should not be null")
            val calculatorTool = toolsList.tools.find { it.name == complexToolName }
            assertNotNull(calculatorTool, "Calculator tool should be in the list")

            val arguments = mapOf(
                "operation" to "multiply",
                "a" to 5.5,
                "b" to 2.0,
                "precision" to 3,
                "showSteps" to true,
                "tags" to listOf("test", "calculator", "integration"),
            )

            val result = client.callTool(complexToolName, arguments)

            val toolResult = assertCallToolResult(result)

            val content = toolResult.content.firstOrNull() as? TextContent
            assertNotNull(content, "Tool result content should be TextContent")
            assertNotNull(content.text, "Text content should not be null")
            val contentText = content.text


            assertTrue(contentText.contains("Operation"), "Result should contain operation")
            assertTrue(contentText.contains("multiply"), "Result should contain multiply operation")
            assertTrue(contentText.contains("5.5"), "Result should contain first operand")
            assertTrue(contentText.contains("2.0"), "Result should contain second operand")
            assertTrue(contentText.contains("11"), "Result should contain result value")

            val structuredContent = toolResult.structuredContent as JsonObject
            assertJsonProperty(structuredContent, "operation", "multiply")
            assertJsonProperty(structuredContent, "result", 11.0)

            val formattedResult = structuredContent["formattedResult"]?.toString()?.trim('"') ?: ""
            assertTrue(
                formattedResult == "11.000" || formattedResult == "11,000",
                "Formatted result should be either '11.000' or '11,000', but was '$formattedResult'"
            )
            assertJsonProperty(structuredContent, "precision", 3)

            val tags = structuredContent["tags"] as? JsonArray
            assertNotNull(tags, "Tags should be present")
        }
    }

    @Test
    fun testToolErrorHandling() = runTest {
        val successArgs = mapOf("errorType" to "none")
        val successResult = client.callTool(errorToolName, successArgs)

        val successToolResult = assertCallToolResult(successResult, "No error: ")
        assertTextContent(successToolResult.content.firstOrNull(), "No error occurred")

        val noErrorStructured = successToolResult.structuredContent as JsonObject
        assertJsonProperty(noErrorStructured, "error", false)

        val errorArgs = mapOf(
            "errorType" to "error",
            "message" to "Custom error message",
        )
        val errorResult = client.callTool(errorToolName, errorArgs)

        val errorToolResult = assertCallToolResult(errorResult, "Error: ")
        assertTextContent(errorToolResult.content.firstOrNull(), "Error: Custom error message")

        val errorStructured = errorToolResult.structuredContent as JsonObject
        assertJsonProperty(errorStructured, "error", true)
        assertJsonProperty(errorStructured, "message", "Custom error message")

        val exceptionArgs = mapOf(
            "errorType" to "exception",
            "message" to "Exception message",
        )

        val exception = assertThrows<Exception> {
            runBlocking {
                client.callTool(errorToolName, exceptionArgs)
            }
        }

        assertTrue(
            exception.message?.contains("Exception message") == true,
            "Exception message should contain 'Exception message'"
        )
    }

    @Test
    fun testMultiContentTool() = runTest {
        val testText = "Test multi-content"
        val arguments = mapOf(
            "text" to testText,
            "includeImage" to true,
        )

        val result = client.callTool(multiContentToolName, arguments)

        val toolResult = assertCallToolResult(result)
        assertEquals(2, toolResult.content.size, "Tool result should have 2 content items")

        val textContent = toolResult.content.firstOrNull { it is TextContent } as? TextContent
        assertNotNull(textContent, "Result should contain TextContent")
        assertNotNull(textContent.text, "Text content should not be null")
        assertEquals("Text content: $testText", textContent.text, "Text content should match")

        val imageContent = toolResult.content.firstOrNull { it is ImageContent } as? ImageContent
        assertNotNull(imageContent, "Result should contain ImageContent")
        assertEquals("image/png", imageContent.mimeType, "Image MIME type should match")
        assertTrue(imageContent.data.isNotEmpty(), "Image data should not be empty")

        val structuredContent = toolResult.structuredContent as JsonObject
        assertJsonProperty(structuredContent, "text", testText)
        assertJsonProperty(structuredContent, "includeImage", true)

        val textOnlyArgs = mapOf(
            "text" to testText,
            "includeImage" to false,
        )

        val textOnlyResult = client.callTool(multiContentToolName, textOnlyArgs)

        val textOnlyToolResult = assertCallToolResult(textOnlyResult, "Text-only: ")
        assertEquals(1, textOnlyToolResult.content.size, "Text-only result should have 1 content item")

        assertTextContent(textOnlyToolResult.content.firstOrNull(), "Text content: $testText")
    }
}