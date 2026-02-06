package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.CompletableDeferred
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

        // Verify transport stays open after start (use eventually for cross-platform reliability)
        eventually(2.seconds) {
            didClose shouldBe false
        }

        transport.close()

        // Verify transport is closed after close() call
        eventually(2.seconds) {
            didClose shouldBe true
        }
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

        messages shouldBe readMessages

        transport.close()
    }
}
