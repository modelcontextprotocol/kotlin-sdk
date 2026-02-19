package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Notification
import io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.Request
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.Root
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

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

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(listChanged = true),
        prompts = ServerCapabilities.Prompts(listChanged = true),
        resources = ServerCapabilities.Resources(listChanged = true),
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
