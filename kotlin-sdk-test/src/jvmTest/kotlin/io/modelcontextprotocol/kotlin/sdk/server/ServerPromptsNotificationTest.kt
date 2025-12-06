package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Prompt
import io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerPromptsNotificationTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        prompts = ServerCapabilities.Prompts(true),
    )

    @Test
    fun `addPrompt should send notification`() = runTest {
        // Configure notification handler
        val notifications = mutableListOf<PromptListChangedNotification>()
        client.setNotificationHandler<PromptListChangedNotification>(Method.Defined.NotificationsPromptsListChanged) {
            notifications.add(it)
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

        // Verify that the notification was sent
        await untilAsserted {
            assertTrue(notifications.isNotEmpty(), "Notification should be sent when prompt is added")
        }
    }

    @Test
    fun `removePrompts should remove multiple prompts and send two notifications`() = runTest {
        // Configure notification handler
        val notifications = mutableListOf<PromptListChangedNotification>()
        client.setNotificationHandler<PromptListChangedNotification>(Method.Defined.NotificationsPromptsListChanged) {
            notifications.add(it)
            CompletableDeferred(Unit)
        }

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

        // Remove the prompts
        val result = server.removePrompts(listOf(testPrompt1.name, testPrompt2.name))

        // Verify the prompts were removed
        assertEquals(2, result, "Both prompts should be removed")

        // Verify that the notifications were sent twice
        await untilAsserted {
            assertEquals(
                4,
                notifications.size,
                "Two notifications should be sent when prompts are added and two when removed",
            )
        }
    }

    @Test
    fun `notification should not be send when removed prompt does not exists`() = runTest {
        // Track notifications
        val notifications = mutableListOf<PromptListChangedNotification>()
        client.setNotificationHandler<PromptListChangedNotification>(Method.Defined.NotificationsPromptsListChanged) {
            notifications.add(it)
            CompletableDeferred(Unit)
        }

        // Try to remove a non-existent prompt
        val result = server.removePrompt("non-existent-prompt")

        // Verify the result
        assertFalse(result, "Removing non-existent prompt should return false")
        await untilAsserted {
            assertTrue(
                notifications.isEmpty(),
                "No notification should be sent when prompt doesn't exist",
            )
        }
    }
}
