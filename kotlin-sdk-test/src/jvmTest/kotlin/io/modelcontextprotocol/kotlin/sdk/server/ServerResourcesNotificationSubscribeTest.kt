package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerResourcesNotificationSubscribeTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        resources = ServerCapabilities.Resources(null, true),
    )

    @Test
    fun `removeResource should send resource update notification`() = runTest {
        val notifications = mutableListOf<ResourceUpdatedNotification>()
        client.setNotificationHandler<ResourceUpdatedNotification>(Method.Defined.NotificationsResourcesUpdated) {
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

        client.subscribeResource(SubscribeRequest(SubscribeRequestParams(uri = testResourceUri1)))

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

        // Remove the resource
        val result = server.removeResource(testResourceUri1)

        // Verify the resource was removed
        assertTrue(result, "Resource should be removed successfully")

        // Verify that the notification was sent
        await untilAsserted {
            assertEquals(1, notifications.size, "Notification should be sent when resource 1 was deleted")
        }
        assertEquals(testResourceUri1, notifications[0].params.uri, "Notification should contain the resource 1 URI")
    }

    @Test
    fun `removeResource for two resources should send two separate notifications`() = runTest {
        val notifications = mutableListOf<ResourceUpdatedNotification>()
        client.setNotificationHandler<ResourceUpdatedNotification>(Method.Defined.NotificationsResourcesUpdated) {
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

        client.subscribeResource(SubscribeRequest(SubscribeRequestParams(uri = testResourceUri1)))
        client.subscribeResource(SubscribeRequest(SubscribeRequestParams(uri = testResourceUri2)))

        // Remove the resource
        val result1 = server.removeResource(testResourceUri1)
        val result2 = server.removeResource(testResourceUri2)

        // Verify the resource was removed
        assertTrue(result1, "Resource 1 should be removed successfully")
        assertTrue(result2, "Resource 2 should be removed successfully")

        println(notifications.map { it.params.uri })
        // Verify that the notification was sent
        await untilAsserted {
            assertEquals(
                2,
                notifications.size,
                "Notification should be sent when resource 1 and resource 2 was deleted",
            )
        }

        val deletedResources = listOf(notifications[0].params.uri, notifications[1].params.uri)
        assertTrue(
            deletedResources.contains(testResourceUri1),
            "Notification should contain the removed resource 1 URI",
        )
        assertTrue(
            deletedResources.contains(testResourceUri2),
            "Notification should contain the removed resource 2 URI",
        )
    }

    @Test
    fun `notification should not be send when removed resource does not exists`() = runTest {
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
}
