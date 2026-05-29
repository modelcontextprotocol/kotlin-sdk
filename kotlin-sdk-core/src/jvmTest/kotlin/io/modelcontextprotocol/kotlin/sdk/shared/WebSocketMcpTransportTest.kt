package io.modelcontextprotocol.kotlin.sdk.shared

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.fail

class WebSocketMcpTransportTest {
    @Test
    fun `should continue receiving while previous handler is suspended`() = runTest {
        val session = TestWebSocketSession(coroutineContext)
        val transport = TestWebSocketMcpTransport(session)
        val firstMessage = PingRequest().toJSON()
        val secondMessage = InitializedNotification().toJSON()
        val releaseFirstHandler = CompletableDeferred<Unit>()
        val secondMessageProcessed = CompletableDeferred<Unit>()

        transport.onMessage { message ->
            if (message == firstMessage) {
                releaseFirstHandler.await()
            }
            if (message == secondMessage) {
                secondMessageProcessed.complete(Unit)
            }
        }

        transport.start()

        session.incoming.send(Frame.Text(serializeMessage(firstMessage)))
        session.incoming.send(Frame.Text(serializeMessage(secondMessage)))

        secondMessageProcessed.await()
        releaseFirstHandler.complete(Unit)
        transport.close()
    }

    @Test
    fun `close cancels suspended message handler`() = runTest {
        val session = TestWebSocketSession(coroutineContext)
        val transport = TestWebSocketMcpTransport(session)
        val handlerStarted = CompletableDeferred<Unit>()
        val handlerCancelled = CompletableDeferred<Unit>()

        transport.onMessage {
            handlerStarted.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
            } catch (e: CancellationException) {
                handlerCancelled.complete(Unit)
                throw e
            }
        }

        transport.start()

        session.incoming.send(Frame.Text(serializeMessage(PingRequest().toJSON())))
        handlerStarted.await()
        transport.close()

        handlerCancelled.await()
    }

    @Test
    fun `handler errors are reported via onError`() = runTest {
        val session = TestWebSocketSession(coroutineContext)
        val transport = TestWebSocketMcpTransport(session)
        val expected = IllegalStateException("handler failed")
        val capturedError = CompletableDeferred<Throwable>()

        transport.onError { capturedError.complete(it) }
        transport.onMessage { throw expected }
        transport.start()

        session.incoming.send(Frame.Text(serializeMessage(PingRequest().toJSON())))

        assertSame(expected, capturedError.await())
        transport.close()
    }

    @Test
    fun `handler cancellation is not reported via onError`() = runTest {
        val session = TestWebSocketSession(coroutineContext)
        val transport = TestWebSocketMcpTransport(session)
        val capturedError = CompletableDeferred<Throwable>()
        val handlerStarted = CompletableDeferred<Unit>()

        transport.onError { capturedError.complete(it) }
        transport.onMessage {
            handlerStarted.complete(Unit)
            throw CancellationException("handler cancelled")
        }
        transport.start()

        session.incoming.send(Frame.Text(serializeMessage(PingRequest().toJSON())))
        handlerStarted.await()

        if (capturedError.isCompleted) {
            fail("CancellationException should not be reported via onError")
        }
        transport.close()
    }

    private class TestWebSocketMcpTransport(
        override val session: WebSocketSession,
    ) : WebSocketMcpTransport() {
        override suspend fun initializeSession() = Unit
    }

    private class TestWebSocketSession(
        parentContext: CoroutineContext,
    ) : WebSocketSession {
        private val job = Job(parentContext[Job])

        override val coroutineContext: CoroutineContext = parentContext + job
        override var masking: Boolean = false
        override var maxFrameSize: Long = Long.MAX_VALUE
        override val incoming = Channel<Frame>(Channel.UNLIMITED)
        override val outgoing = Channel<Frame>(Channel.UNLIMITED)
        override val extensions: List<WebSocketExtension<*>> = emptyList()

        override suspend fun flush() = Unit

        override suspend fun send(frame: Frame) {
            outgoing.send(frame)
            if (frame is Frame.Close) {
                job.cancel()
                incoming.close()
                outgoing.close()
            }
        }

        @Deprecated(
            "Use cancel() instead.",
            replaceWith = ReplaceWith("cancel()", "kotlinx.coroutines.cancel"),
            level = DeprecationLevel.ERROR
        )
        override fun terminate() {
            job.cancel()
            incoming.close()
            outgoing.close()
        }
    }
}
