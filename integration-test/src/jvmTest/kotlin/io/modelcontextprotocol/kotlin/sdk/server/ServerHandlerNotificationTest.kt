package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

class ServerHandlerNotificationTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(true),
        prompts = ServerCapabilities.Prompts(true),
        resources = ServerCapabilities.Resources(true, null),
        logging = JsonObject(emptyMap()),
    )

    @Test
    fun `tool handler can send notification to client`() = runTest {
        val notificationReceived = CompletableDeferred<LoggingMessageNotification>()
        client.setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) { notification ->
            notificationReceived.complete(notification)
            CompletableDeferred(Unit)
        }

        val notificationText = "Notification from tool"
        server.addTool("notify-tool", "A tool that notifies") {
            sendLoggingMessage(
                LoggingMessageNotification(
                    LoggingMessageNotificationParams(
                        level = LoggingLevel.Info,
                        data = JsonPrimitive(notificationText),
                    ),
                ),
            )
            CallToolResult(listOf(TextContent("Tool executed")))
        }

        val result = client.callTool(CallToolRequest(CallToolRequestParams(name = "notify-tool")))

        (result.content[0] as TextContent).text shouldBe "Tool executed"
        val received = notificationReceived.await()
        received.params.level shouldBe LoggingLevel.Info
        received.params.data shouldBe JsonPrimitive(notificationText)
    }

    @Test
    fun `prompt handler can send notification to client`() = runTest {
        val notificationReceived = CompletableDeferred<LoggingMessageNotification>()
        client.setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) { notification ->
            notificationReceived.complete(notification)
            CompletableDeferred(Unit)
        }

        val notificationText = "Notification from prompt"
        server.addPrompt("notify-prompt", "A prompt that notifies") {
            sendLoggingMessage(
                LoggingMessageNotification(
                    LoggingMessageNotificationParams(
                        level = LoggingLevel.Info,
                        data = JsonPrimitive(notificationText),
                    ),
                ),
            )
            GetPromptResult(messages = listOf(), description = "Prompt executed")
        }

        val result = client.getPrompt(GetPromptRequest(GetPromptRequestParams(name = "notify-prompt")))

        result.description shouldBe "Prompt executed"
        val received = notificationReceived.await()
        received.params.level shouldBe LoggingLevel.Info
        received.params.data shouldBe JsonPrimitive(notificationText)
    }

    @Test
    fun `resource handler can send notification to client`() = runTest {
        val notificationReceived = CompletableDeferred<LoggingMessageNotification>()
        client.setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) { notification ->
            notificationReceived.complete(notification)
            CompletableDeferred(Unit)
        }

        val resourceUri = "test://notify-resource"
        val notificationText = "Notification from resource"
        server.addResource(resourceUri, "Notify Resource", "A resource that notifies") {
            sendLoggingMessage(
                LoggingMessageNotification(
                    LoggingMessageNotificationParams(
                        level = LoggingLevel.Info,
                        data = JsonPrimitive(notificationText),
                    ),
                ),
            )
            ReadResourceResult(
                contents = listOf(TextResourceContents(uri = resourceUri, text = "Resource read")),
            )
        }

        val result = client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = resourceUri)))

        (result.contents[0] as TextResourceContents).text shouldBe "Resource read"
        val received = notificationReceived.await()
        received.params.level shouldBe LoggingLevel.Info
        received.params.data shouldBe JsonPrimitive(notificationText)
    }
}
