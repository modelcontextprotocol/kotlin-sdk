package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsResourcesUpdated
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ServerResourcesNotificationSubscribeTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        resources = ServerCapabilities.Resources(null, true),
    )

    @Test
    fun `should send resource notifications`() = runBlocking {
        val notifications = ConcurrentLinkedQueue<ResourceUpdatedNotification>()

        client.setNotificationHandler<ResourceUpdatedNotification>(NotificationsResourcesUpdated) {
            notifications.add(it)
            CompletableDeferred(Unit)
        }
        // Add resources
        val testResourceUri1 = "test://resource1-${Uuid.random()}"
        val testResourceUri2 = "test://resource2-${Uuid.random()}"

        client.subscribeResource(SubscribeRequest(SubscribeRequestParams(uri = testResourceUri1)))
        client.subscribeResource(SubscribeRequest(SubscribeRequestParams(uri = testResourceUri2)))

        server.addResource(
            uri = testResourceUri1,
            name = "Test Resource 1",
            description = "A test resource 1",
            mimeType = "text/plain",
        ) { request ->
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = "Test resource content 1",
                        uri = request.uri,
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
        ) { request ->
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = "Test resource content 2",
                        uri = request.uri,
                        mimeType = "text/plain",
                    ),
                ),
            )
        }

        // Verify that the notification was sent
        await untilAsserted {
            notifications.shouldHaveSize(2)
            assertTrue(
                notifications.any { it.params.uri == testResourceUri1 && it.method == NotificationsResourcesUpdated },
                "Notification should be sent when resource 1 was deleted",
            )
            assertTrue(
                notifications.any { it.params.uri == testResourceUri2 && it.method == NotificationsResourcesUpdated },
                "Notification should be sent when resource 2 was deleted",
            )
        }

        notifications.clear()

        // Remove the resource
        val result1 = server.removeResource(testResourceUri1)
        assertTrue(result1, "Resource 1 should be removed successfully")

        val result2 = server.removeResource(testResourceUri2)
        // Verify the resource was removed
        assertTrue(result2, "Resource 2 should be removed successfully")

        // Verify that the notification was sent
        await untilAsserted {
            notifications.shouldHaveSize(2)
            assertTrue(
                notifications.any { it.params.uri == testResourceUri1 && it.method == NotificationsResourcesUpdated },
                "Notification should be sent when resource 1 was deleted",
            )
            assertTrue(
                notifications.any { it.params.uri == testResourceUri2 && it.method == NotificationsResourcesUpdated },
                "Notification should be sent when resource 2 was deleted",
            )
        }
    }

    @Test
    fun `notification should not be send when removed resource does not exists`(): Unit = runBlocking {
        println("Thread ${Thread.currentThread().name} test 1")
        // Track notifications
        val notifications = ConcurrentLinkedQueue<ResourceUpdatedNotification>()
        client.setNotificationHandler<ResourceUpdatedNotification>(NotificationsResourcesUpdated) { notification ->
            notifications.add(notification)
            CompletableDeferred(Unit)
        }

        // Try to remove a non-existent resource
        val result = server.removeResource("non-existent-resource")
        withClue("Removing non-existent resource should return false") {
            result shouldBe false
        }

        // Verify no notification is sent (use eventually for cross-platform reliability)
        withClue("No notification should be sent when resource doesn't exist") {
            eventually(2.seconds) {
                notifications.shouldBeEmpty()
            }
        }
    }
}
