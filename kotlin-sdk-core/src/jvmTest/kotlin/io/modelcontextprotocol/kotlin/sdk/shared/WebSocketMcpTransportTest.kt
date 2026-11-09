package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameTooBigException
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

class WebSocketMcpTransportTest {

    @Test
    fun `should keep reading after messages it cannot parse`() = runTest {
        val session = FakeWebSocketSession()
        val transport = TestTransport(session)
        val errors = CopyOnWriteArrayList<Throwable>()
        val received = CompletableDeferred<JSONRPCMessage>()
        transport.onError { errors += it }
        transport.onMessage { received.complete(it) }
        transport.start()

        session.incomingFrames.send(Frame.Binary(true, byteArrayOf(1, 2, 3)))
        session.incomingFrames.send(Frame.Text("{"))
        session.incomingFrames.send(Frame.Text("""{"jsonrpc":"2.0","id":1,"method":"ping"}"""))

        received.await().shouldBeInstanceOf<JSONRPCRequest>().method shouldBe "ping"
        errors shouldHaveSize 2
        session.incomingFrames.close()
    }

    @Test
    fun `should report a session failure through onError`() = runTest {
        val session = FakeWebSocketSession()
        val transport = TestTransport(session)
        val error = CompletableDeferred<Throwable>()
        transport.onError { error.complete(it) }
        transport.start()

        session.incomingFrames.close(FrameTooBigException(42))

        error.await().shouldBeInstanceOf<FrameTooBigException>()
    }

    private class TestTransport(override val session: WebSocketSession) : WebSocketMcpTransport() {
        override suspend fun initializeSession() = Unit
    }
}

private class FakeWebSocketSession : WebSocketSession {
    val incomingFrames = Channel<Frame>(Channel.UNLIMITED)

    override val coroutineContext: CoroutineContext = Job()
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE
    override val incoming: ReceiveChannel<Frame> get() = incomingFrames
    override val outgoing: SendChannel<Frame> = Channel(Channel.UNLIMITED)
    override val extensions: List<WebSocketExtension<*>> = emptyList()

    override suspend fun flush() = Unit

    @Deprecated("Use cancel() instead.", level = DeprecationLevel.ERROR)
    override fun terminate() = Unit
}
