package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerResourcesNotificationTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        resources = ServerCapabilities.Resources(true, null),
    )

    @Test
    fun `addResource should send notification`() = runTest {
        // Configure notification handler
        val notifications = mutableListOf<ResourceListChangedNotification>()
        client.setNotificationHandler<ResourceListChangedNotification>(
            Method.Defined.NotificationsResourcesListChanged,
        ) { notification ->
            notifications.add(notification)
            CompletableDeferred(Unit)
        }

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

        // Verify that the notification was sent
        await untilAsserted {
            assertTrue(notifications.isNotEmpty(), "Notification should be sent when resource is added")
        }
    }

    @Test
    fun `removeResources should remove multiple resources and send two notifications`() = runTest {
        // Configure notification handler
        val notifications = mutableListOf<ResourceListChangedNotification>()
        client.setNotificationHandler<ResourceListChangedNotification>(
            Method.Defined.NotificationsResourcesListChanged,
        ) {
            notifications.add(it)
            CompletableDeferred(Unit)
        }

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

        // Verify that the notifications were sent twice
        await untilAsserted {
            assertEquals(
                4,
                notifications.size,
                "Two notifications should be sent when resources are added and two when removed",
            )
        }
    }

    @Test
    fun `notification should not be send when removed resource does not exists`() = runTest {
        // Track notifications
        val notifications = mutableListOf<ResourceListChangedNotification>()
        client.setNotificationHandler<ResourceListChangedNotification>(
            Method.Defined.NotificationsResourcesListChanged,
        ) {
            notifications.add(it)
            CompletableDeferred(Unit)
        }

        // Try to remove a non-existent resource
        val result = server.removeResource("non-existent-resource")

        // Verify the result
        assertFalse(result, "Removing non-existent resource should return false")
        assertTrue(
            notifications.isEmpty(),
            "No notification should be sent when resource doesn't exist",
        )
    }
}
