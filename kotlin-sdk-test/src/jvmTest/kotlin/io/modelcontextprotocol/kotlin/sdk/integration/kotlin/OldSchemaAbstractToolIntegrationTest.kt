package io.modelcontextprotocol.kotlin.sdk.integration.kotlin

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.PromptMessageContent
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class OldSchemaAbstractToolIntegrationTest : OldSchemaKotlinTestBase() {
    private val testToolName = "echo"
    private val testToolDescription = "A simple echo tool that returns the input text"
    private val complexToolName = "calculator"
    private val complexToolDescription = "A calculator tool that performs operations on numbers"
    private val errorToolName = "error-tool"
    private val errorToolDescription = "A tool that demonstrates error handling"
    private val multiContentToolName = "multi-content"
    private val multiContentToolDescription = "A tool that returns multiple content types"

    private val basicToolName = "basic-tool"
    private val basicToolDescription = "A basic tool for testing"

    private val largeToolName = "large-tool"
    private val largeToolDescription = "A tool that returns a large response"
    private val largeToolContent = "X".repeat(100_000) // 100KB of data

    private val slowToolName = "slow-tool"
    private val slowToolDescription = "A tool that takes time to respond"

    private val specialCharsToolName = "special-chars-tool"
    private val specialCharsToolDescription = "A tool that handles special characters"
    private val specialCharsContent = "!@#$%^&*()_+{}|:\"<>?~`-=[]\\;',./\n\t"

    override fun configureServerCapabilities(): io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities =
        ServerCapabilities(
            tools = ServerCapabilities.Tools(
                listChanged = true,
            ),
        )

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
                    put(
                        "text",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "The text to echo back")
                        },
                    )
                },
                required = listOf("text"),
            ),
        ) { request ->
            val text = (request.params.arguments?.get("text") as? JsonPrimitive)?.content ?: "No text provided"

            CallToolResult(
                content = listOf(TextContent(text = "Echo: $text")),
                structuredContent = buildJsonObject {
                    put("result", text)
                },
            )
        }
    }

    private fun setupCalculatorTool() {
        server.addTool(
            name = basicToolName,
            description = basicToolDescription,
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put(
                        "text",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "The text to echo back")
                        },
                    )
                },
                required = listOf("text"),
            ),
        ) { request ->
            val text = (request.params.arguments?.get("text") as? JsonPrimitive)?.content ?: "No text provided"

            CallToolResult(
                content = listOf(TextContent(text = "Echo: $text")),
                structuredContent = buildJsonObject {
                    put("result", text)
                },
            )
        }

        server.addTool(
            name = specialCharsToolName,
            description = specialCharsToolDescription,
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put(
                        "special",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Special characters to process")
                        },
                    )
                },
            ),
        ) { request ->
            val special = (request.params.arguments?.get("special") as? JsonPrimitive)?.content ?: specialCharsContent

            CallToolResult(
                content = listOf(TextContent(text = "Received special characters: $special")),
                structuredContent = buildJsonObject {
                    put("special", special)
                    put("length", special.length)
                },
            )
        }

        server.addTool(
            name = slowToolName,
            description = slowToolDescription,
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put(
                        "delay",
                        buildJsonObject {
                            put("type", "integer")
                            put("description", "Delay in milliseconds")
                        },
                    )
                },
            ),
        ) { request ->
            val delay = (request.params.arguments?.get("delay") as? JsonPrimitive)?.content?.toIntOrNull() ?: 1000

            // simulate slow operation
            runBlocking {
                delay(delay.toLong())
            }

            CallToolResult(
                content = listOf(TextContent(text = "Completed after ${delay}ms delay")),
                structuredContent = buildJsonObject {
                    put("delay", delay)
                },
            )
        }

        server.addTool(
            name = largeToolName,
            description = largeToolDescription,
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put(
                        "size",
                        buildJsonObject {
                            put("type", "integer")
                            put("description", "Size multiplier")
                        },
                    )
                },
            ),
        ) { request ->
            val size = (request.params.arguments?.get("size") as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
            val content = largeToolContent.take(largeToolContent.length.coerceAtMost(size * 1000))

            CallToolResult(
                content = listOf(TextContent(text = content)),
                structuredContent = buildJsonObject {
                    put("size", content.length)
                },
            )
        }

        server.addTool(
            name = complexToolName,
            description = complexToolDescription,
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put(
                        "operation",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "The operation to perform (add, subtract, multiply, divide)")
                            put(
                                "enum",
                                buildJsonArray {
                                    add("add")
                                    add("subtract")
                                    add("multiply")
                                    add("divide")
                                },
                            )
                        },
                    )
                    put(
                        "a",
                        buildJsonObject {
                            put("type", "number")
                            put("description", "First operand")
                        },
                    )
                    put(
                        "b",
                        buildJsonObject {
                            put("type", "number")
                            put("description", "Second operand")
                        },
                    )
                    put(
                        "precision",
                        buildJsonObject {
                            put("type", "integer")
                            put("description", "Number of decimal places (optional)")
                            put("default", 2)
                        },
                    )
                    put(
                        "showSteps",
                        buildJsonObject {
                            put("type", "boolean")
                            put("description", "Whether to show calculation steps")
                            put("default", false)
                        },
                    )
                    put(
                        "tags",
                        buildJsonObject {
                            put("type", "array")
                            put("description", "Optional tags for the calculation")
                            put(
                                "items",
                                buildJsonObject {
                                    put("type", "string")
                                },
                            )
                        },
                    )
                },
                required = listOf("operation", "a", "b"),
            ),
        ) { request ->
            val operation = (request.params.arguments?.get("operation") as? JsonPrimitive)?.content ?: "add"
            val a = (request.params.arguments?.get("a") as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
            val b = (request.params.arguments?.get("b") as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
            val precision = (request.params.arguments?.get("precision") as? JsonPrimitive)?.content?.toIntOrNull() ?: 2
            val showSteps =
                (request.params.arguments?.get("showSteps") as? JsonPrimitive)?.content?.toBoolean() ?: false
            val tags = (request.params.arguments?.get("tags") as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.content
            } ?: emptyList()

            val result = when (operation) {
                "add" -> a + b
                "subtract" -> a - b
                "multiply" -> a * b
                "divide" -> if (b != 0.0) a / b else Double.POSITIVE_INFINITY
                else -> 0.0
            }

            val pattern = if (precision > 0) "0." + "0".repeat(precision) else "0"
            val symbols = DecimalFormatSymbols(Locale.US).apply { decimalSeparator = '.' }
            val df = DecimalFormat(pattern, symbols).apply { isGroupingUsed = false }
            val formattedResult = df.format(result)

            val textContent = if (showSteps) {
                "Operation: $operation\nA: $a\nB: $b\nResult: $formattedResult\nTags: ${
                    tags.joinToString(", ")
                }"
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
                },
            )
        }
    }

    private fun setupErrorHandlingTool() {
        server.addTool(
            name = errorToolName,
            description = errorToolDescription,
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put(
                        "errorType",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Type of error to simulate (none, exception, error)")
                            put(
                                "enum",
                                buildJsonArray {
                                    add("none")
                                    add("exception")
                                    add("error")
                                },
                            )
                        },
                    )
                    put(
                        "message",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Custom error message")
                            put("default", "An error occurred")
                        },
                    )
                },
                required = listOf("errorType"),
            ),
        ) { request ->
            val errorType = (request.params.arguments?.get("errorType") as? JsonPrimitive)?.content ?: "none"
            val message = (request.params.arguments?.get("message") as? JsonPrimitive)?.content ?: "An error occurred"

            when (errorType) {
                "exception" -> throw IllegalArgumentException(message)

                "error" -> CallToolResult(
                    content = listOf(TextContent(text = "Error: $message")),
                    structuredContent = buildJsonObject {
                        put("error", true)
                        put("message", message)
                    },
                )

                else -> CallToolResult(
                    content = listOf(TextContent(text = "No error occurred")),
                    structuredContent = buildJsonObject {
                        put("error", false)
                        put("message", "Success")
                    },
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
                    put(
                        "text",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Text to include in the response")
                        },
                    )
                    put(
                        "includeImage",
                        buildJsonObject {
                            put("type", "boolean")
                            put("description", "Whether to include an image in the response")
                            put("default", true)
                        },
                    )
                },
                required = listOf("text"),
            ),
        ) { request ->
            val text = (request.params.arguments?.get("text") as? JsonPrimitive)?.content ?: "Default text"
            val includeImage =
                (request.params.arguments?.get("includeImage") as? JsonPrimitive)?.content?.toBoolean() ?: true

            val content = mutableListOf<PromptMessageContent>(
                TextContent(text = "Text content: $text"),
            )

            if (includeImage) {
                content.add(
                    ImageContent(
                        data = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
                        mimeType = "image/png",
                    ),
                )
            }

            CallToolResult(
                content = content,
                structuredContent = buildJsonObject {
                    put("text", text)
                    put("includeImage", includeImage)
                },
            )
        }
    }

    @Test
    fun testListTools(): Unit = runBlocking(Dispatchers.IO) {
        val result = client.listTools()

        assertNotNull(result, "List tools result should not be null")
        assertNull(actual = result.meta, "List tools result meta should be null")
        assertTrue(result.tools.isNotEmpty(), "Tools list should not be empty")

        val testTool = result.tools.find { it.name == testToolName }

        assertNotNull(testTool, "Test tool should be in the list")
        assertEquals(
            testToolDescription,
            testTool.description,
            "Tool description should match",
        )
    }

    @Test
    fun testCallTool(): Unit = runBlocking(Dispatchers.IO) {
        val testText = "Hello, world!"
        val arguments = mapOf("text" to testText)

        val result = client.callTool(testToolName, arguments)
        assertNull(actual = result.meta, "CallTool result meta should be null")

        val actualContent = result.structuredContent.toString()
        val expectedContent = """
                {"result":"Hello, world!"}
        """.trimIndent()

        actualContent shouldEqualJson expectedContent
    }

    @Test
    fun testComplexInputSchemaTool(): Unit = runBlocking(Dispatchers.IO) {
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

        val result = client.callTool(
            complexToolName,
            arguments,
        )

        val actualContent = result.structuredContent.toString()
        val expectedContent = """
                {
                  "operation" : "multiply",
                  "a" : 5.5,
                  "b" : 2.0,
                  "result" : 11.0,
                  "formattedResult" : "11.000",
                  "precision" : 3,
                  "tags" : ["test", "calculator", "integration"]
                }
        """.trimIndent()

        actualContent shouldEqualJson expectedContent
    }

    @Test
    fun testToolErrorHandling(): Unit = runBlocking(Dispatchers.IO) {
        val successArgs = mapOf("errorType" to "none")
        val successResult = client.callTool(errorToolName, successArgs)

        val actualContent = successResult?.structuredContent.toString()
        val expectedContent = """
            {
              "error" : false,
              "message" : "Success"
            }
        """.trimIndent()

        actualContent shouldEqualJson expectedContent

        val errorArgs = mapOf(
            "errorType" to "error",
            "message" to "Custom error message",
        )
        val errorResult = client.callTool(
            errorToolName,
            errorArgs,
        )

        val actualError = errorResult.structuredContent.toString()
        val expectedError = """
            {
              "error" : true,
              "message" : "Custom error message"
            }
        """.trimIndent()

        actualError shouldEqualJson expectedError

        val exceptionArgs = mapOf(
            "errorType" to "exception",
            "message" to "My exception message",
        )

        val exceptionResult = client.callTool(
            errorToolName,
            exceptionArgs,
        )

        assertTrue(exceptionResult.isError ?: false, "isError should be true for exception")

        val exceptionContent = exceptionResult.content.firstOrNull { it is TextContent } as? TextContent
        assertNotNull(exceptionContent, "Error content should be present in the result")

        val exceptionText = exceptionContent.text ?: ""
        assertTrue(
            exceptionText.contains("Error executing tool") && exceptionText.contains("My exception message"),
            "Error message should contain the exception details",
        )
    }

    @Test
    fun testMultiContentTool(): Unit = runBlocking(Dispatchers.IO) {
        val testText = "Test multi-content"
        val arguments = mapOf(
            "text" to testText,
            "includeImage" to true,
        )

        val result = client.callTool(
            multiContentToolName,
            arguments,
        )

        assertEquals(
            2,
            result.content.size,
            "Tool result should have 2 content items",
        )

        val textContent = result.content.firstOrNull { it is TextContent } as? TextContent
        assertNotNull(textContent, "Result should contain TextContent")
        assertNotNull(textContent.text, "Text content should not be null")
        assertEquals(
            "Text content: $testText",
            textContent.text,
            "Text content should match",
        )

        val imageContent = result.content.firstOrNull { it is ImageContent } as? ImageContent
        assertNotNull(imageContent, "Result should contain ImageContent")
        assertEquals("image/png", imageContent.mimeType, "Image MIME type should match")
        assertTrue(imageContent.data.isNotEmpty(), "Image data should not be empty")

        val actualContent = result.structuredContent.toString()
        val expectedContent = """
            {
              "text" : "Test multi-content",
              "includeImage" : true
            }
        """.trimIndent()

        actualContent shouldEqualJson expectedContent

        val textOnlyArgs = mapOf(
            "text" to testText,
            "includeImage" to false,
        )

        val textOnlyResult = client.callTool(
            multiContentToolName,
            textOnlyArgs,
        )

        assertEquals(
            1,
            textOnlyResult.content.size,
            "Text-only result should have 1 content item",
        )
    }

    @Test
    fun testComplexNestedSchema(): Unit = runBlocking(Dispatchers.IO) {
        val userJson = buildJsonObject {
            put("name", JsonPrimitive("John Galt"))
            put("age", JsonPrimitive(30))
            put(
                "address",
                buildJsonObject {
                    put("street", JsonPrimitive("123 Main St"))
                    put("city", JsonPrimitive("New York"))
                    put("country", JsonPrimitive("USA"))
                },
            )
        }

        val optionsJson = buildJsonArray {
            add(JsonPrimitive("option1"))
            add(JsonPrimitive("option2"))
            add(JsonPrimitive("option3"))
        }

        val arguments = buildJsonObject {
            put("user", userJson)
            put("options", optionsJson)
        }

        val result = client.callTool(
            CallToolRequest(
                CallToolRequestParams(
                    name = complexToolName,
                    arguments = arguments,
                ),
            ),
        )

        val actualContent = result.structuredContent.toString()
        val expectedContent = """
            {
              "operation": "add",
              "a": 0.0,
              "b": 0.0,
              "result": 0.0,
              "formattedResult": "0.00",
              "precision": 2,
              "tags": []
            }
        """.trimIndent()

        actualContent shouldEqualJson expectedContent
    }

    @Test
    fun testLargeResponse(): Unit = runBlocking(Dispatchers.IO) {
        val size = 10
        val arguments = mapOf("size" to size)

        val result = client.callTool(
            largeToolName,
            arguments,
        )

        val content = result.content.firstOrNull() as TextContent
        assertNotNull(content, "Tool result content should be TextContent")

        val actualContent = result.structuredContent.toString()
        val expectedContent = """
            {
              "size" : 10000
            }
        """.trimIndent()

        actualContent shouldEqualJson expectedContent
    }

    @Test
    fun testSlowTool(): Unit = runBlocking(Dispatchers.IO) {
        val delay = 500
        val arguments = mapOf("delay" to delay)

        val startTime = System.currentTimeMillis()
        val result = client.callTool(slowToolName, arguments)
        val endTime = System.currentTimeMillis()

        val content = result.content.firstOrNull() as? TextContent
        assertNotNull(content, "Tool result content should be TextContent")

        assertTrue(endTime - startTime >= delay, "Tool should take at least the specified delay")

        val actualContent = result.structuredContent.toString()
        val expectedContent = """
            {
              "delay" : 500
            }
        """.trimIndent()

        actualContent shouldEqualJson expectedContent
    }

    @Test
    fun testSpecialCharacters() {
        runBlocking(Dispatchers.IO) {
            val arguments = mapOf("special" to specialCharsContent)

            val result = client.callTool(
                specialCharsToolName,
                arguments,
            )

            val content = result.content.firstOrNull() as? TextContent
            assertNotNull(content, "Tool result content should be TextContent")
            val text = content.text ?: ""

            assertTrue(text.contains(specialCharsContent), "Result should contain the special characters")

            val actualContent = result.structuredContent.toString()
            val expectedContent = """
            {
              "special" : "!@#$%^&*()_+{}|:\"<>?~`-=[]\\;',./\n\t",
              "length" : 34
            }
            """.trimIndent()

            actualContent shouldEqualJson expectedContent
        }
    }

    @Test
    fun testConcurrentToolCalls() = runTest {
        val concurrentCount = 10
        val results = mutableListOf<io.modelcontextprotocol.kotlin.sdk.types.CallToolResult?>()

        runBlocking {
            repeat(concurrentCount) { index ->
                launch {
                    val toolName = when (index % 5) {
                        0 -> basicToolName
                        1 -> complexToolName
                        2 -> largeToolName
                        3 -> slowToolName
                        else -> specialCharsToolName
                    }

                    val arguments = when (toolName) {
                        basicToolName -> mapOf("text" to "Concurrent call $index")

                        complexToolName -> mapOf(
                            "user" to mapOf(
                                "name" to "User $index",
                                "age" to 20 + index,
                                "address" to mapOf(
                                    "street" to "Street $index",
                                    "city" to "City $index",
                                    "country" to "Country $index",
                                ),
                            ),
                        )

                        largeToolName -> mapOf("size" to 1)

                        slowToolName -> mapOf("delay" to 100)

                        else -> mapOf("special" to "!@#$%^&*()")
                    }

                    val result = client.callTool(toolName, arguments)

                    synchronized(results) {
                        results.add(result)
                    }
                }
            }
        }

        assertEquals(concurrentCount, results.size, "All concurrent operations should complete")
        results.forEach { result ->
            assertNotNull(result, "Result should not be null")
            assertTrue(result.content.isNotEmpty(), "Result content should not be empty")
        }
    }

    @Test
    fun testNonExistentTool() = runTest {
        val nonExistentToolName = "non-existent-tool"
        val arguments = mapOf("text" to "Test")

        val result = runBlocking {
            client.callTool(nonExistentToolName, arguments)
        }

        assertNotNull(result, "Tool call result should not be null")
        val callResult = result
        assertTrue(callResult.isError ?: false, "isError should be true for non-existent tool")

        val textContent = callResult.content.firstOrNull { it is TextContent } as? TextContent
        assertNotNull(textContent, "Error content should be present in the result")

        val errorText = textContent.text ?: ""
        assertTrue(
            errorText.contains("non-existent-tool") && errorText.contains("not found"),
            "Error message should indicate the tool was not found",
        )
    }
}
