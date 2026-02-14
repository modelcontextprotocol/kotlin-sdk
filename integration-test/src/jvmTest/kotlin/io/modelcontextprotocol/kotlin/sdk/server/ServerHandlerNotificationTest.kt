package io.modelcontextprotocol.kotlin.sdk.server

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
import kotlin.test.assertEquals

class ServerHandlerNotificationTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(true),
        prompts = ServerCapabilities.Prompts(true),
        resources = ServerCapabilities.Resources(true, null),
        logging = JsonObject(emptyMap()),
    )

    @Test
    fun `tool should be able to send notification to client`() = runTest {
        val notificationReceived = CompletableDeferred<LoggingMessageNotification>()

        // Setup client notification handler
        client.setNotificationHandler<LoggingMessageNotification>(
            Method.Defined.NotificationsMessage,
        ) { notification ->
            notificationReceived.complete(notification)
            CompletableDeferred(Unit)
        }

        val toolName = "notify-tool"
        val notificationText = "Notification from tool"

        // Add a tool that sends a notification
        server.addTool(toolName, "A tool that notifies") { _ ->
            // Use the session from context to send a notification
            session.sendLoggingMessage(
                LoggingMessageNotification(
                    LoggingMessageNotificationParams(
                        level = LoggingLevel.Info,
                        data = JsonPrimitive(notificationText),
                    ),
                ),
            )
            CallToolResult(listOf(TextContent("Tool executed")))
        }

        // Call the tool
        val result = client.callTool(CallToolRequest(CallToolRequestParams(name = toolName)))

        assertEquals(1, result.content.size)
        assertEquals("Tool executed", (result.content[0] as TextContent).text)

        // Verify notification was received
        val receivedNotification = notificationReceived.await()
        assertEquals(LoggingLevel.Info, receivedNotification.params.level)
        assertEquals(JsonPrimitive(notificationText), receivedNotification.params.data)
    }

    @Test
    fun `prompt should be able to send notification to client`() = runTest {
        val notificationReceived = CompletableDeferred<LoggingMessageNotification>()

        // Setup client notification handler
        client.setNotificationHandler<LoggingMessageNotification>(
            Method.Defined.NotificationsMessage,
        ) { notification ->
            notificationReceived.complete(notification)
            CompletableDeferred(Unit)
        }

        val promptName = "notify-prompt"
        val notificationText = "Notification from prompt"

        // Add a prompt that sends a notification
        server.addPrompt(promptName, "A prompt that notifies") { _ ->
            // Use the session from context to send a notification
            session.sendLoggingMessage(
                LoggingMessageNotification(
                    LoggingMessageNotificationParams(
                        level = LoggingLevel.Info,
                        data = JsonPrimitive(notificationText),
                    ),
                ),
            )
            GetPromptResult(
                messages = listOf(),
                description = "Prompt executed",
            )
        }

        // Get the prompt
        val result = client.getPrompt(GetPromptRequest(GetPromptRequestParams(name = promptName)))

        assertEquals("Prompt executed", result.description)

        // Verify notification was received
        val receivedNotification = notificationReceived.await()
        assertEquals(LoggingLevel.Info, receivedNotification.params.level)
        assertEquals(JsonPrimitive(notificationText), receivedNotification.params.data)
    }

    @Test
    fun `resource should be able to send notification to client`() = runTest {
        val notificationReceived = CompletableDeferred<LoggingMessageNotification>()

        // Setup client notification handler
        client.setNotificationHandler<LoggingMessageNotification>(
            Method.Defined.NotificationsMessage,
        ) { notification ->
            notificationReceived.complete(notification)
            CompletableDeferred(Unit)
        }

        val resourceUri = "test://notify-resource"
        val notificationText = "Notification from resource"

        // Add a resource that sends a notification
        server.addResource(resourceUri, "Notify Resource", "A resource that notifies") { _ ->
            // Use the session from context to send a notification
            session.sendLoggingMessage(
                LoggingMessageNotification(
                    LoggingMessageNotificationParams(
                        level = LoggingLevel.Info,
                        data = JsonPrimitive(notificationText),
                    ),
                ),
            )
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        uri = resourceUri,
                        text = "Resource read",
                    ),
                ),
            )
        }

        // Read the resource
        val result = client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = resourceUri)))

        assertEquals(1, result.contents.size)
        assertEquals("Resource read", (result.contents[0] as TextResourceContents).text)

        // Verify notification was received
        val receivedNotification = notificationReceived.await()
        assertEquals(LoggingLevel.Info, receivedNotification.params.level)
        assertEquals(JsonPrimitive(notificationText), receivedNotification.params.data)
    }
}
