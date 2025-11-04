package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.ResourceListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerResourcesTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        resources = ServerCapabilities.Resources(null, null),
    )

    @Test
    fun `removeResource should remove a resource and send notification`() = runTest {
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

        // Remove the resource
        val result = server.removeResource(testResourceUri)

        // Verify the resource was removed
        assertTrue(result, "Resource should be removed successfully")
    }

    @Test
    fun `removeResources should remove multiple resources and send notification`() = runTest {
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

        // Remove the resources
        val result = server.removeResources(listOf(testResourceUri1, testResourceUri2))

        // Verify the resources were removed
        assertEquals(2, result, "Both resources should be removed")
    }

    @Test
    fun `removeResource should return false when resource does not exist`() = runTest {
        // Track notifications
        var resourceListChangedNotificationReceived = false
        client.setNotificationHandler<ResourceListChangedNotification>(
            Method.Defined.NotificationsResourcesListChanged,
        ) {
            resourceListChangedNotificationReceived = true
            CompletableDeferred(Unit)
        }

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
