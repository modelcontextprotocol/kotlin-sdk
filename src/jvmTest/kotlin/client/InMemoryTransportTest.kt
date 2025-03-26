package client

import InMemoryTransport
import io.modelcontextprotocol.kotlin.sdk.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import kotlinx.coroutines.runBlocking
import io.modelcontextprotocol.kotlin.sdk.toJSON
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InMemoryTransportTest {
    private lateinit var clientTransport: InMemoryTransport
    private lateinit var serverTransport: InMemoryTransport

    @BeforeEach
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
    fun `should start without error`() {
        runBlocking {
            clientTransport.start()
            serverTransport.start()
            // If no exception is thrown, the test passes
        }
    }

    @Test
    fun `should send message from client to server`() {
        runBlocking {
            val message = InitializedNotification()

            var receivedMessage: JSONRPCMessage? = null
            serverTransport.onMessage { msg ->
                receivedMessage = msg
            }

            val rpcNotification = message.toJSON()
            clientTransport.send(rpcNotification)
            assertEquals(rpcNotification, receivedMessage)
        }
    }

    @Test
    fun `should send message from server to client`() {
        runBlocking {
            val message = InitializedNotification()
                .toJSON()

            var receivedMessage: JSONRPCMessage? = null
            clientTransport.onMessage { msg ->
                receivedMessage = msg
            }

            serverTransport.send(message)
            assertEquals(message, receivedMessage)
        }
    }

    @Test
    fun `should handle close`() {
        runBlocking {
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
    }

    @Test
    fun `should throw error when sending after close`() {
        runBlocking {
            clientTransport.close()

            assertThrows<IllegalStateException> {
                clientTransport.send(
                    InitializedNotification().toJSON()
                )
            }
        }
    }

    @Test
    fun `should queue messages sent before start`() {
        runBlocking {
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
}
