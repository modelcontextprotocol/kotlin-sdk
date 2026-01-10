package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.Prompt
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OldSchemaServerPromptsTest : OldSchemaAbstractServerFeaturesTest() {

    override fun getServerCapabilities(): io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities =
        ServerCapabilities(
            prompts = ServerCapabilities.Prompts(false),
        )

    @Test
    fun `Should list no prompts by default`() = runTest {
        client.listPrompts() shouldNotBeNull {
            prompts.shouldBeEmpty()
        }
    }

    @Test
    fun `Should add a prompt`() = runTest {
        // Add a prompt
        val testPrompt = Prompt(
            name = "test-prompt-with-custom-handler",
            description = "Test Prompt",
            arguments = null,
        )
        val expectedPromptResult = GetPromptResult(
            description = "Test prompt description",
            messages = listOf(),
        )

        server.addPrompt(testPrompt) {
            expectedPromptResult
        }

        client.getPrompt(
            GetPromptRequest(
                name = "test-prompt-with-custom-handler",
                arguments = null,
            ),
        ) shouldBe expectedPromptResult

        client.listPrompts() shouldNotBeNull {
            prompts shouldContainExactly listOf(testPrompt)
            nextCursor shouldBe null
            _meta shouldBe EmptyJsonObject
        }
    }

    @Test
    fun `Should remove a prompt`() = runTest {
        // given
        val testPrompt = Prompt(
            name = "test-prompt-to-remove",
            description = "Test Prompt",
            arguments = null,
        )
        server.addPrompt(testPrompt) {
            GetPromptResult(
                description = "Test prompt description",
                messages = listOf(),
            )
        }

        client.listPrompts() shouldNotBeNull {
            prompts shouldContain testPrompt
        }

        // when
        val result = server.removePrompt(testPrompt.name)

        // then
        assertTrue(result, "Prompt should be removed successfully")
        val mcpException = shouldThrow<McpException> {
            client.getPrompt(
                GetPromptRequest(
                    name = testPrompt.name,
                    arguments = null,
                ),
            )
        }
        mcpException shouldHaveMessage "MCP error -32603: Prompt not found: ${testPrompt.name}"

        client.listPrompts() shouldNotBeNull {
            prompts.firstOrNull { it.name == testPrompt.name } shouldBe null
        }
    }

    @Test
    fun `Should remove multiple prompts and send notification`() = runTest {
        // Add prompts
        val testPrompt1 = Prompt("test-prompt-1", "Test Prompt 1", null)
        val testPrompt2 = Prompt("test-prompt-2", "Test Prompt 2", null)
        server.addPrompt(testPrompt1) {
            GetPromptResult(
                description = "Test prompt description 1",
                messages = listOf(),
            )
        }
        server.addPrompt(testPrompt2) {
            GetPromptResult(
                description = "Test prompt description 2",
                messages = listOf(),
            )
        }

        client.listPrompts() shouldNotBeNull {
            prompts shouldHaveSize 2
        }
        // Remove the prompts
        val result = server.removePrompts(listOf(testPrompt1.name, testPrompt2.name))

        // Verify the prompts were removed
        assertEquals(2, result, "Both prompts should be removed")
        client.listPrompts() shouldNotBeNull {
            prompts.shouldBeEmpty()
        }
    }

    @Test
    fun `removePrompt should return false when prompt does not exist`() = runTest {
        // Track notifications
        var promptListChangedNotificationReceived = false
        client.setNotificationHandler<io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification>(
            Method.Defined.NotificationsPromptsListChanged,
        ) {
            promptListChangedNotificationReceived = true
            CompletableDeferred(Unit)
        }

        // Try to remove a non-existent prompt
        val result = server.removePrompt("non-existent-prompt")

        // Verify the result
        assertFalse(result, "Removing non-existent prompt should return false")
        assertFalse(promptListChangedNotificationReceived, "No notification should be sent when prompt doesn't exist")
    }

    @Nested
    inner class NoPromptsCapabilitiesTests {
        // Create server without prompts capability
        val serverWithoutPrompts = Server(
            Implementation(name = "test server", version = "1.0"),
            ServerOptions(
                capabilities = ServerCapabilities(),
            ),
        )

        @Test
        fun `RemovePrompt should throw when prompts capability is not supported`() = runTest {
            // Verify that removing a prompt throws an exception
            val exception = assertThrows<IllegalStateException> {
                serverWithoutPrompts.removePrompt("test-prompt")
            }
            assertEquals("Server does not support prompts capability.", exception.message)
        }

        @Test
        fun `Remove Prompts should throw when prompts capability is not supported`() = runTest {
            // Verify that removing a prompt throws an exception
            val exception = assertThrows<IllegalStateException> {
                serverWithoutPrompts.removePrompts(emptyList())
            }
            assertEquals("Server does not support prompts capability.", exception.message)
        }

        @Test
        fun `Add Prompt should throw when prompts capability is not supported`() = runTest {
            // Verify that removing a prompt throws an exception
            val exception = assertThrows<IllegalStateException> {
                serverWithoutPrompts.addPrompt(name = "test-prompt") {
                    GetPromptResult(
                        description = "Test prompt description",
                        messages = listOf(),
                    )
                }
            }
            assertEquals("Server does not support prompts capability.", exception.message)
        }

        @Test
        fun `Add Prompts should throw when prompts capability is not supported`() = runTest {
            // Verify that removing a prompt throws an exception
            val exception = assertThrows<IllegalStateException> {
                serverWithoutPrompts.addPrompts(emptyList())
            }
            assertEquals("Server does not support prompts capability.", exception.message)
        }
    }
}
