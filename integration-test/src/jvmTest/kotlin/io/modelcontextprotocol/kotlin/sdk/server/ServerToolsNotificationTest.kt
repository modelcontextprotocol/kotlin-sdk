package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerToolsNotificationTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(true),
    )

    @Test
    fun `addTool should send notification`() = runTest {
        // Configure notification handler
        val notifications = mutableListOf<ToolListChangedNotification>()
        client.setNotificationHandler<ToolListChangedNotification>(Method.Defined.NotificationsToolsListChanged) {
            notifications.add(it)
            CompletableDeferred(Unit)
        }

        // Add a tool
        server.addTool("test-tool", "Test Tool", ToolSchema()) {
            CallToolResult(listOf(TextContent("Test result")))
        }

        // Remove the tool
        val result = server.removeTool("test-tool")

        // Verify the tool was removed
        assertTrue(result, "Tool should be removed successfully")

        // Verify that the notification was sent
        await untilAsserted {
            assertTrue(notifications.isNotEmpty(), "Notification should be sent when tool is added")
        }
    }

    @Test
    fun `removeTools should remove multiple tools and send two notifications`() = runTest {
        // Configure notification handler
        val notifications = mutableListOf<ToolListChangedNotification>()
        client.setNotificationHandler<ToolListChangedNotification>(
            Method.Defined.NotificationsToolsListChanged,
        ) { notification ->
            notifications.add(notification)
            CompletableDeferred(Unit)
        }

        // Add tools
        server.addTool("test-tool-1", "Test Tool 1") {
            CallToolResult(listOf(TextContent("Test result 1")))
        }
        server.addTool("test-tool-2", "Test Tool 2") {
            CallToolResult(listOf(TextContent("Test result 2")))
        }

        // Remove the tools
        val result = server.removeTools(listOf("test-tool-1", "test-tool-2"))
        // Verify the tools were removed
        assertEquals(2, result, "Both tools should be removed")

        // Verify that the notifications were sent twice
        await untilAsserted {
            assertEquals(
                4,
                notifications.size,
                "Two notifications should be sent when tools are added and two when removed",
            )
        }
    }

    @Test
    fun `notification should not be send when removed tool does not exists`() = runTest {
        // Track notifications
        val notifications = mutableListOf<ToolListChangedNotification>()
        client.setNotificationHandler<ToolListChangedNotification>(
            Method.Defined.NotificationsToolsListChanged,
        ) { notification ->
            notifications.add(notification)
            CompletableDeferred(Unit)
        }

        // Try to remove a non-existent tool
        val result = server.removeTool("non-existent-tool")
        // Close the server to stop processing further events and flush notifications
        server.close()

        // Verify the result
        assertFalse(result, "Removing non-existent tool should return false")
        assertTrue(notifications.isEmpty(), "No notification should be sent when tool doesn't exist")
    }
}
