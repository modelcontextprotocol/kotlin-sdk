package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Tests for the protected helper [Client.assertCapability].
 *
 * `assertCapability(capability, method)` is `protected`, so this suite uses
 * a small subclass [TestClient] that re-exports it via [TestClient.exposedAssertCapability].
 * Server capabilities are seeded by completing the initialization handshake through
 * [CapabilitiesTransport], which replays a configurable [ServerCapabilities] in its
 * `initialize` response.
 */
class ClientAssertCapabilityTest {

    @Test
    fun `assertCapability tasks throws when server has no tasks capability`() = runTest {
        val client = newTestClient(serverCapabilities = ServerCapabilities())
        val ex = assertFailsWith<IllegalStateException> {
            client.exposedAssertCapability("tasks", "tasks/list")
        }
        ex.message.orEmpty() shouldContain "Server does not support tasks"
    }

    @Test
    fun `TasksGet throws when server has no tasks capability`() = runTest {
        val client = newTestClient(serverCapabilities = ServerCapabilities())
        val ex = assertFailsWith<IllegalStateException> {
            client.exposedAssertCapabilityForMethod(Method.Defined.TasksGet)
        }
        ex.message.orEmpty() shouldContain "Server does not support tasks"
    }

    @Test
    fun `TasksGet does not throw when server declared tasks`() = runTest {
        val client = newTestClient(
            serverCapabilities = ServerCapabilities(tasks = ServerCapabilities.Tasks()),
        )
        client.exposedAssertCapabilityForMethod(Method.Defined.TasksGet)
    }

    @Test
    fun `TasksList throws when server tasks list is null`() = runTest {
        val client = newTestClient(
            serverCapabilities = ServerCapabilities(tasks = ServerCapabilities.Tasks()),
        )
        val ex = assertFailsWith<IllegalStateException> {
            client.exposedAssertCapabilityForMethod(Method.Defined.TasksList)
        }
        ex.message.orEmpty() shouldContain "Server does not support listing tasks"
    }

    @Test
    fun `TasksCancel throws when server tasks cancel is null`() = runTest {
        val client = newTestClient(
            serverCapabilities = ServerCapabilities(tasks = ServerCapabilities.Tasks()),
        )
        val ex = assertFailsWith<IllegalStateException> {
            client.exposedAssertCapabilityForMethod(Method.Defined.TasksCancel)
        }
        ex.message.orEmpty() shouldContain "Server does not support cancelling tasks"
    }

    @Test
    fun `NotificationsTasksStatus throws when client has no tasks capability`() = runTest {
        val client = newTestClient(clientCapabilities = ClientCapabilities())
        val ex = assertFailsWith<IllegalStateException> {
            client.exposedAssertNotificationCapability(Method.Defined.NotificationsTasksStatus)
        }
        ex.message.orEmpty() shouldContain "Client does not support tasks"
    }

    @Test
    fun `CompletionComplete does not throw when server declared completions`() = runTest {
        val client = newTestClient(
            serverCapabilities = ServerCapabilities(completions = ServerCapabilities.Completions),
        )
        client.exposedAssertCapabilityForMethod(Method.Defined.CompletionComplete)
    }

    @Test
    fun `CompletionComplete throws when server has no completions capability`() = runTest {
        val client = newTestClient(
            serverCapabilities = ServerCapabilities(prompts = ServerCapabilities.Prompts()),
        )
        val ex = assertFailsWith<IllegalStateException> {
            client.exposedAssertCapabilityForMethod(Method.Defined.CompletionComplete)
        }
        ex.message.orEmpty() shouldContain "Server does not support completions"
    }

    private suspend fun newTestClient(
        serverCapabilities: ServerCapabilities = ServerCapabilities(),
        clientCapabilities: ClientCapabilities = ClientCapabilities(),
    ): TestClient {
        val client = TestClient(
            Implementation("test-client", "1.0.0"),
            ClientOptions(capabilities = clientCapabilities),
        )
        val transport = CapabilitiesTransport(serverCapabilities)
        client.connect(transport)
        return client
    }

    /**
     * Test-only [Client] subclass that exposes the protected
     * [Client.assertCapability], [Client.assertCapabilityForMethod], and
     * [Client.assertNotificationCapability] helpers to the test code.
     */
    private class TestClient(clientInfo: Implementation, options: ClientOptions = ClientOptions()) :
        Client(clientInfo, options) {
        fun exposedAssertCapability(capability: String, method: String): Unit = assertCapability(capability, method)
        fun exposedAssertCapabilityForMethod(method: Method): Unit = assertCapabilityForMethod(method)
        fun exposedAssertNotificationCapability(method: Method): Unit = assertNotificationCapability(method)
    }

    /**
     * Minimal in-memory [Transport] that responds to `initialize` with a configurable
     * [ServerCapabilities]. Other JSON-RPC traffic (e.g. the `notifications/initialized`
     * sent by [Client.connect]) is silently consumed.
     */
    private class CapabilitiesTransport(private val serverCapabilities: ServerCapabilities) : Transport {
        private var onMessageBlock: (suspend (JSONRPCMessage) -> Unit)? = null
        private var onCloseBlock: (() -> Unit)? = null

        override suspend fun start() = Unit

        override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
            if (message is JSONRPCRequest && message.method == "initialize") {
                onMessageBlock?.invoke(
                    JSONRPCResponse(
                        id = message.id,
                        result = InitializeResult(
                            protocolVersion = LATEST_PROTOCOL_VERSION,
                            capabilities = serverCapabilities,
                            serverInfo = Implementation("mock-server", "1.0.0"),
                        ),
                    ),
                )
            }
        }

        override suspend fun close() {
            onCloseBlock?.invoke()
        }

        override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
            onMessageBlock = block
        }

        override fun onClose(block: () -> Unit) {
            onCloseBlock = block
        }

        override fun onError(block: (Throwable) -> Unit) = Unit
    }
}
