package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.Prompt
import io.modelcontextprotocol.kotlin.sdk.PromptListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.ResourceListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.ToolListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.shared.InMemoryTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerTest {

    @Test
    fun `removeTool should remove a tool`() = runTest {
        // Create server with tools capability
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(null),
            ),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        // Add a tool
        server.addTool("test-tool", "Test Tool", Tool.Input()) {
            CallToolResult(listOf(TextContent("Test result")))
        }

        // Setup client
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
        )

        // Connect client and server
        launch { client.connect(clientTransport) }
        launch { server.connect(serverTransport) }

        // Remove the tool
        val result = server.removeTool("test-tool")

        // Verify the tool was removed
        assertTrue(result, "Tool should be removed successfully")
    }

    @Test
    fun `removeTool should return false when tool does not exist`() = runTest {
        // Create server with tools capability
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(null),
            ),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        // Setup client
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
        )

        // Track notifications
        var toolListChangedNotificationReceived = false
        client.setNotificationHandler<ToolListChangedNotification>(Method.Defined.NotificationsToolsListChanged) {
            toolListChangedNotificationReceived = true
            CompletableDeferred(Unit)
        }

        // Connect client and server
        launch { client.connect(clientTransport) }
        launch { server.connect(serverTransport) }

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
        // Create server with tools capability
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(null),
            ),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        // Add tools
        server.addTool("test-tool-1", "Test Tool 1") {
            CallToolResult(listOf(TextContent("Test result 1")))
        }
        server.addTool("test-tool-2", "Test Tool 2") {
            CallToolResult(listOf(TextContent("Test result 2")))
        }

        // Setup client
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
        )

        // Connect client and server
        launch { client.connect(clientTransport) }
        launch { server.connect(serverTransport) }

        // Remove the tools
        val result = server.removeTools(listOf("test-tool-1", "test-tool-2"))

        // Verify the tools were removed
        assertEquals(2, result, "Both tools should be removed")
    }

    @Test
    fun `removePrompt should remove a prompt`() = runTest {
        // Create server with prompts capability
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = false),
            ),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        // Add a prompt
        val testPrompt = Prompt("test-prompt", "Test Prompt", null)
        server.addPrompt(testPrompt) {
            GetPromptResult(
                description = "Test prompt description",
                messages = listOf(),
            )
        }

        // Setup client
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
        )

        // Connect client and server
        launch { client.connect(clientTransport) }
        launch { server.connect(serverTransport) }

        // Remove the prompt
        val result = server.removePrompt(testPrompt.name)

        // Verify the prompt was removed
        assertTrue(result, "Prompt should be removed successfully")
    }

    @Test
    fun `removePrompts should remove multiple prompts and send notification`() = runTest {
        // Create server with prompts capability
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = false),
            ),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

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

        // Setup client
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
        )

        // Connect client and server
        launch { client.connect(clientTransport) }
        launch { server.connect(serverTransport) }

        // Remove the prompts
        val result = server.removePrompts(listOf(testPrompt1.name, testPrompt2.name))

        // Verify the prompts were removed
        assertEquals(2, result, "Both prompts should be removed")
    }

    @Test
    fun `removeResource should remove a resource and send notification`() = runTest {
        // Create server with resources capability
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(
                resources = ServerCapabilities.Resources(null, null),
            ),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        // Add a resource
        val testResourceUri = "test://resource"
        server.addResource(
            uri = testResourceUri,
            name = "Test Resource",
            description = "A test resource",
            mimeType = "text/plain",
        ) {
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = "Test resource content",
                        uri = testResourceUri,
                        mimeType = "text/plain",
                    ),
                ),
            )
        }

        // Setup client
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
        )

        // Connect client and server
        launch { client.connect(clientTransport) }
        launch { server.connect(serverTransport) }

        // Remove the resource
        val result = server.removeResource(testResourceUri)

        // Verify the resource was removed
        assertTrue(result, "Resource should be removed successfully")
    }

    @Test
    fun `removeResources should remove multiple resources and send notification`() = runTest {
        // Create server with resources capability
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(
                resources = ServerCapabilities.Resources(null, null),
            ),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        // Add resources
        val testResourceUri1 = "test://resource1"
        val testResourceUri2 = "test://resource2"
        server.addResource(
            uri = testResourceUri1,
            name = "Test Resource 1",
            description = "A test resource 1",
            mimeType = "text/plain",
        ) {
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = "Test resource content 1",
                        uri = testResourceUri1,
                        mimeType = "text/plain",
                    ),
                ),
            )
        }
        server.addResource(
            uri = testResourceUri2,
            name = "Test Resource 2",
            description = "A test resource 2",
            mimeType = "text/plain",
        ) {
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = "Test resource content 2",
                        uri = testResourceUri2,
                        mimeType = "text/plain",
                    ),
                ),
            )
        }

        // Setup client
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
        )

        // Connect client and server
        launch { client.connect(clientTransport) }
        launch { server.connect(serverTransport) }

        // Remove the resources
        val result = server.removeResources(listOf(testResourceUri1, testResourceUri2))

        // Verify the resources were removed
        assertEquals(2, result, "Both resources should be removed")
    }

    @Test
    fun `removePrompt should return false when prompt does not exist`() = runTest {
        // Create server with prompts capability
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = false),
            ),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        // Setup client
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
        )

        // Track notifications
        var promptListChangedNotificationReceived = false
        client.setNotificationHandler<PromptListChangedNotification>(Method.Defined.NotificationsPromptsListChanged) {
            promptListChangedNotificationReceived = true
            CompletableDeferred(Unit)
        }

        // Connect client and server
        launch { client.connect(clientTransport) }
        launch { server.connect(serverTransport) }

        // Try to remove a non-existent prompt
        val result = server.removePrompt("non-existent-prompt")

        // Verify the result
        assertFalse(result, "Removing non-existent prompt should return false")
        assertFalse(promptListChangedNotificationReceived, "No notification should be sent when prompt doesn't exist")
    }

    @Test
    fun `removePrompt should throw when prompts capability is not supported`() = runTest {
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
    }

    @Test
    fun `removeResource should return false when resource does not exist`() = runTest {
        // Create server with resources capability
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(
                resources = ServerCapabilities.Resources(null, null),
            ),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        // Setup client
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
        )

        // Track notifications
        var resourceListChangedNotificationReceived = false
        client.setNotificationHandler<ResourceListChangedNotification>(
            Method.Defined.NotificationsResourcesListChanged,
        ) {
            resourceListChangedNotificationReceived = true
            CompletableDeferred(Unit)
        }

        // Connect client and server
        launch { client.connect(clientTransport) }
        launch { server.connect(serverTransport) }

        // Try to remove a non-existent resource
        val result = server.removeResource("non-existent-resource")

        // Verify the result
        assertFalse(result, "Removing non-existent resource should return false")
        assertFalse(
            resourceListChangedNotificationReceived,
            "No notification should be sent when resource doesn't exist",
        )
    }

    @Test
    fun `removeResource should throw when resources capability is not supported`() = runTest {
        // Create server without resources capability
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        // Verify that removing a resource throws an exception
        val exception = assertThrows<IllegalStateException> {
            server.removeResource("test://resource")
        }
        assertEquals("Server does not support resources capability.", exception.message)
    }
}
