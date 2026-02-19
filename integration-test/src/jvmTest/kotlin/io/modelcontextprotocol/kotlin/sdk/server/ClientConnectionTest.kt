package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Notification
import io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Request
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.Root
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

class ClientConnectionTest : AbstractServerFeaturesTest() {

    private inline fun <reified T : Notification> onClientNotification(method: Method): CompletableDeferred<T> {
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
        client.setRequestHandler<Req>(method) { req, _ -> response(req) }
    }

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(listChanged = true),
        prompts = ServerCapabilities.Prompts(listChanged = true),
        resources = ServerCapabilities.Resources(listChanged = true),
        logging = ServerCapabilities.Logging,
    )

    override fun getClientCapabilities(): ClientCapabilities = ClientCapabilities(
        roots = ClientCapabilities.Roots(listChanged = true),
    )

    private val sampleRoots = listOf(Root("file:///project", "Project Root"))

    /** Holds all deferreds that capture what a server-side handler emits during [callAllMethods]. */
    private inner class Captures {
        val pingComplete = CompletableDeferred<Unit>()
        val rootsResult = CompletableDeferred<ListRootsResult>()
        val sessionId = CompletableDeferred<String>()
        val toolListChanged =
            onClientNotification<ToolListChangedNotification>(Method.Defined.NotificationsToolsListChanged)
        val resourceListChanged =
            onClientNotification<ResourceListChangedNotification>(Method.Defined.NotificationsResourcesListChanged)
        val promptListChanged =
            onClientNotification<PromptListChangedNotification>(Method.Defined.NotificationsPromptsListChanged)
        val loggingMessage = onClientNotification<LoggingMessageNotification>(Method.Defined.NotificationsMessage)
        val resourceUpdated =
            onClientNotification<ResourceUpdatedNotification>(Method.Defined.NotificationsResourcesUpdated)

        init {
            onClientRequest<ListRootsRequest, ListRootsResult>(Method.Defined.RootsList) {
                ListRootsResult(sampleRoots)
            }
        }

        suspend fun assertAll() {
            val roots = rootsResult.await()
            val log = loggingMessage.await()
            val resourceUri = resourceUpdated.await().params.uri
            val capturedSessionId = sessionId.await()
            assertSoftly {
                withClue("ping() was not called inside the handler") {
                    pingComplete.isCompleted shouldBe true
                }
                roots.roots shouldBe sampleRoots
                withClue("sendToolListChanged() did not deliver a notification to the client") {
                    toolListChanged.isCompleted shouldBe true
                }
                withClue("sendResourceListChanged() did not deliver a notification to the client") {
                    resourceListChanged.isCompleted shouldBe true
                }
                withClue("sendPromptListChanged() did not deliver a notification to the client") {
                    promptListChanged.isCompleted shouldBe true
                }
                withClue("sendLoggingMessage() delivered unexpected payload") {
                    log.params.data shouldBe JsonPrimitive("log")
                }
                withClue("sendResourceUpdated() delivered wrong URI") {
                    resourceUri shouldBe "test://res"
                }
                capturedSessionId.shouldNotBeBlank()
            }
        }
    }

    /** Exercises every [ClientConnection] method, recording results into [cap]. */
    private suspend fun ClientConnection.callAllMethods(cap: Captures) {
        ping()
        cap.pingComplete.complete(Unit)
        cap.rootsResult.complete(listRoots())
        sendToolListChanged()
        sendResourceListChanged()
        sendPromptListChanged()
        sendLoggingMessage(
            LoggingMessageNotification(
                LoggingMessageNotificationParams(level = LoggingLevel.Info, data = JsonPrimitive("log")),
            ),
        )
        sendResourceUpdated(ResourceUpdatedNotification(ResourceUpdatedNotificationParams("test://res")))
        cap.sessionId.complete(sessionId)
    }

    @Test
    fun `all ClientConnection methods are callable from tool handler`() = runTest {
        val cap = Captures()
        addTool("test") { callAllMethods(cap) }

        client.callTool(CallToolRequest(CallToolRequestParams("test")))

        cap.assertAll()
    }

    @Test
    fun `all ClientConnection methods are callable from prompt handler`() = runTest {
        val cap = Captures()
        addPrompt("test") { callAllMethods(cap) }

        client.getPrompt(GetPromptRequest(GetPromptRequestParams("test")))

        cap.assertAll()
    }

    @Test
    fun `all ClientConnection methods are callable from resource handler`() = runTest {
        val cap = Captures()
        addResource("test://resource") { callAllMethods(cap) }

        client.readResource(ReadResourceRequest(ReadResourceRequestParams("test://resource")))

        cap.assertAll()
    }
}
