package integration.kotlin

import integration.utils.TestUtils.runTest
import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PromptEdgeCasesTest : KotlinTestBase() {

    override val port = 3008

    private val basicPromptName = "basic-prompt"
    private val basicPromptDescription = "A basic prompt for testing"

    private val complexPromptName = "complex-prompt"
    private val complexPromptDescription = "A complex prompt with many arguments"

    private val largePromptName = "large-prompt"
    private val largePromptDescription = "A very large prompt for testing"
    private val largePromptContent = "X".repeat(100_000) // 100KB of data

    private val specialCharsPromptName = "special-chars-prompt"
    private val specialCharsPromptDescription = "A prompt with special characters"
    private val specialCharsContent = "!@#$%^&*()_+{}|:\"<>?~`-=[]\\;',./\n\t"

    override fun configureServerCapabilities(): ServerCapabilities {
        return ServerCapabilities(
            prompts = ServerCapabilities.Prompts(
                listChanged = true
            )
        )
    }

    override fun configureServer() {
        server.addPrompt(
            name = basicPromptName,
            description = basicPromptDescription,
            arguments = listOf(
                PromptArgument(
                    name = "name",
                    description = "The name to greet",
                    required = true
                )
            )
        ) { request ->
            val name = request.arguments?.get("name") ?: "World"

            GetPromptResult(
                description = basicPromptDescription,
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = TextContent(text = "Hello, $name!")
                    ),
                    PromptMessage(
                        role = Role.assistant,
                        content = TextContent(text = "Greetings, $name! How can I assist you today?")
                    )
                )
            )
        }

        server.addPrompt(
            name = complexPromptName,
            description = complexPromptDescription,
            arguments = listOf(
                PromptArgument(name = "arg1", description = "Argument 1", required = true),
                PromptArgument(name = "arg2", description = "Argument 2", required = true),
                PromptArgument(name = "arg3", description = "Argument 3", required = true),
                PromptArgument(name = "arg4", description = "Argument 4", required = false),
                PromptArgument(name = "arg5", description = "Argument 5", required = false),
                PromptArgument(name = "arg6", description = "Argument 6", required = false),
                PromptArgument(name = "arg7", description = "Argument 7", required = false),
                PromptArgument(name = "arg8", description = "Argument 8", required = false),
                PromptArgument(name = "arg9", description = "Argument 9", required = false),
                PromptArgument(name = "arg10", description = "Argument 10", required = false)
            )
        ) { request ->
            // validate required arguments
            val requiredArgs = listOf("arg1", "arg2", "arg3")
            for (argName in requiredArgs) {
                if (request.arguments?.get(argName) == null) {
                    throw IllegalArgumentException("Missing required argument: $argName")
                }
            }

            val args = mutableMapOf<String, String>()
            for (i in 1..10) {
                val argName = "arg$i"
                val argValue = request.arguments?.get(argName)
                if (argValue != null) {
                    args[argName] = argValue
                }
            }

            GetPromptResult(
                description = complexPromptDescription,
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = TextContent(text = "Arguments: ${args.entries.joinToString { "${it.key}=${it.value}" }}")
                    ),
                    PromptMessage(
                        role = Role.assistant,
                        content = TextContent(text = "Received ${args.size} arguments")
                    )
                )
            )
        }

        // Very large prompt
        server.addPrompt(
            name = largePromptName,
            description = largePromptDescription,
            arguments = listOf(
                PromptArgument(
                    name = "size",
                    description = "Size multiplier",
                    required = false
                )
            )
        ) { request ->
            val size = request.arguments?.get("size")?.toIntOrNull() ?: 1
            val content = largePromptContent.repeat(size)

            GetPromptResult(
                description = largePromptDescription,
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = TextContent(text = "Generate a large response")
                    ),
                    PromptMessage(
                        role = Role.assistant,
                        content = TextContent(text = content)
                    )
                )
            )
        }

        server.addPrompt(
            name = specialCharsPromptName,
            description = specialCharsPromptDescription,
            arguments = listOf(
                PromptArgument(
                    name = "special",
                    description = "Special characters to include",
                    required = false
                )
            )
        ) { request ->
            val special = request.arguments?.get("special") ?: specialCharsContent

            GetPromptResult(
                description = specialCharsPromptDescription,
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = TextContent(text = "Special characters: $special")
                    ),
                    PromptMessage(
                        role = Role.assistant,
                        content = TextContent(text = "Received special characters: $special")
                    )
                )
            )
        }
    }

    @Test
    fun testBasicPrompt() {
        runTest {
            val testName = "Alice"
            val result = client.getPrompt(
                GetPromptRequest(
                    name = basicPromptName,
                    arguments = mapOf("name" to testName)
                )
            )

            assertNotNull(result, "Get prompt result should not be null")
            assertEquals(basicPromptDescription, result.description, "Prompt description should match")

            assertEquals(2, result.messages.size, "Prompt should have 2 messages")

            val userMessage = result.messages.find { it.role == Role.user }
            assertNotNull(userMessage, "User message should be in the list")
            val userContent = userMessage.content as? TextContent
            assertNotNull(userContent, "User message content should be TextContent")
            assertEquals("Hello, $testName!", userContent.text, "User message content should match")

            val assistantMessage = result.messages.find { it.role == Role.assistant }
            assertNotNull(assistantMessage, "Assistant message should be in the list")
            val assistantContent = assistantMessage.content as? TextContent
            assertNotNull(assistantContent, "Assistant message content should be TextContent")
            assertEquals(
                "Greetings, $testName! How can I assist you today?",
                assistantContent.text,
                "Assistant message content should match"
            )
        }
    }

    @Test
    fun testComplexPromptWithManyArguments() {
        runTest {
            val arguments = (1..10).associate { i -> "arg$i" to "value$i" }

            val result = client.getPrompt(
                GetPromptRequest(
                    name = complexPromptName,
                    arguments = arguments
                )
            )

            assertNotNull(result, "Get prompt result should not be null")
            assertEquals(complexPromptDescription, result.description, "Prompt description should match")

            assertEquals(2, result.messages.size, "Prompt should have 2 messages")

            val userMessage = result.messages.find { it.role == Role.user }
            assertNotNull(userMessage, "User message should be in the list")
            val userContent = userMessage.content as? TextContent
            assertNotNull(userContent, "User message content should be TextContent")

            // verify all arguments
            val text = userContent.text ?: ""
            for (i in 1..10) {
                assertTrue(text.contains("arg$i=value$i"), "Message should contain arg$i=value$i")
            }

            val assistantMessage = result.messages.find { it.role == Role.assistant }
            assertNotNull(assistantMessage, "Assistant message should be in the list")
            val assistantContent = assistantMessage.content as? TextContent
            assertNotNull(assistantContent, "Assistant message content should be TextContent")
            assertEquals(
                "Received 10 arguments",
                assistantContent.text,
                "Assistant message should indicate 10 arguments"
            )
        }
    }

    @Test
    fun testLargePrompt() {
        runTest {
            val result = client.getPrompt(
                GetPromptRequest(
                    name = largePromptName,
                    arguments = mapOf("size" to "1")
                )
            )

            assertNotNull(result, "Get prompt result should not be null")
            assertEquals(largePromptDescription, result.description, "Prompt description should match")

            assertEquals(2, result.messages.size, "Prompt should have 2 messages")

            val assistantMessage = result.messages.find { it.role == Role.assistant }
            assertNotNull(assistantMessage, "Assistant message should be in the list")
            val assistantContent = assistantMessage.content as? TextContent
            assertNotNull(assistantContent, "Assistant message content should be TextContent")
            val text = assistantContent.text ?: ""
            assertEquals(100_000, text.length, "Assistant message should be 100KB in size")
        }
    }

    @Test
    fun testSpecialCharacters() {
        runTest {
            val result = client.getPrompt(
                GetPromptRequest(
                    name = specialCharsPromptName,
                    arguments = mapOf("special" to specialCharsContent)
                )
            )

            assertNotNull(result, "Get prompt result should not be null")
            assertEquals(specialCharsPromptDescription, result.description, "Prompt description should match")

            assertEquals(2, result.messages.size, "Prompt should have 2 messages")

            val userMessage = result.messages.find { it.role == Role.user }
            assertNotNull(userMessage, "User message should be in the list")
            val userContent = userMessage.content as? TextContent
            assertNotNull(userContent, "User message content should be TextContent")
            val userText = userContent.text ?: ""
            assertTrue(userText.contains(specialCharsContent), "User message should contain special characters")

            val assistantMessage = result.messages.find { it.role == Role.assistant }
            assertNotNull(assistantMessage, "Assistant message should be in the list")
            val assistantContent = assistantMessage.content as? TextContent
            assertNotNull(assistantContent, "Assistant message content should be TextContent")
            val assistantText = assistantContent.text ?: ""
            assertTrue(
                assistantText.contains(specialCharsContent),
                "Assistant message should contain special characters"
            )
        }
    }

    @Test
    fun testMissingRequiredArguments() {
        runTest {
            val exception = assertThrows<Exception> {
                runBlocking {
                    client.getPrompt(
                        GetPromptRequest(
                            name = complexPromptName,
                            arguments = mapOf("arg4" to "value4", "arg5" to "value5")
                        )
                    )
                }
            }

            assertTrue(
                exception.message?.contains("arg1") == true ||
                        exception.message?.contains("arg2") == true ||
                        exception.message?.contains("arg3") == true ||
                        exception.message?.contains("required") == true,
                "Exception should mention missing required arguments"
            )
        }
    }

    @Test
    fun testConcurrentPromptRequests() {
        runTest {
            val concurrentCount = 10
            val results = mutableListOf<GetPromptResult?>()

            runBlocking {
                repeat(concurrentCount) { index ->
                    launch {
                        val promptName = when (index % 4) {
                            0 -> basicPromptName
                            1 -> complexPromptName
                            2 -> largePromptName
                            else -> specialCharsPromptName
                        }

                        val arguments = when (promptName) {
                            basicPromptName -> mapOf("name" to "User$index")
                            complexPromptName -> mapOf("arg1" to "v1", "arg2" to "v2", "arg3" to "v3")
                            largePromptName -> mapOf("size" to "1")
                            else -> mapOf("special" to "!@#$%^&*()")
                        }

                        val result = client.getPrompt(
                            GetPromptRequest(
                                name = promptName,
                                arguments = arguments
                            )
                        )

                        synchronized(results) {
                            results.add(result)
                        }
                    }
                }
            }

            assertEquals(concurrentCount, results.size, "All concurrent operations should complete")

            results.forEach { result ->
                assertNotNull(result, "Result should not be null")
                assertTrue(result.messages.isNotEmpty(), "Result messages should not be empty")
            }
        }
    }

    @Test
    fun testNonExistentPrompt() {
        runTest {
            val nonExistentPromptName = "non-existent-prompt"

            val exception = assertThrows<Exception> {
                runBlocking {
                    client.getPrompt(
                        GetPromptRequest(
                            name = nonExistentPromptName,
                            arguments = mapOf("name" to "Test")
                        )
                    )
                }
            }

            assertTrue(
                exception.message?.contains("not found") == true ||
                        exception.message?.contains("does not exist") == true ||
                        exception.message?.contains("unknown") == true ||
                        exception.message?.contains("error") == true,
                "Exception should indicate prompt not found"
            )
        }
    }
}