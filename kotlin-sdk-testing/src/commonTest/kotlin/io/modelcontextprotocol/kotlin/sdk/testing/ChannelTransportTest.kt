package io.modelcontextprotocol.kotlin.sdk.testing

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

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
        val mutex = Mutex()
        transport.onMessage { msg ->
            mutex.withLock {
                received.add(msg)
                if (received.size == 2) {
                    messagesProcessed.complete(Unit)
                }
            }
        }

        transport.start()

        val msg1 = JSONRPCRequest(RequestId.NumberId(1), "method1")
        val msg2 = JSONRPCRequest(RequestId.NumberId(2), "method2")
        receiveChannel.send(msg1)
        receiveChannel.send(msg2)

        // Wait for messages to be processed
        messagesProcessed.await()

        received.shouldContainExactlyInAnyOrder(msg1, msg2)
    }

    @Test
    fun `start completes when channel closes`() = runTest {
        val receiveChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
        val transport = ChannelTransport(Channel(), receiveChannel)

        val messageProcessed = CompletableDeferred<Unit>()
        val received = mutableListOf<JSONRPCMessage>()
        val mutex = Mutex()
        transport.onMessage {
            mutex.withLock {
                received.add(it)
                messageProcessed.complete(Unit)
            }
        }

        transport.start()

        val msg = JSONRPCRequest(RequestId.NumberId(1), "method1")
        receiveChannel.send(msg)
        receiveChannel.close() // Close the channel

        // Wait for a message to be processed
        messageProcessed.await()

        received.shouldContainExactlyInAnyOrder(msg)
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
        val mutex = Mutex()
        transport.onMessage {
            mutex.withLock {
                calls.add("first")
            }
        }
        transport.onMessage {
            mutex.withLock {
                calls.add("second")
                messageProcessed.complete(Unit)
            }
        }

        val startJob = backgroundScope.launch { transport.start() }

        receiveChannel.send(JSONRPCRequest(RequestId.NumberId(1), "test"))
        messageProcessed.await()

        calls.shouldContainExactlyInAnyOrder("first", "second")
        startJob.cancelAndJoin()
    }

    @Test
    fun `secondary constructor uses single channel`() = runTest {
        val channel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
        val transport = ChannelTransport(channel)

        val messageProcessed = CompletableDeferred<Unit>()
        val received = mutableListOf<JSONRPCMessage>()
        val mutex = Mutex()
        transport.onMessage { msg ->
            mutex.withLock {
                received.add(msg)
                messageProcessed.complete(Unit)
            }
        }

        transport.start()

        val message = JSONRPCRequest(RequestId.NumberId(1), "test")
        transport.send(message)
        messageProcessed.await()

        eventually(2.seconds) {
            received.shouldContainExactlyInAnyOrder(message)
        }
    }

    @Test
    fun `send to closed channel triggers error`() = runTest {
        val sendChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
        val transport = ChannelTransport(sendChannel, Channel())

        transport.start()

        var errorCaught = false
        transport.onError {
            it.shouldBeInstanceOf<ClosedSendChannelException>()
            errorCaught = true
        }
        sendChannel.close()

        try {
            transport.send(JSONRPCRequest(RequestId.NumberId(1), "method"))
        } catch (e: Exception) {
            // send() wraps ClosedSendChannelException in McpException
            e.shouldBeInstanceOf<io.modelcontextprotocol.kotlin.sdk.types.McpException>()
        }

        eventually(2.seconds) {
            errorCaught shouldBe true
        }
    }

    @Test
    fun `exceptions in message handler do not stop processing`() = runTest {
        val receiveChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
        val transport = ChannelTransport(Channel(), receiveChannel)

        val allProcessed = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val errors = Channel<Throwable>(Channel.UNLIMITED)
        val mutex = Mutex()

        transport.onError { error ->
            errors.trySend(error)
        }
        transport.onMessage { msg ->
            val id = ((msg as JSONRPCRequest).id as RequestId.NumberId).value.toInt()
            mutex.withLock {
                received.add(id)
                if (id == 2) {
                    @Suppress("TooGenericExceptionThrown")
                    throw RuntimeException("Error processing message 2")
                }
                if (received.size == 4) {
                    allProcessed.complete(Unit)
                }
            }
        }

        transport.start()

        // Send 4 messages, second one will throw
        receiveChannel.send(JSONRPCRequest(RequestId.NumberId(1), "m1"))
        receiveChannel.send(JSONRPCRequest(RequestId.NumberId(2), "m2"))
        delay(100)
        receiveChannel.send(JSONRPCRequest(RequestId.NumberId(3), "m3"))
        receiveChannel.send(JSONRPCRequest(RequestId.NumberId(4), "m4"))

        allProcessed.await()
        transport.close()

        // All messages should be processed despite error in message 2
        received.shouldContainExactlyInAnyOrder(1, 2, 3, 4)
        val capturedErrors = mutableListOf<Throwable>()
        eventually(2.seconds) {
            while (true) {
                val error = errors.tryReceive().getOrNull() ?: break
                capturedErrors.add(error)
            }
            capturedErrors.size shouldBe 1
            capturedErrors[0].message shouldBe "Error processing message 2"
        }
    }
}
