package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Tests for the protected helpers [ServerSession.assertCapabilityForMethod] and
 * [ServerSession.assertNotificationCapability].
 *
 * Both helpers are `protected`, so this suite uses a small subclass [TestServerSession]
 * that re-exports them via [TestServerSession.exposedAssertCapabilityForMethod] and
 * [TestServerSession.exposedAssertNotificationCapability]. Client capabilities are
 * seeded by replaying an [InitializeRequest] through [InitializeReplayTransport]:
 * the transport drives the inbound side of the handshake on `start()`, which causes
 * the session's internal [Method.Defined.Initialize] handler to populate
 * [ServerSession.clientCapabilities].
 */
class ServerSessionAssertCapabilityTest {

    @Test
    fun `server TasksGet throws when client has no tasks capability`() = runTest {
        val session = newTestServerSession(clientCapabilities = ClientCapabilities())
        val ex = assertFailsWith<IllegalStateException> {
            session.exposedAssertCapabilityForMethod(Method.Defined.TasksGet)
        }
        ex.message.orEmpty() shouldContain "Client does not support tasks"
    }

    @Test
    fun `server TasksGet succeeds when client declared tasks`() = runTest {
        val session = newTestServerSession(
            clientCapabilities = ClientCapabilities(tasks = ClientCapabilities.Tasks()),
        )
        session.exposedAssertCapabilityForMethod(Method.Defined.TasksGet)
    }

    @Test
    fun `server TasksList throws when client tasks list is null`() = runTest {
        val session = newTestServerSession(
            clientCapabilities = ClientCapabilities(tasks = ClientCapabilities.Tasks()),
        )
        val ex = assertFailsWith<IllegalStateException> {
            session.exposedAssertCapabilityForMethod(Method.Defined.TasksList)
        }
        ex.message.orEmpty() shouldContain "Client does not support listing tasks"
    }

    @Test
    fun `server TasksCancel throws when client tasks cancel is null`() = runTest {
        val session = newTestServerSession(
            clientCapabilities = ClientCapabilities(tasks = ClientCapabilities.Tasks()),
        )
        val ex = assertFailsWith<IllegalStateException> {
            session.exposedAssertCapabilityForMethod(Method.Defined.TasksCancel)
        }
        ex.message.orEmpty() shouldContain "Client does not support cancelling tasks"
    }

    @Test
    fun `server NotificationsTasksStatus throws when server has no tasks capability`() = runTest {
        val session = newTestServerSession(serverCapabilities = ServerCapabilities())
        val ex = assertFailsWith<IllegalStateException> {
            session.exposedAssertNotificationCapability(Method.Defined.NotificationsTasksStatus)
        }
        ex.message.orEmpty() shouldContain "Server does not support tasks"
    }

    private suspend fun newTestServerSession(
        clientCapabilities: ClientCapabilities = ClientCapabilities(),
        serverCapabilities: ServerCapabilities = ServerCapabilities(),
    ): TestServerSession {
        val session = TestServerSession(
            serverInfo = Implementation("test-server", "1.0.0"),
            options = ServerOptions(capabilities = serverCapabilities),
            instructions = null,
        )
        val transport = InitializeReplayTransport(clientCapabilities)
        session.connect(transport)
        return session
    }

    /**
     * Test-only [ServerSession] subclass that exposes the protected
     * [ServerSession.assertCapabilityForMethod] and
     * [ServerSession.assertNotificationCapability] helpers to the test code.
     */
    private class TestServerSession(serverInfo: Implementation, options: ServerOptions, instructions: String?) :
        ServerSession(serverInfo, options, instructions) {
        fun exposedAssertCapabilityForMethod(method: Method): Unit = assertCapabilityForMethod(method)
        fun exposedAssertNotificationCapability(method: Method): Unit = assertNotificationCapability(method)
    }

    /**
     * Minimal in-memory [Transport] that drives the server-side initialize handshake.
     *
     * On [start], it dispatches an [InitializeRequest] carrying the configured
     * [clientCapabilities] to the session via [onMessage]. The session's internal
     * `initialize` handler runs synchronously and populates
     * [ServerSession.clientCapabilities] before [start] returns. Other JSON-RPC
     * traffic the session subsequently emits is silently consumed.
     */
    private class InitializeReplayTransport(private val clientCapabilities: ClientCapabilities) : Transport {
        private var onMessageBlock: (suspend (JSONRPCMessage) -> Unit)? = null
        private var onCloseBlock: (() -> Unit)? = null

        override suspend fun start() {
            val initializeRequest = InitializeRequest(
                params = InitializeRequestParams(
                    protocolVersion = LATEST_PROTOCOL_VERSION,
                    capabilities = clientCapabilities,
                    clientInfo = Implementation("mock-client", "1.0.0"),
                ),
            ).toJSON().copy(id = RequestId.NumberId(1))
            onMessageBlock?.invoke(initializeRequest)
        }

        override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) = Unit

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
