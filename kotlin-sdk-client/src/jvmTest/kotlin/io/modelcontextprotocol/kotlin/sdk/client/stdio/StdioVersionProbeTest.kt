// ABOUTME: Client.connect under VersionNegotiationMode.Auto over a real stdio transport against a scripted server.
// ABOUTME: Pins that a legacy server ignoring the server/discover probe is reached by a handshake after a bounded wait.
package io.modelcontextprotocol.kotlin.sdk.client.stdio

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.VersionNegotiationMode
import io.modelcontextprotocol.kotlin.sdk.shared.DEFAULT_REQUEST_TIMEOUT
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_HANDSHAKE_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread
import kotlin.test.Test

private const val PIPE_SIZE = 64 * 1024

@OptIn(ExperimentalMcpApi::class)
class StdioVersionProbeTest {

    @Test
    fun `should fall back well before the request timeout when the server ignores the probe`(): Unit =
        runBlocking(Dispatchers.IO) {
            val serverStdout = PipedOutputStream()
            val clientStdin = PipedInputStream(serverStdout, PIPE_SIZE)
            val clientStdout = PipedOutputStream()
            val serverStdin = PipedInputStream(clientStdout, PIPE_SIZE)
            val received = mutableListOf<String>()

            // A legacy server that never answers a request it does not know, as the spec allows.
            thread(isDaemon = true) {
                serverStdin.bufferedReader().lineSequence().forEach { line ->
                    val message = McpJson.decodeFromString<JSONRPCMessage>(line)
                    if (message !is JSONRPCRequest) return@forEach
                    synchronized(received) { received += message.method }
                    if (message.method != "initialize") return@forEach
                    val response = JSONRPCResponse(
                        id = message.id,
                        result = InitializeResult(
                            protocolVersion = LATEST_HANDSHAKE_VERSION,
                            capabilities = ServerCapabilities(),
                            serverInfo = Implementation("silent-legacy-server", "1.0.0"),
                        ),
                    )
                    val reply = McpJson.encodeToString(JSONRPCMessage.serializer(), response) + "\n"
                    serverStdout.write(reply.toByteArray())
                    serverStdout.flush()
                }
            }

            val transport = StdioClientTransport(
                input = clientStdin.asSource().buffered(),
                output = clientStdout.asSink().buffered(),
            )
            val client = Client(
                Implementation("test-client", "1.0.0"),
                ClientOptions(versionNegotiation = VersionNegotiationMode.Auto),
            )

            // The server hangs up at half the request timeout, so a client still waiting on the probe
            // fails to connect. Ending the stream is also the only way to stop the blocking stdin read.
            val hangUp = launch {
                delay(DEFAULT_REQUEST_TIMEOUT / 2)
                serverStdout.close()
            }
            try {
                client.connect(transport)

                client.protocolVersion shouldBe LATEST_HANDSHAKE_VERSION
                synchronized(received) { received.toList() } shouldBe listOf("server/discover", "initialize")
            } finally {
                hangUp.cancel()
                serverStdout.close()
                client.close()
            }
        }
}
