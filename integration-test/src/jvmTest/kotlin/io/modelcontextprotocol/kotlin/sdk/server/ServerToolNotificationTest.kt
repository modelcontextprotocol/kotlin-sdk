package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ServerToolNotificationTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(true),
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
}
