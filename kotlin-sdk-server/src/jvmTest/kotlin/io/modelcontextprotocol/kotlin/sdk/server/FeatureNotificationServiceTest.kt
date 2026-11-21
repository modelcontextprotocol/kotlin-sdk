package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import io.modelcontextprotocol.kotlin.test.utils.runIntegrationTest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@ResourceLock(Resources.SYSTEM_ERR)
class FeatureNotificationServiceTest {
    private val resourceUri = "test://resource"

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `should log skipped resource updates without a subscription`(unsubscribe: Boolean): Unit = runIntegrationTest {
        val (logs, notifications) = sendResourceUpdate(subscribe = unsubscribe, unsubscribe = unsubscribe)

        logs shouldContain "No subscription for resource $resourceUri. Skipping notification:"
        notifications shouldBe emptyList()
    }

    @Test
    fun `should send resource updates without logging a missing subscription`(): Unit = runIntegrationTest {
        val (logs, notifications) = sendResourceUpdate(subscribe = true)

        logs shouldNotContain "No subscription for resource $resourceUri"
        notifications shouldBe listOf(
            ResourceUpdatedNotification(ResourceUpdatedNotificationParams(uri = resourceUri)).toJSON(),
        )
    }

    private suspend fun sendResourceUpdate(
        subscribe: Boolean,
        unsubscribe: Boolean = false,
    ): Pair<String, List<JSONRPCNotification>> {
        val transport = RecordingTransport()
        val session = ServerSession(
            serverInfo = Implementation("test-server", "1.0.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    resources = ServerCapabilities.Resources(subscribe = true),
                    tools = ServerCapabilities.Tools(listChanged = true),
                ),
            ),
            instructions = null,
        )
        val logs = captureLogs {
            val service = FeatureNotificationService()
            try {
                session.connect(transport)
                service.subscribeSession(session)
                // Wait for the asynchronous collector before emitting the resource update under test.
                await.atMost(Duration.ofSeconds(3)) untilAsserted {
                    service.toolListChangedListener.onFeatureUpdated("test-tool")
                    transport.messages.contains(ToolListChangedNotification().toJSON()) shouldBe true
                }
                if (subscribe) service.subscribeToResourceUpdate(session, resourceUri)
                if (unsubscribe) service.unsubscribeFromResourceUpdate(session, resourceUri)
                service.resourceUpdatedListener.onFeatureUpdated(resourceUri)
            } finally {
                withContext(NonCancellable) {
                    try {
                        service.close()
                    } finally {
                        session.close()
                    }
                }
            }
        }
        val resourceNotifications = transport.messages.filterIsInstance<JSONRPCNotification>()
            .filter { it.method == Method.Defined.NotificationsResourcesUpdated.value }
        return logs to resourceNotifications
    }

    private suspend fun captureLogs(block: suspend () -> Unit): String {
        val originalErr = System.err
        val output = ByteArrayOutputStream()
        PrintStream(output).use { stream ->
            try {
                System.setErr(stream)
                block()
            } finally {
                System.setErr(originalErr)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private class RecordingTransport : AbstractTransport() {
        val messages = ConcurrentLinkedQueue<JSONRPCMessage>()

        override suspend fun start() = Unit

        override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
            messages.add(message)
        }

        override suspend fun close() {
            invokeOnCloseCallback()
        }
    }
}
