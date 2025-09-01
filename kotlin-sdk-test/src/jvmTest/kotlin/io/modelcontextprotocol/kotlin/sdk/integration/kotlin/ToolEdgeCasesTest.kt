package io.modelcontextprotocol.kotlin.sdk.integration.kotlin

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.CallToolResultBase
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.integration.utils.TestUtils.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolEdgeCasesTest : KotlinTestBase() {

    override val port = 3009

    private val basicToolName = "basic-tool"
    private val basicToolDescription = "A basic tool for testing"

    private val complexToolName = "complex-tool"
    private val complexToolDescription = "A complex tool with nested schema"

    private val largeToolName = "large-tool"
    private val largeToolDescription = "A tool that returns a large response"
    private val largeToolContent = "X".repeat(100_000) // 100KB of data

    private val slowToolName = "slow-tool"
    private val slowToolDescription = "A tool that takes time to respond"

    private val specialCharsToolName = "special-chars-tool"
    private val specialCharsToolDescription = "A tool that handles special characters"
    private val specialCharsContent = "!@#$%^&*()_+{}|:\"<>?~`-=[]\\;',./\n\t"

    override fun configureServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(
            listChanged = true,
        ),
    )

    override fun configureServer() {
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
            val text = (request.arguments["text"] as? JsonPrimitive)?.content ?: "No text provided"

            CallToolResult(
                content = listOf(TextContent(text = "Echo: $text")),
                structuredContent = buildJsonObject {
                    put("result", text)
                },
            )
        }

        server.addTool(
            name = complexToolName,
            description = complexToolDescription,
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put(
                        "user",
                        buildJsonObject {
                            put("type", "object")
                            put("description", "User information")
                            put(
                                "properties",
                                buildJsonObject {
                                    put(
                                        "name",
                                        buildJsonObject {
                                            put("type", "string")
                                            put("description", "User's name")
                                        },
                                    )
                                    put(
                                        "age",
                                        buildJsonObject {
                                            put("type", "integer")
                                            put("description", "User's age")
                                        },
                                    )
                                    put(
                                        "address",
                                        buildJsonObject {
                                            put("type", "object")
                                            put("description", "User's address")
                                            put(
                                                "properties",
                                                buildJsonObject {
                                                    put(
                                                        "street",
                                                        buildJsonObject {
                                                            put("type", "string")
                                                        },
                                                    )
                                                    put(
                                                        "city",
                                                        buildJsonObject {
                                                            put("type", "string")
                                                        },
                                                    )
                                                    put(
                                                        "country",
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
                        },
                    )
                    put(
                        "options",
                        buildJsonObject {
                            put("type", "array")
                            put("description", "Additional options")
                            put(
                                "items",
                                buildJsonObject {
                                    put("type", "string")
                                },
                            )
                        },
                    )
                },
                required = listOf("user"),
            ),
        ) { request ->
            val user = request.arguments["user"] as? JsonObject
            val name = (user?.get("name") as? JsonPrimitive)?.content ?: "Unknown"
            val age = (user?.get("age") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0

            val address = user?.get("address") as? JsonObject
            val street = (address?.get("street") as? JsonPrimitive)?.content ?: "Unknown"
            val city = (address?.get("city") as? JsonPrimitive)?.content ?: "Unknown"
            val country = (address?.get("country") as? JsonPrimitive)?.content ?: "Unknown"

            val options = (request.arguments["options"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.content
            } ?: emptyList()

            val summary =
                "User: $name, Age: $age, Address: $street, $city, $country, Options: ${options.joinToString(", ")}"

            CallToolResult(
                content = listOf(TextContent(text = summary)),
                structuredContent = buildJsonObject {
                    put("name", name)
                    put("age", age)
                    put(
                        "address",
                        buildJsonObject {
                            put("street", street)
                            put("city", city)
                            put("country", country)
                        },
                    )
                    put(
                        "options",
                        buildJsonArray {
                            options.forEach { add(it) }
                        },
                    )
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
            val size = (request.arguments["size"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
            val content = largeToolContent.take(largeToolContent.length.coerceAtMost(size * 1000))

            CallToolResult(
                content = listOf(TextContent(text = content)),
                structuredContent = buildJsonObject {
                    put("size", content.length)
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
            val delay = (request.arguments["delay"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1000

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
            val special = (request.arguments["special"] as? JsonPrimitive)?.content ?: specialCharsContent

            CallToolResult(
                content = listOf(TextContent(text = "Received special characters: $special")),
                structuredContent = buildJsonObject {
                    put("special", special)
                    put("length", special.length)
                },
            )
        }
    }

    @Test
    fun testBasicTool() {
        runTest {
            val testText = "Hello, world!"
            val arguments = mapOf("text" to testText)

            val result = client.callTool(basicToolName, arguments) as CallToolResultBase

            val expectedToolResult = "[TextContent(text=Echo: Hello, world!, annotations=null)]"
            assertEquals(expectedToolResult, result.content.toString(), "Unexpected tool result")

            val actualContent = result.structuredContent.toString()
            val expectedContent = """
                {
                  "result" : "Hello, world!"
                }
            """.trimIndent()

            actualContent.shouldEqualJson(expectedContent)
        }
    }

    @Test
    fun testComplexNestedSchema() {
        runTest {
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
                    name = complexToolName,
                    arguments = arguments,
                ),
            ) as CallToolResultBase

            val actualContent = result.structuredContent.toString()
            val expectedContent = """
                {
                  "name" : "John Galt",
                  "age" : 30,
                  "address" : {
                    "street" : "123 Main St",
                    "city" : "New York",
                    "country" : "USA"
                  },
                  "options" : [ "option1", "option2", "option3" ]
                }
            """.trimIndent()

            actualContent.shouldEqualJson(expectedContent)
        }
    }

    @Test
    fun testLargeResponse() {
        runTest {
            val size = 10
            val arguments = mapOf("size" to size)

            val result = client.callTool(largeToolName, arguments) as CallToolResultBase

            val content = result.content.firstOrNull() as TextContent
            assertNotNull(content, "Tool result content should be TextContent")

            val actualContent = result.structuredContent.toString()
            val expectedContent = """
            {
              "size" : 10000
            }
        """.trimIndent()

            actualContent.shouldEqualJson(expectedContent)
        }
    }

    @Test
    fun testSlowTool() {
        runTest {
            val delay = 500
            val arguments = mapOf("delay" to delay)

            val startTime = System.currentTimeMillis()
            val result = client.callTool(slowToolName, arguments) as CallToolResultBase
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

            actualContent.shouldEqualJson(expectedContent)
        }
    }

    @Test
    fun testSpecialCharacters() {
        runTest {
            val arguments = mapOf("special" to specialCharsContent)

            val result = client.callTool(specialCharsToolName, arguments) as CallToolResultBase

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

            actualContent.shouldEqualJson(expectedContent)
        }
    }

    @Test
    fun testConcurrentToolCalls() = runTest {
        val concurrentCount = 10
        val results = mutableListOf<CallToolResultBase?>()

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

        val exception = assertThrows<IllegalStateException> {
            runBlocking {
                client.callTool(nonExistentToolName, arguments)
            }
        }

        val msg = exception.message ?: ""
        val expectedMessage = "JSONRPCError(code=InternalError, message=Tool not found: non-existent-tool, data={})"

        assertEquals(expectedMessage, msg, "Unexpected error message for non-existent tool")
    }
}
