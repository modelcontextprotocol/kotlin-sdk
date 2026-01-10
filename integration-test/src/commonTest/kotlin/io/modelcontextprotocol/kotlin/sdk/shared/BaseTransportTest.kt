package io.modelcontextprotocol.kotlin.sdk.shared

import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

abstract class BaseTransportTest {

    protected suspend fun testTransportOpenClose(transport: Transport) {
        transport.onError { error ->
            fail("Unexpected error: $error")
        }

        var didClose = false
        transport.onClose { didClose = true }

        transport.start()
        delay(1.seconds)

        assertFalse(didClose, "Transport should not be closed immediately after start")

        transport.close()
        assertTrue(didClose, "Transport should be closed after close() call")
    }

    protected suspend fun testTransportRead(transport: Transport) {
        transport.onError { error ->
            error.printStackTrace()
            fail("Unexpected error: $error")
        }

        val messages = listOf(
            PingRequest().toJSON(),
            InitializedNotification().toJSON(),
        )

        val readMessages = mutableListOf<JSONRPCMessage>()
        val finished = CompletableDeferred<Unit>()

        transport.onMessage { message ->
            readMessages.add(message)
            if (message == messages.last()) {
                finished.complete(Unit)
            }
        }

        transport.start()

        for (message in messages) {
            transport.send(message)
        }

        finished.await()

        assertEquals(messages, readMessages, "Assert messages received")

        transport.close()
    }
}
