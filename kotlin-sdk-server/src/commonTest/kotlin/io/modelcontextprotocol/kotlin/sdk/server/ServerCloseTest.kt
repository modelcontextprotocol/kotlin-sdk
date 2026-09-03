package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ServerCloseTest {
    @Test
    fun `session close awaits suspending cleanup`() = runTest {
        val session = ServerSession(
            serverInfo = Implementation(name = "test-server", version = "1.0"),
            options = testServerOptions(),
            instructions = null,
        )
        val transport = CloseCallbackTransport()
        session.connect(transport)
        val releaseCleanup = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        session.onClose {
            events += "started"
            cleanupStarted.complete(Unit)
            releaseCleanup.await()
            events += "finished"
        }

        val closeJob = launch { session.close() }

        cleanupStarted.await()
        closeJob.isActive shouldBe true
        events shouldContainExactly listOf("started")

        releaseCleanup.complete(Unit)
        closeJob.join()
        events shouldContainExactly listOf("started", "finished")
    }

    @Test
    fun `server close awaits suspending cleanup`() = runTest {
        val server = Server(
            serverInfo = Implementation(name = "test-server", version = "1.0"),
            options = testServerOptions(),
        )
        val releaseCleanup = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        server.onClose {
            events += "started"
            cleanupStarted.complete(Unit)
            releaseCleanup.await()
            events += "finished"
        }

        val closeJob = launch { server.close() }

        cleanupStarted.await()
        closeJob.isActive shouldBe true
        events shouldContainExactly listOf("started")

        releaseCleanup.complete(Unit)
        closeJob.join()
        events shouldContainExactly listOf("started", "finished")
    }

    private fun testServerOptions() = ServerOptions(capabilities = ServerCapabilities())

    private class CloseCallbackTransport : Transport {
        private var closeCallback: suspend () -> Unit = {}

        override suspend fun start() = Unit

        override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) = Unit

        override suspend fun close() {
            closeCallback()
        }

        override fun onClose(block: suspend () -> Unit) {
            closeCallback = block
        }

        override fun onError(block: (Throwable) -> Unit) = Unit

        override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) = Unit
    }
}
