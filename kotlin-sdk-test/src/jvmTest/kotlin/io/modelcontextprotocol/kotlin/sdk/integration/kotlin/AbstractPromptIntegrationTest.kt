package io.modelcontextprotocol.kotlin.sdk.integration.kotlin

import io.modelcontextprotocol.kotlin.sdk.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.Role
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class AbstractPromptIntegrationTest : KotlinTestBase() {

    private val basicPromptName = "basic-prompt"
    private val basicPromptDescription = "A basic prompt for testing"

    private val complexPromptName = "multimodal-prompt"
    private val complexPromptDescription = "A prompt with multiple content types"
    private val conversationPromptName = "conversation"
    private val conversationPromptDescription = "A prompt with multiple messages and roles"
    private val strictPromptName = "strict-prompt"
    private val strictPromptDescription = "A prompt with required arguments"

    private val largePromptName = "large-prompt"
    private val largePromptDescription = "A very large prompt for testing"
    private val largePromptContent = "X".repeat(100_000) // 100KB of data

    private val specialCharsPromptName = "special-chars-prompt"
    private val specialCharsPromptDescription = "A prompt with special characters"
    private val specialCharsContent = "!@#$%^&*()_+{}|:\"<>?~`-=[]\\;',./\n\t"

    override fun configureServerCapabilities(): ServerCapabilities = ServerCapabilities(
        prompts = ServerCapabilities.Prompts(
            listChanged = true,
        ),
    )

    override fun configureServer() {
        // basic prompt with a name parameter
        server.addPrompt(
            name = basicPromptName,
            description = basicPromptDescription,
            arguments = listOf(
                PromptArgument(
                    name = "name",
                    description = "The name to greet",
                    required = true,
                ),
            ),
        ) { request ->
            val name = request.arguments?.get("name") ?: "World"

            GetPromptResult(
                description = basicPromptDescription,
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = TextContent(text = "Hello, $name!"),
                    ),
                    PromptMessage(
                        role = Role.assistant,
                        content = TextContent(text = "Greetings, $name! How can I assist you today?"),
                    ),
                ),
            )
        }

        // special chars prompt
        server.addPrompt(
            name = specialCharsPromptName,
            description = specialCharsPromptDescription,
            arguments = listOf(
                PromptArgument(
                    name = "special",
                    description = "Special characters to include",
                    required = false,
                ),
            ),
        ) { request ->
            val special = request.arguments?.get("special") ?: specialCharsContent

            GetPromptResult(
                description = specialCharsPromptDescription,
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = TextContent(text = "Special characters: $special"),
                    ),
                    PromptMessage(
                        role = Role.assistant,
                        content = TextContent(text = "Received special characters: $special"),
                    ),
                ),
            )
        }

        // very large prompt
        server.addPrompt(
            name = largePromptName,
            description = largePromptDescription,
            arguments = listOf(
                PromptArgument(
                    name = "size",
                    description = "Size multiplier",
                    required = false,
                ),
            ),
        ) { request ->
            val size = request.arguments?.get("size")?.toIntOrNull() ?: 1
            val content = largePromptContent.repeat(size)

            GetPromptResult(
                description = largePromptDescription,
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = TextContent(text = "Generate a large response"),
                    ),
                    PromptMessage(
                        role = Role.assistant,
                        content = TextContent(text = content),
                    ),
                ),
            )
        }

        // complext prompt
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
                PromptArgument(name = "arg10", description = "Argument 10", required = false),
            ),
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
                        content = TextContent(
                            text = "Arguments: ${
                                args.entries.joinToString {
                                    "${it.key}=${it.value}"
                                }
                            }",
                        ),
                    ),
                    PromptMessage(
                        role = Role.assistant,
                        content = TextContent(text = "Received ${args.size} arguments"),
                    ),
                ),
            )
        }

        // prompt with multiple messages and roles
        server.addPrompt(
            name = conversationPromptName,
            description = conversationPromptDescription,
            arguments = listOf(
                PromptArgument(
                    name = "topic",
                    description = "The topic of the conversation",
                    required = false,
                ),
            ),
        ) { request ->
            val topic = request.arguments?.get("topic") ?: "weather"

            GetPromptResult(
                description = conversationPromptDescription,
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = TextContent(text = "Let's talk about the $topic."),
                    ),
                    PromptMessage(
                        role = Role.assistant,
                        content = TextContent(
                            text = "Sure, I'd love to discuss the $topic. What would you like to know?",
                        ),
                    ),
                    PromptMessage(
                        role = Role.user,
                        content = TextContent(text = "What's your opinion on the $topic?"),
                    ),
                    PromptMessage(
                        role = Role.assistant,
                        content = TextContent(
                            text = "As an AI, I don't have personal opinions," +
                                " but I can provide information about $topic.",
                        ),
                    ),
                    PromptMessage(
                        role = Role.user,
                        content = TextContent(text = "That's helpful, thank you!"),
                    ),
                    PromptMessage(
                        role = Role.assistant,
                        content = TextContent(
                            text = "You're welcome! Let me know if you have more questions about $topic.",
                        ),
                    ),
                ),
            )
        }

        // prompt with strict required arguments
        server.addPrompt(
            name = strictPromptName,
            description = strictPromptDescription,
            arguments = listOf(
                PromptArgument(
                    name = "requiredArg1",
                    description = "First required argument",
                    required = true,
                ),
                PromptArgument(
                    name = "requiredArg2",
                    description = "Second required argument",
                    required = true,
                ),
                PromptArgument(
                    name = "optionalArg",
                    description = "Optional argument",
                    required = false,
                ),
            ),
        ) { request ->
            val args = request.arguments ?: emptyMap()
            val arg1 = args["requiredArg1"] ?: throw IllegalArgumentException(
                "Missing required argument: requiredArg1",
            )
            val arg2 = args["requiredArg2"] ?: throw IllegalArgumentException(
                "Missing required argument: requiredArg2",
            )
            val optArg = args["optionalArg"] ?: "default"

            GetPromptResult(
                description = strictPromptDescription,
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = TextContent(text = "Required arguments: $arg1, $arg2. Optional: $optArg"),
                    ),
                    PromptMessage(
                        role = Role.assistant,
                        content = TextContent(text = "I received your arguments: $arg1, $arg2, and $optArg"),
                    ),
                ),
            )
        }
    }

    @Test
    fun testListPrompts() = runBlocking(Dispatchers.IO) {
        val result = client.listPrompts()

        assertNotNull(result, "List prompts result should not be null")
        assertTrue(result.prompts.isNotEmpty(), "Prompts list should not be empty")

        val testPrompt = result.prompts.find { it.name == basicPromptName }
        assertNotNull(testPrompt, "Test prompt should be in the list")
        assertEquals(
            basicPromptDescription,
            testPrompt.description,
            "Prompt description should match",
        )

        val arguments = testPrompt.arguments ?: error("Prompt arguments should not be null")
        assertTrue(arguments.isNotEmpty(), "Prompt arguments should not be empty")

        val nameArg = arguments.find { it.name == "name" }
        assertNotNull(nameArg, "Name argument should be in the list")
        assertEquals(
            "The name to greet",
            nameArg.description,
            "Argument description should match",
        )
        assertEquals(true, nameArg.required, "Argument required flag should match")
    }

    @Test
    fun testGetPrompt() = runBlocking(Dispatchers.IO) {
        val testName = "Alice"
        val result = client.getPrompt(
            GetPromptRequest(
                name = basicPromptName,
                arguments = mapOf("name" to testName),
            ),
        )

        assertNotNull(result, "Get prompt result should not be null")
        assertEquals(
            basicPromptDescription,
            result.description,
            "Prompt description should match",
        )

        assertTrue(result.messages.isNotEmpty(), "Prompt messages should not be empty")
        assertEquals(2, result.messages.size, "Prompt should have 2 messages")

        val userMessage = result.messages.find { it.role == Role.user }
        assertNotNull(userMessage, "User message should be in the list")
        val userContent = userMessage.content as? TextContent
        assertNotNull(userContent, "User message content should be TextContent")
        assertNotNull(userContent.text, "User message text should not be null")
        assertEquals(
            "Hello, $testName!",
            userContent.text,
            "User message content should match",
        )

        val assistantMessage = result.messages.find { it.role == Role.assistant }
        assertNotNull(assistantMessage, "Assistant message should be in the list")
        val assistantContent = assistantMessage.content as? TextContent
        assertNotNull(assistantContent, "Assistant message content should be TextContent")
        assertNotNull(assistantContent.text, "Assistant message text should not be null")
        assertEquals(
            "Greetings, $testName! How can I assist you today?",
            assistantContent.text,
            "Assistant message content should match",
        )
    }

    @Test
    fun testMissingRequiredArguments() = runBlocking(Dispatchers.IO) {
        val promptsList = client.listPrompts()
        assertNotNull(promptsList, "Prompts list should not be null")
        val strictPrompt = promptsList.prompts.find { it.name == strictPromptName }
        assertNotNull(strictPrompt, "Strict prompt should be in the list")

        val argsDef = strictPrompt.arguments ?: error("Prompt arguments should not be null")
        val requiredArgs = argsDef.filter { it.required == true }
        assertEquals(
            2,
            requiredArgs.size,
            "Strict prompt should have 2 required arguments",
        )

        // test missing required arg
        val exception = assertThrows<IllegalStateException> {
            runBlocking {
                client.getPrompt(
                    GetPromptRequest(
                        name = strictPromptName,
                        arguments = mapOf("requiredArg1" to "value1"),
                    ),
                )
            }
        }

        assertEquals(
            true,
            exception.message?.contains("requiredArg2"),
            "Exception should mention the missing argument",
        )

        // test with no args
        val exception2 = assertThrows<IllegalStateException> {
            runBlocking {
                client.getPrompt(
                    GetPromptRequest(
                        name = strictPromptName,
                        arguments = emptyMap(),
                    ),
                )
            }
        }

        assertEquals(
            exception2.message?.contains("requiredArg"),
            true,
            "Exception should mention a missing required argument",
        )

        // test with all required args
        val result = client.getPrompt(
            GetPromptRequest(
                name = strictPromptName,
                arguments = mapOf(
                    "requiredArg1" to "value1",
                    "requiredArg2" to "value2",
                ),
            ),
        )

        assertNotNull(result, "Get prompt result should not be null")
        assertEquals(2, result.messages.size, "Prompt should have 2 messages")

        val userMessage = result.messages.find { it.role == Role.user }
        assertNotNull(userMessage, "User message should be in the list")
        val userContent = userMessage.content as? TextContent
        assertNotNull(userContent, "User message content should be TextContent")
        val userText = requireNotNull(userContent.text)
        assertTrue(userText.contains("value1"), "Message should contain first argument")
        assertTrue(userText.contains("value2"), "Message should contain second argument")
    }

    @Test
    fun testMultipleMessagesAndRoles() = runBlocking(Dispatchers.IO) {
        val topic = "climate change"
        val result = client.getPrompt(
            GetPromptRequest(
                name = conversationPromptName,
                arguments = mapOf("topic" to topic),
            ),
        )

        assertNotNull(result, "Get prompt result should not be null")
        assertEquals(
            conversationPromptDescription,
            result.description,
            "Prompt description should match",
        )

        assertTrue(result.messages.isNotEmpty(), "Prompt messages should not be empty")
        assertEquals(6, result.messages.size, "Prompt should have 6 messages")

        val userMessages = result.messages.filter { it.role == Role.user }
        val assistantMessages = result.messages.filter { it.role == Role.assistant }

        assertEquals(3, userMessages.size, "Should have 3 user messages")
        assertEquals(3, assistantMessages.size, "Should have 3 assistant messages")

        for (i in 0 until result.messages.size) {
            val expectedRole = if (i % 2 == 0) Role.user else Role.assistant
            assertEquals(
                expectedRole,
                result.messages[i].role,
                "Message $i should have role $expectedRole",
            )
        }

        for (message in result.messages) {
            val content = message.content as? TextContent
            assertNotNull(content, "Message content should be TextContent")
            val text = requireNotNull(content.text)

            // Either the message contains the topic or it's a generic conversation message
            val containsTopic = text.contains(topic)
            val isGenericMessage = text.contains("thank you") || text.contains("welcome")

            assertTrue(
                containsTopic || isGenericMessage,
                "Message should either contain the topic or be a generic conversation message",
            )
        }
    }

    @Test
    fun testBasicPrompt() = runBlocking(Dispatchers.IO) {
        val testName = "Alice"
        val result = client.getPrompt(
            GetPromptRequest(
                name = basicPromptName,
                arguments = mapOf("name" to testName),
            ),
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
            "Assistant message content should match",
        )
    }

    @Test
    fun testComplexPromptWithManyArguments() = runBlocking(Dispatchers.IO) {
        val arguments = (1..10).associate { i -> "arg$i" to "value$i" }

        val result = client.getPrompt(
            GetPromptRequest(
                name = complexPromptName,
                arguments = arguments,
            ),
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
            "Assistant message should indicate 10 arguments",
        )
    }

    @Test
    fun testLargePrompt() = runBlocking(Dispatchers.IO) {
        val result = client.getPrompt(
            GetPromptRequest(
                name = largePromptName,
                arguments = mapOf("size" to "1"),
            ),
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

    @Test
    fun testSpecialCharacters() = runBlocking(Dispatchers.IO) {
        val result = client.getPrompt(
            GetPromptRequest(
                name = specialCharsPromptName,
                arguments = mapOf("special" to specialCharsContent),
            ),
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
            "Assistant message should contain special characters",
        )
    }

    @Test
    fun testConcurrentPromptRequests() = runTest {
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
                            arguments = arguments,
                        ),
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

    @Test
    fun testNonExistentPrompt() = runTest {
        val nonExistentPromptName = "non-existent-prompt"

        val exception = assertThrows<IllegalStateException> {
            runBlocking {
                client.getPrompt(
                    GetPromptRequest(
                        name = nonExistentPromptName,
                        arguments = mapOf("name" to "Test"),
                    ),
                )
            }
        }

        val msg = exception.message ?: ""
        val expectedMessage = "JSONRPCError(code=InternalError, message=Prompt not found: non-existent-prompt, data={})"

        assertEquals(expectedMessage, msg, "Unexpected error message for non-existent prompt")
    }
}
