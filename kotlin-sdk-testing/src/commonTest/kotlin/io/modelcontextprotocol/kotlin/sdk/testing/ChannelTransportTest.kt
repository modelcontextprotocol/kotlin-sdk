package io.modelcontextprotocol.kotlin.sdk.testing

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class ChannelTransportTest {

    @Test
    fun `send writes to sendChannel`() = runTest {
        val sendChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
        val transport = ChannelTransport(sendChannel, Channel())

        transport.start()

        val message = JSONRPCRequest(RequestId.NumberId(42), "test")
        transport.send(message)

        sendChannel.receive() shouldBe message
    }

    @Test
    fun `start reads from receiveChannel and invokes onMessage`() = runTest {
        val receiveChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
        val transport = ChannelTransport(Channel(), receiveChannel)

        val messagesProcessed = CompletableDeferred<Unit>()
        val received = mutableListOf<JSONRPCMessage>()
        transport.onMessage { msg ->
            received.add(msg)
            if (received.size == 2) {
                messagesProcessed.complete(Unit)
            }
        }

        transport.start()

        val msg1 = JSONRPCRequest(RequestId.NumberId(1), "method1")
        val msg2 = JSONRPCRequest(RequestId.NumberId(2), "method2")
        receiveChannel.send(msg1)
        receiveChannel.send(msg2)

        // Wait for messages to be processed
        messagesProcessed.await()

        received.shouldContainExactly(msg1, msg2)
    }

    @Test
    fun `delivers pending messages and closes when the receive channel closes`() = runTest {
        val receiveChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
        val transport = ChannelTransport(Channel(), receiveChannel)

        val received = mutableListOf<JSONRPCMessage>()
        val closed = CompletableDeferred<Unit>()
        transport.onMessage { received.add(it) }
        transport.onClose { closed.complete(Unit) }

        transport.start()

        val msg = JSONRPCRequest(RequestId.NumberId(1), "method1")
        receiveChannel.send(msg)
        receiveChannel.close()
        closed.await()

        received.shouldContainExactly(msg)
    }

    @Test
    fun `close is idempotent`() = runTest {
        val transport = ChannelTransport()

        var closeCount = 0
        transport.onClose { closeCount++ }

        transport.start()

        transport.close()
        transport.close()

        closeCount shouldBe 1
    }

    @Test
    fun `callbacks chain not replace`() = runTest {
        val receiveChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
        val transport = ChannelTransport(Channel(), receiveChannel)

        val messageProcessed = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        transport.onMessage {
            calls.add("first")
        }
        transport.onMessage {
            calls.add("second")
            messageProcessed.complete(Unit)
        }

        transport.start()

        receiveChannel.send(JSONRPCRequest(RequestId.NumberId(1), "test"))
        messageProcessed.await()

        calls.shouldContainExactly("first", "second")
    }

    @Test
    fun `secondary constructor uses single channel`() = runTest {
        val channel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
        val transport = ChannelTransport(channel)

        val messageProcessed = CompletableDeferred<Unit>()
        val received = mutableListOf<JSONRPCMessage>()
        transport.onMessage { msg ->
            received.add(msg)
            messageProcessed.complete(Unit)
        }

        transport.start()

        val message = JSONRPCRequest(RequestId.NumberId(1), "test")
        transport.send(message)
        messageProcessed.await()

        received.shouldContainExactly(message)
    }

    @Test
    fun `exceptions in message handler do not stop processing`() = runTest {
        val receiveChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
        val transport = ChannelTransport(Channel(), receiveChannel)

        val allProcessed = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val errors = mutableListOf<Throwable>()

        transport.onError { errors.add(it) }
        transport.onMessage { msg ->
            val id = ((msg as JSONRPCRequest).id as RequestId.NumberId).value.toInt()
            received.add(id)
            if (id == 2) {
                throw RuntimeException("Error processing message 2")
            }
            if (received.size == 4) {
                allProcessed.complete(Unit)
            }
        }

        transport.start()

        // Send 4 messages, second one will throw
        receiveChannel.send(JSONRPCRequest(RequestId.NumberId(1), "m1"))
        receiveChannel.send(JSONRPCRequest(RequestId.NumberId(2), "m2"))
        receiveChannel.send(JSONRPCRequest(RequestId.NumberId(3), "m3"))
        receiveChannel.send(JSONRPCRequest(RequestId.NumberId(4), "m4"))

        allProcessed.await()

        // All messages should be processed despite error in message 2
        received.shouldContainExactly(1, 2, 3, 4)
        errors.size shouldBe 1
        errors[0].message shouldBe "Error processing message 2"
    }
}
