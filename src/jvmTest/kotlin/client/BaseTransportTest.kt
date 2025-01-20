package client

import io.modelcontextprotocol.kotlin.sdk.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.PingRequest
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

abstract class BaseTransportTest {
    protected suspend fun testClientOpenClose(client: Transport) {
        client.onError { error ->
            fail("Unexpected error: $error")
        }

        var didClose = false
        client.onClose { didClose = true }

        client.start()
        assertFalse(didClose, "Transport should not be closed immediately after start")

        client.close()
        assertTrue(didClose, "Transport should be closed after close() call")
    }

    protected suspend fun testClientRead(client: Transport) {
        client.onError { error ->
            error.printStackTrace()
            fail("Unexpected error: $error")
        }

        val messages = listOf<JSONRPCMessage>(
            PingRequest().toJSON(), InitializedNotification().toJSON()
        )

        val readMessages = mutableListOf<JSONRPCMessage>()
        val finished = CompletableDeferred<Unit>()

        client.onMessage { message ->
            readMessages.add(message)
            if (message == messages.last()) {
                finished.complete(Unit)
            }
        }

        client.start()

        for (message in messages) {
            client.send(message)
        }

        finished.await()

        assertEquals(messages, readMessages, "Assert messages received")

        client.close()
    }
}
