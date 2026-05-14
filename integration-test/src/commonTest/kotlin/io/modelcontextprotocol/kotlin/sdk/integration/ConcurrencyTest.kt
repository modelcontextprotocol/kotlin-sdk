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
import kotlinx.coroutines.delay
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
     * Uses real Dispatchers instead of runTest's virtual time to allow actual concurrent execution.
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun `server handles concurrent requests concurrently`() = runBlocking {
        val slowToolDelay = 500L

        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(null)),
        )
        serverOptions.concurrentMessageHandling = true

        val server = Server(
            serverInfo = Implementation("test-server", "1.0"),
            options = serverOptions,
        )

        server.addTool("slow_tool", "A tool that takes a while") {
            delay(slowToolDelay)
            CallToolResult(content = listOf(TextContent("slow_tool_done")))
        }
        server.addTool("fast_tool", "A tool that is quick") {
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

            val startTime = System.currentTimeMillis()

            val slowResult = CompletableDeferred<CallToolResult>()
            val fastResult = CompletableDeferred<CallToolResult>()

            launch {
                slowResult.complete(client.callTool("slow_tool", mapOf()))
            }

            delay(50)

            launch {
                fastResult.complete(client.callTool("fast_tool", mapOf()))
            }

            val slow = withTimeout(5.seconds) { slowResult.await() }
            val fast = withTimeout(5.seconds) { fastResult.await() }

            assertNotNull(slow)
            assertNotNull(fast)

            val elapsed = System.currentTimeMillis() - startTime
            // If concurrent: elapsed ≈ slowToolDelay + overhead
            // If serial: elapsed ≈ slowToolDelay * 2
            if (elapsed >= slowToolDelay * 2L) {
                throw AssertionError(
                    "Fast tool was blocked by slow tool. Total duration ${elapsed}ms " +
                        "should be < ${slowToolDelay * 2}ms.",
                )
            }
        } finally {
            // Clean up: close transports and cancel scope to prevent coroutine leaks
            clientTransport.close()
            serverTransport.close()
            scope.cancel()
        }
    }
}
