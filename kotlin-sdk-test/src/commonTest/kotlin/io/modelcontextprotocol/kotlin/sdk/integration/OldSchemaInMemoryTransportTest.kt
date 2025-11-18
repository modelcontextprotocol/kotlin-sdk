package io.modelcontextprotocol.kotlin.sdk.integration

import io.modelcontextprotocol.kotlin.sdk.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.InMemoryTransport
import io.modelcontextprotocol.kotlin.sdk.toJSON
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OldSchemaInMemoryTransportTest {
    private lateinit var clientTransport: InMemoryTransport
    private lateinit var serverTransport: InMemoryTransport

    @BeforeTest
    fun setUp() {
        val (client, server) = InMemoryTransport.createLinkedPair()
        clientTransport = client
        serverTransport = server
    }

    @Test
    fun `should create linked pair`() {
        assertNotNull(clientTransport)
        assertNotNull(serverTransport)
    }

    @Test
    fun `should start without error`() = runTest {
        clientTransport.start()
        serverTransport.start()
        // If no exception is thrown, the test passes
    }

    @Test
    fun `should send message from client to server`() = runTest {
        val message = InitializedNotification()

        var receivedMessage: JSONRPCMessage? = null
        serverTransport.onMessage { msg ->
            receivedMessage = msg
        }

        val rpcNotification = message.toJSON()
        clientTransport.send(rpcNotification)
        assertEquals(rpcNotification, receivedMessage)
    }

    @Test
    fun `should send message from server to client`() = runTest {
        val message = InitializedNotification()
            .toJSON()

        var receivedMessage: JSONRPCMessage? = null
        clientTransport.onMessage { msg ->
            receivedMessage = msg
        }

        serverTransport.send(message)
        assertEquals(message, receivedMessage)
    }

    @Test
    fun `should handle close`() = runTest {
        var clientClosed = false
        var serverClosed = false

        clientTransport.onClose {
            clientClosed = true
        }

        serverTransport.onClose {
            serverClosed = true
        }

        clientTransport.close()
        assertTrue(clientClosed)
        assertTrue(serverClosed)
    }

    @Test
    fun `should throw error when sending after close`() = runTest {
        clientTransport.close()

        assertFailsWith<IllegalStateException> {
            clientTransport.send(
                InitializedNotification().toJSON(),
            )
        }
    }

    @Test
    fun `should queue messages sent before start`() = runTest {
        val message = InitializedNotification()
            .toJSON()

        var receivedMessage: JSONRPCMessage? = null
        serverTransport.onMessage { msg ->
            receivedMessage = msg
        }

        clientTransport.send(message)
        serverTransport.start()
        assertEquals(message, receivedMessage)
    }
}
