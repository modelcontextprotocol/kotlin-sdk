package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.shared.InMemoryTransport
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import kotlin.test.assertEquals

class OldSchemaServerInstructionsTest {

    @Test
    fun `Server constructor should accept instructions provider parameter`() = runTest {
        val serverInfo = Implementation(name = "test server", version = "1.0")
        val serverOptions = ServerOptions(capabilities = ServerCapabilities())
        val instructions = "This is a test server. Use it for testing purposes only."

        val server = Server(serverInfo, serverOptions, { instructions })

        // The instructions should be stored internally and used in handleInitialize
        // We can't directly access the private field, but we can test it through initialization
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val client = Client(clientInfo = Implementation(name = "test client", version = "1.0"))

        server.createSession(serverTransport)
        client.connect(clientTransport)

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
        val client = Client(clientInfo = Implementation(name = "test client", version = "1.0"))

        server.createSession(serverTransport)
        client.connect(clientTransport)

        assertEquals(instructions, client.serverInstructions)
    }

    @Test
    fun `Server constructor should work without instructions parameter`() = runTest {
        val serverInfo = Implementation(name = "test server", version = "1.0")
        val serverOptions = ServerOptions(capabilities = ServerCapabilities())

        // Test that server works when instructions parameter is omitted (defaults to null)
        val server = Server(serverInfo, serverOptions)

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val client = Client(clientInfo = Implementation(name = "test client", version = "1.0"))

        server.createSession(serverTransport)
        client.connect(clientTransport)

        assertNull(client.serverInstructions)
    }
}
