package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Notification
import io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.Request
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.Root
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class ClientConnectionTest : AbstractServerFeaturesTest() {

    private inline fun <reified T : Notification> onClientNotification(method: Method): Deferred<T> {
        val deferred = CompletableDeferred<T>()
        client.setNotificationHandler<T>(method) {
            deferred.complete(it)
            CompletableDeferred(Unit)
        }
        return deferred
    }

    private inline fun <reified Req : Request, reified Res : RequestResult> onClientRequest(
        method: Method,
        crossinline response: (Req) -> Res,
    ) {
        client.setRequestHandler<Req>(method) { req, _ ->
            response(req)
        }
    }

    private fun addTool(name: String, block: suspend ClientConnection.() -> Unit) {
        server.addTool(name, "Test $name") {
            block()
            CallToolResult(listOf(TextContent("Success")))
        }
    }

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(listChanged = true),
        prompts = ServerCapabilities.Prompts(listChanged = true),
        resources = ServerCapabilities.Resources(listChanged = true),
        logging = JsonObject(emptyMap()),
    )

    override fun getClientCapabilities(): ClientCapabilities = ClientCapabilities(
        roots = ClientCapabilities.Roots(listChanged = true),
    )

    @Test
    fun `ping should work from server to client`() = runTest {
        val pingReceived = CompletableDeferred<Unit>()

        addTool("test-ping") {
            ping()
            pingReceived.complete(Unit)
        }

        client.callTool(CallToolRequest(CallToolRequestParams("test-ping")))
        pingReceived.isCompleted shouldBe true
    }

    @Test
    fun `notification should send logging message to client`() = runTest {
        val notificationReceived = onClientNotification<LoggingMessageNotification>(Method.Defined.NotificationsMessage)

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
    fun `notification should filter logging messages below set level`(minLevel: LoggingLevel) = runTest {
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
    fun `sendLoggingMessage should send message at each level`(expectedLevel: LoggingLevel) = runTest {
        val notificationReceived = onClientNotification<LoggingMessageNotification>(
            Method.Defined.NotificationsMessage,
        )

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

    @Test
    fun `sendLoggingMessage should filter messages below set level`() = runTest {
        val receivedMessages = mutableListOf<LoggingMessageNotification>()
        client.setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) {
            receivedMessages.add(it)
            CompletableDeferred(Unit)
        }

        client.setLoggingLevel(LoggingLevel.Warning)

        addTool("test-logging-filtered") {
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

        client.callTool(CallToolRequest(CallToolRequestParams("test-logging-filtered")))

        val expectedLevels = LoggingLevel.entries.filter { it >= LoggingLevel.Warning }
        receivedMessages shouldHaveSize expectedLevels.size
        receivedMessages.map { it.params.level } shouldBe expectedLevels
    }

    @ParameterizedTest
    @EnumSource(LoggingLevel::class)
    fun `sendLoggingMessage should filter all messages below set level`(minLevel: LoggingLevel) = runTest {
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
    fun `sendLoggingMessage should send no messages when level is set to highest`() = runTest {
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

    @Test
    fun `listRoots should work`() = runTest {
        val expectedRoots = listOf(
            Root("file:///home/user/project", "Project Root"),
            Root("file:///var/log", "Logs"),
        )
        onClientRequest<ListRootsRequest, ListRootsResult>(Method.Defined.RootsList) {
            ListRootsResult(expectedRoots)
        }

        addTool("test-list-roots") {
            val result = listRoots()
            result.roots shouldHaveSize expectedRoots.size
            result.roots shouldBe expectedRoots
        }

        client.callTool(CallToolRequest(CallToolRequestParams("test-list-roots")))
    }

    @Test
    fun `sendToolListChanged should work`() = runTest {
        val notificationReceived =
            onClientNotification<ToolListChangedNotification>(Method.Defined.NotificationsToolsListChanged)

        addTool("test-tool-changed") {
            sendToolListChanged()
        }

        client.callTool(CallToolRequest(CallToolRequestParams("test-tool-changed")))
        notificationReceived.isCompleted shouldBe true
    }

    @Test
    fun `sendResourceListChanged should work`() = runTest {
        val notificationReceived =
            onClientNotification<ResourceListChangedNotification>(Method.Defined.NotificationsResourcesListChanged)

        addTool("test-resource-changed") {
            sendResourceListChanged()
        }

        client.callTool(CallToolRequest(CallToolRequestParams("test-resource-changed")))
        notificationReceived.isCompleted shouldBe true
    }

    @Test
    fun `sendPromptListChanged should work`() = runTest {
        val notificationReceived =
            onClientNotification<PromptListChangedNotification>(Method.Defined.NotificationsPromptsListChanged)

        addTool("test-prompt-changed") {
            sendPromptListChanged()
        }

        client.callTool(CallToolRequest(CallToolRequestParams("test-prompt-changed")))
        notificationReceived.isCompleted shouldBe true
    }
}
