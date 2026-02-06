package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.mcpClient
import io.modelcontextprotocol.kotlin.sdk.shared.InMemoryTransport
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import kotlin.test.assertEquals

@OptIn(ExperimentalMcpApi::class)
class ServerInstructionsTest {

    @Test
    fun `Server constructor should accept instructions provider parameter`() = runTest {
        val serverInfo = Implementation(name = "test server", version = "1.0")
        val serverOptions = ServerOptions(capabilities = ServerCapabilities())
        val instructions = "This is a test server. Use it for testing purposes only."

        val server = Server(serverInfo, serverOptions, { instructions })

        // The instructions should be stored internally and used in handleInitialize
        // We can't directly access the private field, but we can test it through initialization
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        server.createSession(serverTransport)

        val client = mcpClient(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            clientOptions = ClientOptions(
                capabilities = ClientCapabilities(
                    roots = ClientCapabilities.Roots(listChanged = false),
                ),
            ),
            transport = clientTransport,
        )

        assertEquals(instructions, client.serverInstructions)
    }

    @Test
    fun `Server constructor should accept instructions parameter`() = runTest {
        val serverInfo = Implementation(name = "test server", version = "1.0")
        val serverOptions = ServerOptions(capabilities = ServerCapabilities())
        val instructions = "This is a test server. Use it for testing purposes only."

        val server = Server(serverInfo, serverOptions, instructions)

        // The instructions should be stored internally and used in handleInitialize
        // We can't directly access the private field, but we can test it through initialization
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        server.createSession(serverTransport)

        val client = mcpClient(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            transport = clientTransport,
        )

        assertEquals(instructions, client.serverInstructions)
    }

    @Test
    fun `Server constructor should work without instructions parameter`() = runTest {
        val serverInfo = Implementation(name = "test server", version = "1.0")
        val serverOptions = ServerOptions(capabilities = ServerCapabilities())

        // Test that server works when instructions parameter is omitted (defaults to null)
        val server = Server(serverInfo, serverOptions)

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        server.createSession(serverTransport)

        val client = mcpClient(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            transport = clientTransport,
        )

        assertNull(client.serverInstructions)
    }
}
