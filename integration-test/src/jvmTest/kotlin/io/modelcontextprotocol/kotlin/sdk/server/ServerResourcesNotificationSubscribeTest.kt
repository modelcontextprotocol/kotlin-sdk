package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsResourcesUpdated
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequestParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

class ServerResourcesNotificationSubscribeTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        resources = ServerCapabilities.Resources(subscribe = true),
    )

    @Test
    fun `should notify subscribed client when resources are added and removed`(): Unit = runBlocking {
        val updatedUris = ConcurrentLinkedQueue<String>()
        client.setNotificationHandler<ResourceUpdatedNotification>(NotificationsResourcesUpdated) {
            updatedUris.add(it.params.uri)
            CompletableDeferred(Unit)
        }
        val uris = listOf("test://resource1", "test://resource2")
        uris.forEach { client.subscribeResource(SubscribeRequest(SubscribeRequestParams(uri = it))) }

        uris.forEach { uri ->
            server.addResource(uri = uri, name = uri, description = "Test resource") { ReadResourceResult(emptyList()) }
        }
        await untilAsserted { updatedUris.toList() shouldContainExactlyInAnyOrder uris }

        updatedUris.clear()
        uris.forEach { server.removeResource(it) shouldBe true }
        await untilAsserted { updatedUris.toList() shouldContainExactlyInAnyOrder uris }
    }
}
