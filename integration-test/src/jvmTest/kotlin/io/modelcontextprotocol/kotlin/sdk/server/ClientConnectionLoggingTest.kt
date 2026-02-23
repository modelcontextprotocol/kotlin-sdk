package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class ClientConnectionLoggingTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(listChanged = true),
        logging = JsonObject(emptyMap()),
    )

    @Test
    fun `notification should send logging message to client`(): Unit = runBlocking {
        val notificationReceived = CompletableDeferred<LoggingMessageNotification>()
        client.setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) {
            notificationReceived.complete(it)
            CompletableDeferred(Unit)
        }

        val expectedLevel = LoggingLevel.Info
        val expectedData = JsonPrimitive("test-data-sample")

        addTool("test-notification") {
            notification(
                LoggingMessageNotification(
                    LoggingMessageNotificationParams(
                        level = expectedLevel,
                        data = expectedData,
                    ),
                ),
            )
        }

        client.callTool(CallToolRequest(CallToolRequestParams("test-notification")))
        val received = notificationReceived.await()
        received.params.level shouldBe expectedLevel
        received.params.data shouldBe expectedData
    }

    @ParameterizedTest
    @EnumSource(LoggingLevel::class)
    fun `notification should filter logging messages below level`(minLevel: LoggingLevel): Unit = runBlocking {
        val receivedMessages = mutableListOf<LoggingMessageNotification>()
        client.setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) {
            receivedMessages.add(it)
            CompletableDeferred(Unit)
        }

        client.setLoggingLevel(minLevel)

        addTool("test-notification-filtered") {
            LoggingLevel.entries.forEach { level ->
                notification(
                    LoggingMessageNotification(
                        LoggingMessageNotificationParams(
                            level = level,
                            data = JsonPrimitive(level.name),
                        ),
                    ),
                )
            }
        }

        client.callTool(CallToolRequest(CallToolRequestParams("test-notification-filtered")))

        val expectedLevels = LoggingLevel.entries.filter { it >= minLevel }
        receivedMessages shouldHaveSize expectedLevels.size
        receivedMessages.map { it.params.level } shouldBe expectedLevels
    }

    @ParameterizedTest
    @EnumSource(LoggingLevel::class)
    fun `sendLoggingMessage should send message at level`(expectedLevel: LoggingLevel): Unit = runBlocking {
        val notificationReceived = CompletableDeferred<LoggingMessageNotification>()
        client.setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) {
            notificationReceived.complete(it)
            CompletableDeferred(Unit)
        }

        val expectedData = JsonObject(mapOf("key" to JsonPrimitive("value")))

        addTool("test-logging") {
            sendLoggingMessage(
                LoggingMessageNotification(
                    LoggingMessageNotificationParams(
                        level = expectedLevel,
                        data = expectedData,
                    ),
                ),
            )
        }

        client.callTool(CallToolRequest(CallToolRequestParams("test-logging")))
        val received = notificationReceived.await()
        received.params.level shouldBe expectedLevel
        received.params.data shouldBe expectedData
    }

    @ParameterizedTest
    @EnumSource(LoggingLevel::class)
    fun `sendLoggingMessage should filter messages below level`(minLevel: LoggingLevel): Unit = runBlocking {
        val receivedMessages = mutableListOf<LoggingMessageNotification>()
        client.setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) {
            receivedMessages.add(it)
            CompletableDeferred(Unit)
        }

        client.setLoggingLevel(minLevel)

        addTool("test-logging-level") {
            LoggingLevel.entries.forEach { level ->
                sendLoggingMessage(
                    LoggingMessageNotification(
                        LoggingMessageNotificationParams(
                            level = level,
                            data = JsonPrimitive(level.name),
                        ),
                    ),
                )
            }
        }

        client.callTool(CallToolRequest(CallToolRequestParams("test-logging-level")))

        val expectedLevels = LoggingLevel.entries.filter { it >= minLevel }
        receivedMessages shouldHaveSize expectedLevels.size
        receivedMessages.map { it.params.level } shouldBe expectedLevels
    }

    @Test
    fun `sendLoggingMessage should send no messages when level is set to highest`(): Unit = runBlocking {
        val receivedMessages = mutableListOf<LoggingMessageNotification>()
        client.setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) {
            receivedMessages.add(it)
            CompletableDeferred(Unit)
        }

        client.setLoggingLevel(LoggingLevel.Emergency)

        addTool("test-logging-highest") {
            LoggingLevel.entries.dropLast(1).forEach { level ->
                sendLoggingMessage(
                    LoggingMessageNotification(
                        LoggingMessageNotificationParams(
                            level = level,
                            data = JsonPrimitive(level.name),
                        ),
                    ),
                )
            }
        }

        client.callTool(CallToolRequest(CallToolRequestParams("test-logging-highest")))
        receivedMessages.shouldBeEmpty()
    }
}
