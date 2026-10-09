package io.modelcontextprotocol.kotlin.sdk.shared

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.InternalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalMcpApi::class)
class AbstractClientTransportInitializationTest {

    @Test
    fun `should process messages received during initialization after becoming operational`() = runTest {
        val transport = TestClientTransport(scope = this)
        val response = PingRequest().toJSON()
        val messageProcessed = CompletableDeferred<Unit>()
        transport.onMessage {
            transport.send(response)
            messageProcessed.complete(Unit)
        }
        transport.messageDuringInitialize = PingRequest().toJSON()

        withTimeout(1_000) { transport.start() }
        messageProcessed.await()

        assertEquals(response, transport.sentMessages.single())
    }

    @Test
    fun `should discard messages received when initialization fails`() = runTest {
        val transport = TestClientTransport(scope = this)
        var messageProcessed = false
        transport.onMessage { messageProcessed = true }
        transport.messageDuringInitialize = PingRequest().toJSON()
        val failure = IllegalStateException("Initialization failed")
        transport.initializeFailure = failure

        val actualFailure = runCatching { transport.start() }.exceptionOrNull()
        transport.messageJob?.join()

        assertEquals(failure, actualFailure)
        assertTrue(transport.messageJob?.isCompleted == true)
        assertFalse(messageProcessed)
    }

    @Test
    fun `should process messages received while draining in arrival order`() = runTest {
        val transport = TestClientTransport(scope = this)
        val firstMessageStarted = CompletableDeferred<Unit>()
        val releaseFirstMessage = CompletableDeferred<Unit>()
        val processedMessages = mutableListOf<Int>()
        transport.onMessage {
            val messageNumber = processedMessages.size + 1
            processedMessages.add(messageNumber)
            if (messageNumber == 1) {
                firstMessageStarted.complete(Unit)
                releaseFirstMessage.await()
            }
        }
        transport.messageDuringInitialize = PingRequest().toJSON()

        val startJob = launch(start = CoroutineStart.UNDISPATCHED) { transport.start() }
        firstMessageStarted.await()

        transport.receiveMessage(PingRequest().toJSON())
        assertEquals(listOf(1), processedMessages)

        releaseFirstMessage.complete(Unit)
        startJob.join()

        assertEquals(listOf(1, 2), processedMessages)
    }

    @Test
    fun `should report buffered message handler failures without failing initialization`() = runTest {
        val transport = TestClientTransport(scope = this)
        val handlerFailure = IllegalStateException("Message handler failed")
        var reportedError: Throwable? = null
        transport.onError { reportedError = it }
        transport.onMessage { throw handlerFailure }
        transport.messageDuringInitialize = PingRequest().toJSON()

        transport.start()

        assertEquals(handlerFailure, reportedError)
        assertEquals(ClientTransportState.Operational, transport.currentState)
    }

    @Test
    fun `should report handler cancellation and continue draining buffered messages`() = runTest {
        val transport = TestClientTransport(scope = this)
        val firstMessage = JSONRPCNotification(method = "notifications/first")
        val secondMessage = JSONRPCNotification(method = "notifications/second")
        val firstHandlerStarted = CompletableDeferred<Unit>()
        val releaseFirstHandler = CompletableDeferred<Unit>()
        val processedMessages = mutableListOf<String>()
        var reportedError: Throwable? = null
        transport.onError { reportedError = it }
        transport.onMessage { message ->
            val notification = message as JSONRPCNotification
            processedMessages.add(notification.method)
            if (notification.method == firstMessage.method) {
                firstHandlerStarted.complete(Unit)
                releaseFirstHandler.await()
                throw CancellationException("Message handler cancelled")
            }
        }
        transport.messageDuringInitialize = firstMessage

        val startJob = launch(start = CoroutineStart.UNDISPATCHED) { transport.start() }
        firstHandlerStarted.await()
        transport.receiveMessage(secondMessage)
        releaseFirstHandler.complete(Unit)
        startJob.join()

        assertEquals(listOf(firstMessage.method, secondMessage.method), processedMessages)
        assertEquals("Message handler cancelled", reportedError?.message)
        assertEquals(ClientTransportState.Operational, transport.currentState)
    }

    @Test
    fun `should release the drain gate when start is cancelled during a handler`() = runTest {
        val transport = TestClientTransport(scope = this)
        val firstMessage = JSONRPCNotification(method = "notifications/first")
        val queuedMessage = JSONRPCNotification(method = "notifications/queued")
        val laterMessage = JSONRPCNotification(method = "notifications/later")
        val firstHandlerStarted = CompletableDeferred<Unit>()
        val processedMessages = mutableListOf<String>()
        transport.onMessage { message ->
            val notification = message as JSONRPCNotification
            processedMessages.add(notification.method)
            if (notification.method == firstMessage.method) {
                firstHandlerStarted.complete(Unit)
                awaitCancellation()
            }
        }
        transport.messageDuringInitialize = firstMessage

        val startJob = launch(start = CoroutineStart.UNDISPATCHED) { transport.start() }
        firstHandlerStarted.await()
        transport.receiveMessage(queuedMessage)
        startJob.cancel(CancellationException("Cancel initialization drain"))
        startJob.join()

        assertEquals(ClientTransportState.Operational, transport.currentState)
        transport.receiveMessage(laterMessage)

        assertEquals(listOf(firstMessage.method, laterMessage.method), processedMessages)
    }

    private class TestClientTransport(private val scope: CoroutineScope) : AbstractClientTransport() {
        override val logger: KLogger = KotlinLogging.logger {}
        val sentMessages = mutableListOf<JSONRPCMessage>()
        var messageDuringInitialize: JSONRPCMessage? = null
        var initializeFailure: Exception? = null
        var messageJob: Job? = null
        val currentState: ClientTransportState
            get() = state

        suspend fun receiveMessage(message: JSONRPCMessage) {
            _onMessage(message)
        }

        override suspend fun initialize() {
            messageDuringInitialize?.let { message ->
                messageJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    _onMessage(message)
                    messageRead.complete(Unit)
                }
                messageRead.await()
            }
            initializeFailure?.let { throw it }
        }

        override suspend fun performSend(message: JSONRPCMessage, options: TransportSendOptions?) {
            sentMessages.add(message)
        }

        override suspend fun closeResources() = Unit

        private val messageRead = CompletableDeferred<Unit>()
    }
}
