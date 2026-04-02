package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotification
import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.CustomRequest
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import io.modelcontextprotocol.kotlin.test.utils.runIntegrationTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class ProtocolConcurrentTest {

    private lateinit var protocol: ConcurrentTestProtocol
    private lateinit var transport: ConcurrentTestTransport

    @BeforeEach
    fun setUp() {
        protocol = ConcurrentTestProtocol()
        transport = ConcurrentTestTransport()
    }

    @Test
    fun `should handle requests concurrently without blocking ping`() = runIntegrationTest(timeout = 10.seconds) {
        val slowStarted = CompletableDeferred<Unit>()
        val slowGate = CompletableDeferred<Unit>()

        protocol.setRequestHandler<CustomRequest>(Method.Custom("slow")) { _, _ ->
            slowStarted.complete(Unit)
            slowGate.await()
            EmptyResult()
        }

        protocol.connect(transport)

        // Start a slow request
        transport.deliver(JSONRPCRequest(id = RequestId.NumberId(1), method = "slow"))
        withTimeout(5.seconds) { slowStarted.await() }

        // Ping should not be blocked by the slow handler
        transport.deliver(JSONRPCRequest(id = RequestId.NumberId(2), method = Method.Defined.Ping.value))

        val pingResponse = withTimeout(5.seconds) { transport.awaitResponse() }
        (pingResponse as JSONRPCResponse).id shouldBe RequestId.NumberId(2)

        // Release slow handler and collect its response
        slowGate.complete(Unit)
        val slowResponse = withTimeout(5.seconds) { transport.awaitResponse() }
        (slowResponse as JSONRPCResponse).id shouldBe RequestId.NumberId(1)
    }

    @Test
    fun `should cancel active request on CancelledNotification`() = runIntegrationTest(timeout = 10.seconds) {
        val handlerStarted = CompletableDeferred<Unit>()
        var handlerCancelled = false

        protocol.setRequestHandler<CustomRequest>(Method.Custom("cancellable")) { _, _ ->
            handlerStarted.complete(Unit)
            try {
                delay(60.seconds)
                EmptyResult()
            } catch (e: kotlinx.coroutines.CancellationException) {
                handlerCancelled = true
                throw e
            }
        }

        protocol.connect(transport)

        transport.deliver(JSONRPCRequest(id = RequestId.NumberId(42), method = "cancellable"))
        withTimeout(5.seconds) { handlerStarted.await() }

        // Send cancellation
        transport.deliver(
            CancelledNotification(
                CancelledNotificationParams(requestId = RequestId.NumberId(42), reason = "test"),
            ).toJSON(),
        )

        withTimeout(5.seconds) {
            while (!handlerCancelled) delay(10)
        }
        handlerCancelled shouldBe true
    }
}

private class ConcurrentTestProtocol : Protocol(null) {
    override fun assertCapabilityForMethod(method: Method) {}
    override fun assertNotificationCapability(method: Method) {}
    override fun assertRequestHandlerCapability(method: Method) {}
}

private class ConcurrentTestTransport : Transport {
    private val sentMessages = Channel<JSONRPCMessage>(Channel.UNLIMITED)
    private var onMessageCallback: (suspend (JSONRPCMessage) -> Unit)? = null
    private var onCloseCallback: (() -> Unit)? = null

    override suspend fun start() {}

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        sentMessages.send(message)
    }

    override suspend fun close() {
        onCloseCallback?.invoke()
    }

    override fun onClose(block: () -> Unit) {
        onCloseCallback = block
    }

    override fun onError(block: (Throwable) -> Unit) {}

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        onMessageCallback = block
    }

    suspend fun deliver(message: JSONRPCMessage) {
        val callback = onMessageCallback ?: error("onMessage callback not registered")
        callback(message)
    }

    suspend fun awaitResponse(): JSONRPCMessage = sentMessages.receive()
}
