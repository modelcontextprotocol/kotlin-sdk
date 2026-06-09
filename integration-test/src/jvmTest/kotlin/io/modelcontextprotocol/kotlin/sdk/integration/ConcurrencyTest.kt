package io.modelcontextprotocol.kotlin.sdk.integration

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * Tests that the Protocol layer handles incoming messages concurrently,
 * preventing deadlock when a request handler needs to wait for other messages.
 *
 * See: https://github.com/modelcontextprotocol/kotlin-sdk/issues/176
 */
class ConcurrencyTest {

    /**
     * A channel-based transport that delivers messages asynchronously via Kotlin Channels,
     * simulating real network transports. This is necessary to reproduce the concurrency
     * bug — the synchronous InMemoryTransport masks the issue.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private class ChannelTransport(
        private val scope: CoroutineScope,
        private val sendChannel: Channel<JSONRPCMessage>,
        private val receiveChannel: Channel<JSONRPCMessage>,
    ) : AbstractTransport() {
        override suspend fun start() {
            scope.launch {
                for (message in receiveChannel) {
                    _onMessage.invoke(message)
                }
            }
        }

        override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
            sendChannel.send(message)
        }

        override suspend fun close() {
            sendChannel.close()
            receiveChannel.cancel()
            invokeOnCloseCallback()
        }

        companion object {
            fun createLinkedPair(scope: CoroutineScope): Pair<ChannelTransport, ChannelTransport> {
                val clientToServer = Channel<JSONRPCMessage>(Channel.UNLIMITED)
                val serverToClient = Channel<JSONRPCMessage>(Channel.UNLIMITED)
                return Pair(
                    ChannelTransport(scope, serverToClient, clientToServer),
                    ChannelTransport(scope, clientToServer, serverToClient),
                )
            }
        }
    }

    /**
     * Verifies that concurrent tool calls are handled concurrently, not serially.
     *
     * Uses deterministic synchronization: the fast tool completes while the slow
     * handler is still suspended, proving that handlers run concurrently rather
     * than serially. No wall-clock timing thresholds are used.
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun `server handles concurrent requests concurrently`() = runBlocking {
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(null)),
        )
        serverOptions.concurrentMessageHandling = true

        val server = Server(
            serverInfo = Implementation("test-server", "1.0"),
            options = serverOptions,
        )

        // Latch that signals when the slow handler has started and suspended.
        // This ensures the fast request arrives while the slow handler is already
        // blocking, proving the test is a true concurrency regression test.
        val slowHandlerStarted = CompletableDeferred<Unit>()

        // Latch that blocks the slow handler until we signal it to finish.
        // This lets us prove the fast handler completed while the slow one
        // was still running — impossible under serial dispatch.
        val slowHandlerCanFinish = CompletableDeferred<Unit>()

        server.addTool("slow_tool", "A tool that blocks until signaled") {
            slowHandlerStarted.complete(Unit)
            slowHandlerCanFinish.await()
            CallToolResult(content = listOf(TextContent("slow_tool_done")))
        }

        server.addTool("fast_tool", "A tool that completes immediately") {
            CallToolResult(content = listOf(TextContent("fast_tool_done")))
        }

        val client = Client(
            clientInfo = Implementation("test-client", "1.0"),
            options = ClientOptions(),
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair(scope)
        val serverSessionResult = CompletableDeferred<ServerSession>()

        try {
            listOf(
                launch { client.connect(clientTransport) },
                launch { serverSessionResult.complete(server.createSession(serverTransport)) },
            ).joinAll()

            // Start the slow request (handler blocks on slowHandlerCanFinish)
            val slowResult = CompletableDeferred<CallToolResult>()
            launch {
                slowResult.complete(client.callTool("slow_tool", mapOf()))
            }

            // Wait until the slow handler has actually started and suspended,
            // so the fast request arrives while the slow handler is blocking.
            withTimeout(5.seconds) { slowHandlerStarted.await() }

            // Start the fast request
            val fastResult = CompletableDeferred<CallToolResult>()
            launch {
                fastResult.complete(client.callTool("fast_tool", mapOf()))
            }

            // The fast request must complete while the slow handler is still suspended.
            // Under serial dispatch, both requests would be blocked behind the slow handler,
            // so the fast result could never arrive.
            val fast = withTimeout(5.seconds) { fastResult.await() }
            assertNotNull(fast)

            // Now release the slow handler and verify it completes
            slowHandlerCanFinish.complete(Unit)
            val slow = withTimeout(5.seconds) { slowResult.await() }
            assertNotNull(slow)
            Unit
        } finally {
            clientTransport.close()
            serverTransport.close()
            scope.cancel()
        }
    }
}