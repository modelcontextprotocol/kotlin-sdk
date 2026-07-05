package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.types.CustomRequest
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ProtocolTimeoutTest {
    private lateinit var protocol: TimeoutTestProtocol
    private lateinit var transport: TimeoutTestTransport

    @BeforeTest
    fun setUp() {
        protocol = TimeoutTestProtocol()
        transport = TimeoutTestTransport()
    }

    private fun newRequest() = CustomRequest(method = Method.Custom("example"), params = null)

    @Test
    fun `should fail request with REQUEST_TIMEOUT when no response arrives within timeout`() = runTest {
        protocol.connect(transport)

        val exception = shouldThrow<McpException> {
            protocol.request<EmptyResult>(newRequest(), RequestOptions(timeout = 100.milliseconds))
        }

        exception.code shouldBe RPCError.ErrorCode.REQUEST_TIMEOUT
        exception.cause.shouldBeInstanceOf<TimeoutCancellationException>()
    }

    @Test
    fun `should clean up handlers and notify cancellation when request times out`() = runTest {
        protocol.connect(transport)

        shouldThrow<McpException> {
            protocol.request<EmptyResult>(
                newRequest(),
                RequestOptions(timeout = 100.milliseconds, onProgress = {}),
            )
        }

        protocol.responseHandlers shouldHaveSize 0
        protocol.progressHandlers shouldHaveSize 0

        val sent = transport.awaitRequest()
        val cancellations = transport.sentCancellations()
        cancellations shouldHaveSize 1
        cancellations[0].params?.jsonObject?.get("requestId") shouldBe McpJson.encodeToJsonElement(sent.id)
    }

    @Test
    fun `should complete request normally when response arrives within timeout`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            protocol.request<EmptyResult>(newRequest(), RequestOptions(timeout = 5.seconds))
        }

        val sent = transport.awaitRequest()
        transport.deliver(JSONRPCResponse(sent.id, EmptyResult()))

        inFlight.await() shouldBe EmptyResult()
        transport.sentCancellations() shouldHaveSize 0
        protocol.responseHandlers shouldHaveSize 0
    }

    @Test
    fun `should propagate the caller's own timeout cancellation instead of reporting a request timeout`() = runTest {
        protocol.connect(transport)

        shouldThrow<TimeoutCancellationException> {
            withTimeout(50.milliseconds) {
                protocol.request<EmptyResult>(
                    newRequest(),
                    RequestOptions(timeout = 10.seconds, onProgress = {}),
                )
            }
        }

        transport.sentCancellations() shouldHaveSize 0
        protocol.responseHandlers shouldHaveSize 0
        protocol.progressHandlers shouldHaveSize 0
    }

    @Test
    fun `should clean up handlers when the caller cancels while awaiting the response`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            protocol.request<EmptyResult>(newRequest(), RequestOptions(timeout = 10.seconds, onProgress = {}))
        }
        transport.awaitRequest()
        protocol.responseHandlers shouldHaveSize 1
        protocol.progressHandlers shouldHaveSize 1

        inFlight.cancelAndJoin()

        protocol.responseHandlers shouldHaveSize 0
        protocol.progressHandlers shouldHaveSize 0
    }

    @Test
    fun `should clean up handlers when sending the request fails`() = runTest {
        val throwingTransport = ThrowingSendTransport()
        protocol.connect(throwingTransport)

        shouldThrow<IllegalStateException> {
            protocol.request<EmptyResult>(newRequest(), RequestOptions(onProgress = {}))
        }

        protocol.responseHandlers shouldHaveSize 0
        protocol.progressHandlers shouldHaveSize 0
    }

    @Test
    fun `should fail request with REQUEST_TIMEOUT when sending the request exceeds the timeout`() = runTest {
        val slowTransport = SlowSendTransport(delayFor = 10.seconds)
        protocol.connect(slowTransport)

        val exception = shouldThrow<McpException> {
            protocol.request<EmptyResult>(newRequest(), RequestOptions(timeout = 100.milliseconds))
        }

        exception.code shouldBe RPCError.ErrorCode.REQUEST_TIMEOUT
    }
}

private class TimeoutTestProtocol : Protocol(null) {
    override fun assertCapabilityForMethod(method: Method) {
        // noop
    }
    override fun assertNotificationCapability(method: Method) {
        // noop
    }
    override fun assertRequestHandlerCapability(method: Method) {
        // noop
    }
}

private open class TimeoutTestTransport : Transport {
    val sent = mutableListOf<JSONRPCMessage>()
    private val requests = Channel<JSONRPCRequest>(Channel.UNLIMITED)
    private var onMessageCallback: (suspend (JSONRPCMessage) -> Unit)? = null

    override suspend fun start() {
        // noop
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        sent += message
        if (message is JSONRPCRequest) {
            requests.trySend(message)
        }
    }

    override suspend fun close() {
        // noop
    }

    override fun onClose(block: () -> Unit) {
        // noop
    }

    override fun onError(block: (Throwable) -> Unit) {
        // noop
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        onMessageCallback = block
    }

    suspend fun awaitRequest(): JSONRPCRequest = requests.receive()

    suspend fun deliver(message: JSONRPCMessage) {
        val callback = onMessageCallback ?: error("onMessage callback not registered")
        callback(message)
    }

    fun sentCancellations(): List<JSONRPCNotification> =
        sent.filterIsInstance<JSONRPCNotification>().filter { it.method == "notifications/cancelled" }
}

private class SlowSendTransport(private val delayFor: Duration) : TimeoutTestTransport() {
    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        delay(delayFor)
        super.send(message, options)
    }
}

private class ThrowingSendTransport : TimeoutTestTransport() {
    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?): Unit =
        throw IllegalStateException("send failed")
}
