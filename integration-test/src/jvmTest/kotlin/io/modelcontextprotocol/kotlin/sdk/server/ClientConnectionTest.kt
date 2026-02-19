package io.modelcontextprotocol.kotlin.sdk.server

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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertTrue(pingReceived.isCompleted)
    }

    @Test
    fun `notification should work from server to client`() = runTest {
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
        assertEquals(expectedLevel, received.params.level)
        assertEquals(expectedData, received.params.data)
    }

    @ParameterizedTest
    @EnumSource(LoggingLevel::class)
    fun `sendLoggingMessage should work`(expectedLevel: LoggingLevel) = runTest {
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
        assertEquals(expectedLevel, received.params.level)
        assertEquals(expectedData, received.params.data)
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
            assertEquals(expectedRoots.size, result.roots.size)
            assertEquals(expectedRoots, result.roots)
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
        assertTrue(notificationReceived.isCompleted)
    }

    @Test
    fun `sendResourceListChanged should work`() = runTest {
        val notificationReceived =
            onClientNotification<ResourceListChangedNotification>(Method.Defined.NotificationsResourcesListChanged)

        addTool("test-resource-changed") {
            sendResourceListChanged()
        }

        client.callTool(CallToolRequest(CallToolRequestParams("test-resource-changed")))
        assertTrue(notificationReceived.isCompleted)
    }

    @Test
    fun `sendPromptListChanged should work`() = runTest {
        val notificationReceived =
            onClientNotification<PromptListChangedNotification>(Method.Defined.NotificationsPromptsListChanged)

        addTool("test-prompt-changed") {
            sendPromptListChanged()
        }

        client.callTool(CallToolRequest(CallToolRequestParams("test-prompt-changed")))
        assertTrue(notificationReceived.isCompleted)
    }
}
