package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Prompt
import io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerPromptsTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        prompts = ServerCapabilities.Prompts(null),
    )

    @Test
    fun `removePrompt should remove a prompt and do not send notification`() = runTest {
        // Configure notification handler
        var promptListChangedNotificationReceived = false
        client.setNotificationHandler<PromptListChangedNotification>(Method.Defined.NotificationsPromptsListChanged) {
            promptListChangedNotificationReceived = true
            CompletableDeferred(Unit)
        }

        // Add a prompt
        val testPrompt = Prompt("test-prompt", "Test Prompt", null)
        server.addPrompt(testPrompt) {
            GetPromptResult(
                description = "Test prompt description",
                messages = listOf(),
            )
        }

        // Remove the prompt
        val result = server.removePrompt(testPrompt.name)

        // Verify the prompt was removed
        assertTrue(result, "Prompt should be removed successfully")

        // Verify that the notification was not sent
        assertFalse(
            promptListChangedNotificationReceived,
            "No notification should be sent when prompts capability is not supported",
        )
    }

    @Test
    fun `removePrompts should remove multiple prompts`() = runTest {
        // Configure notification handler
        var promptListChangedNotificationReceived = false
        client.setNotificationHandler<PromptListChangedNotification>(Method.Defined.NotificationsPromptsListChanged) {
            promptListChangedNotificationReceived = true
            CompletableDeferred(Unit)
        }

        // Add prompts
        val testPrompt1 = Prompt("test-prompt-1", "Test Prompt 1", null)
        val testPrompt2 = Prompt("test-prompt-2", "Test Prompt 2", null)
        client.setNotificationHandler<PromptListChangedNotification>(Method.Defined.NotificationsPromptsListChanged) {
            throw IllegalStateException("Notification should not be sent")
        }

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

        // Remove the prompts
        val result = server.removePrompts(listOf(testPrompt1.name, testPrompt2.name))

        // Verify the prompts were removed
        assertEquals(2, result, "Both prompts should be removed")

        // Verify that the notification was not sent
        assertFalse(
            promptListChangedNotificationReceived,
            "No notification should be sent when prompts capability is not supported",
        )
    }

    @Test
    fun `removePrompt should return false when prompt does not exist`() = runTest {
        // Track notifications
        var promptListChangedNotificationReceived = false
        client.setNotificationHandler<PromptListChangedNotification>(Method.Defined.NotificationsPromptsListChanged) {
            promptListChangedNotificationReceived = true
            CompletableDeferred(Unit)
        }

        // Try to remove a non-existent prompt
        val result = server.removePrompt("non-existent-prompt")

        // Verify the result
        assertFalse(result, "Removing non-existent prompt should return false")
        assertFalse(promptListChangedNotificationReceived, "No notification should be sent when prompt doesn't exist")
    }

    @Test
    fun `removePrompt should throw when prompts capability is not supported`() = runTest {
        var promptListChangedNotificationReceived = false
        client.setNotificationHandler<PromptListChangedNotification>(Method.Defined.NotificationsPromptsListChanged) {
            promptListChangedNotificationReceived = true
            CompletableDeferred(Unit)
        }

        // Create server without prompts capability
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        // Verify that removing a prompt throws an exception
        val exception = assertThrows<IllegalStateException> {
            server.removePrompt("test-prompt")
        }
        assertEquals("Server does not support prompts capability.", exception.message)

        // Verify that the notification was not sent
        assertFalse(
            promptListChangedNotificationReceived,
            "No notification should be sent when prompts capability is not supported",
        )
    }
}
