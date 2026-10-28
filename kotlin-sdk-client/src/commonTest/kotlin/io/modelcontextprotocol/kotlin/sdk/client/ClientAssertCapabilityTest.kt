package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for the protected capability checks of [Client], exposed through [TestClient].
 * Server capabilities are seeded through the `initialize` response of [MockTransport].
 */
class ClientAssertCapabilityTest {

    @Test
    fun `assertCapability tasks throws when server has no tasks capability`() = runTest {
        val client = newTestClient(serverCapabilities = ServerCapabilities())

        shouldThrow<IllegalStateException> {
            client.exposedAssertCapability("tasks", "tasks/list")
        }.message shouldContain "Server does not support tasks"
    }

    @Test
    fun `TasksGet throws when server has no tasks capability`() = runTest {
        val client = newTestClient(serverCapabilities = ServerCapabilities())

        shouldThrow<IllegalStateException> {
            client.exposedAssertCapabilityForMethod(Method.Defined.TasksGet)
        }.message shouldContain "Server does not support tasks"
    }

    @Test
    fun `TasksGet does not throw when server declared tasks`() = runTest {
        val client = newTestClient(serverCapabilities = ServerCapabilities(tasks = ServerCapabilities.Tasks()))

        client.exposedAssertCapabilityForMethod(Method.Defined.TasksGet)
    }

    @Test
    fun `TasksList throws when server tasks list is null`() = runTest {
        val client = newTestClient(serverCapabilities = ServerCapabilities(tasks = ServerCapabilities.Tasks()))

        shouldThrow<IllegalStateException> {
            client.exposedAssertCapabilityForMethod(Method.Defined.TasksList)
        }.message shouldContain "Server does not support listing tasks"
    }

    @Test
    fun `TasksCancel throws when server tasks cancel is null`() = runTest {
        val client = newTestClient(serverCapabilities = ServerCapabilities(tasks = ServerCapabilities.Tasks()))

        shouldThrow<IllegalStateException> {
            client.exposedAssertCapabilityForMethod(Method.Defined.TasksCancel)
        }.message shouldContain "Server does not support cancelling tasks"
    }

    @Test
    fun `NotificationsTasksStatus throws when client has no tasks capability`() = runTest {
        val client = newTestClient(clientCapabilities = ClientCapabilities())

        shouldThrow<IllegalStateException> {
            client.exposedAssertNotificationCapability(Method.Defined.NotificationsTasksStatus)
        }.message shouldContain "Client does not support tasks"
    }

    @Test
    fun `CompletionComplete does not throw when server declared completions`() = runTest {
        val client =
            newTestClient(serverCapabilities = ServerCapabilities(completions = ServerCapabilities.Completions))

        client.exposedAssertCapabilityForMethod(Method.Defined.CompletionComplete)
    }

    @Test
    fun `CompletionComplete throws when server has no completions capability`() = runTest {
        val client = newTestClient(serverCapabilities = ServerCapabilities(prompts = ServerCapabilities.Prompts()))

        shouldThrow<IllegalStateException> {
            client.exposedAssertCapabilityForMethod(Method.Defined.CompletionComplete)
        }.message shouldContain "Server does not support completions"
    }

    private suspend fun newTestClient(
        serverCapabilities: ServerCapabilities = ServerCapabilities(),
        clientCapabilities: ClientCapabilities = ClientCapabilities(),
    ): TestClient = TestClient(Implementation("test-client", "1.0.0"), ClientOptions(capabilities = clientCapabilities))
        .apply { connect(MockTransport(serverCapabilities)) }

    private class TestClient(clientInfo: Implementation, options: ClientOptions) : Client(clientInfo, options) {
        fun exposedAssertCapability(capability: String, method: String): Unit = assertCapability(capability, method)
        fun exposedAssertCapabilityForMethod(method: Method): Unit = assertCapabilityForMethod(method)
        fun exposedAssertNotificationCapability(method: Method): Unit = assertNotificationCapability(method)
    }
}
