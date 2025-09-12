package io.modelcontextprotocol.kotlin.sdk.integration.kotlin

import io.modelcontextprotocol.kotlin.sdk.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.Role
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PromptIntegrationTest : KotlinTestBase() {

    private val testPromptName = "greeting"
    private val testPromptDescription = "A simple greeting prompt"
    private val complexPromptName = "multimodal-prompt"
    private val complexPromptDescription = "A prompt with multiple content types"
    private val conversationPromptName = "conversation"
    private val conversationPromptDescription = "A prompt with multiple messages and roles"
    private val strictPromptName = "strict-prompt"
    private val strictPromptDescription = "A prompt with required arguments"

    override fun configureServerCapabilities(): ServerCapabilities = ServerCapabilities(
        prompts = ServerCapabilities.Prompts(
            listChanged = true,
        ),
    )

    override fun configureServer() {
        // simple prompt with a name parameter
        server.addPrompt(
            name = testPromptName,
            description = testPromptDescription,
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
                description = testPromptDescription,
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

        // prompt with multiple content types
        server.addPrompt(
            name = complexPromptName,
            description = complexPromptDescription,
            arguments = listOf(
                PromptArgument(
                    name = "topic",
                    description = "The topic to discuss",
                    required = false,
                ),
                PromptArgument(
                    name = "includeImage",
                    description = "Whether to include an image",
                    required = false,
                ),
            ),
        ) { request ->
            val topic = request.arguments?.get("topic") ?: "general knowledge"
            val includeImage = request.arguments?.get("includeImage")?.toBoolean() ?: true

            val messages = mutableListOf<PromptMessage>()

            messages.add(
                PromptMessage(
                    role = Role.user,
                    content = TextContent(text = "I'd like to discuss $topic."),
                ),
            )

            val assistantContents = mutableListOf<ContentBlock>()
            assistantContents.add(TextContent(text = "I'd be happy to discuss $topic with you."))

            if (includeImage) {
                assistantContents.add(
                    ImageContent(
                        data = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BmMIQAAAABJRU5ErkJggg==",
                        mimeType = "image/png",
                    ),
                )
            }

            messages.add(
                PromptMessage(
                    role = Role.assistant,
                    content = assistantContents[0],
                ),
            )

            GetPromptResult(
                description = complexPromptDescription,
                messages = messages,
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

        val testPrompt = result.prompts.find { it.name == testPromptName }
        assertNotNull(testPrompt, "Test prompt should be in the list")
        assertEquals(
            testPromptDescription,
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
                name = testPromptName,
                arguments = mapOf("name" to testName),
            ),
        )

        assertNotNull(result, "Get prompt result should not be null")
        assertEquals(
            testPromptDescription,
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
    fun testComplexContentTypes() = runBlocking(Dispatchers.IO) {
        val topic = "artificial intelligence"
        val result = client.getPrompt(
            GetPromptRequest(
                name = complexPromptName,
                arguments = mapOf(
                    "topic" to topic,
                    "includeImage" to "true",
                ),
            ),
        )

        assertNotNull(result, "Get prompt result should not be null")
        assertEquals(
            complexPromptDescription,
            result.description,
            "Prompt description should match",
        )

        assertTrue(result.messages.isNotEmpty(), "Prompt messages should not be empty")
        assertEquals(2, result.messages.size, "Prompt should have 2 messages")

        val userMessage = result.messages.find { it.role == Role.user }
        assertNotNull(userMessage, "User message should be in the list")
        val userContent = userMessage.content as? TextContent
        assertNotNull(userContent, "User message content should be TextContent")
        val userText2 = requireNotNull(userContent.text)
        assertTrue(userText2.contains(topic), "User message should contain the topic")

        val assistantMessage = result.messages.find { it.role == Role.assistant }
        assertNotNull(assistantMessage, "Assistant message should be in the list")
        val assistantContent = assistantMessage.content as? TextContent
        assertNotNull(assistantContent, "Assistant message content should be TextContent")
        val assistantText = requireNotNull(assistantContent.text)
        assertTrue(
            assistantText.contains(topic),
            "Assistant message should contain the topic",
        )

        val resultNoImage = client.getPrompt(
            GetPromptRequest(
                name = complexPromptName,
                arguments = mapOf(
                    "topic" to topic,
                    "includeImage" to "false",
                ),
            ),
        )

        assertNotNull(resultNoImage, "Get prompt result (no image) should not be null")
        assertEquals(2, resultNoImage.messages.size, "Prompt should have 2 messages")
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
}
