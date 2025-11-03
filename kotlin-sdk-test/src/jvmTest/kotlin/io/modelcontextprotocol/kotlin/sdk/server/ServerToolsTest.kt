package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.ToolListChangedNotification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerToolsTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(null),
    )

    @Test
    fun `removeTool should remove a tool`() = runTest {
        // Add a tool
        server.addTool("test-tool", "Test Tool", Tool.Input()) {
            CallToolResult(listOf(TextContent("Test result")))
        }

        // Remove the tool
        val result = server.removeTool("test-tool")

        // Verify the tool was removed
        assertTrue(result, "Tool should be removed successfully")
    }

    @Test
    fun `removeTool should return false when tool does not exist`() = runTest {
        // Track notifications
        var toolListChangedNotificationReceived = false
        client.setNotificationHandler<ToolListChangedNotification>(Method.Defined.NotificationsToolsListChanged) {
            toolListChangedNotificationReceived = true
            CompletableDeferred(Unit)
        }

        // Try to remove a non-existent tool
        val result = server.removeTool("non-existent-tool")

        // Verify the result
        assertFalse(result, "Removing non-existent tool should return false")
        assertFalse(toolListChangedNotificationReceived, "No notification should be sent when tool doesn't exist")
    }

    @Test
    fun `removeTool should throw when tools capability is not supported`() = runTest {
        // Create server without tools capability
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        // Verify that removing a tool throws an exception
        val exception = assertThrows<IllegalStateException> {
            server.removeTool("test-tool")
        }
        assertEquals("Server does not support tools capability.", exception.message)
    }

    @Test
    fun `removeTools should remove multiple tools`() = runTest {
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
    }
}
