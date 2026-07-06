package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class ProtocolOutboundCancellationTest {

    @Test
    fun `request times out awaiting the response and sends CancelledNotification with timeout reason`() = runTest {
        val protocol = TestProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)

        val inFlight = async {
            shouldThrow<McpException> {
                protocol.request<EmptyResult>(PingRequest(), RequestOptions(timeout = 5.seconds))
            }
        }
        val sent = transport.awaitRequest() // request is on the wire; peer never responds
        advanceTimeBy(6.seconds)
        runCurrent()

        val thrown = inFlight.await()
        thrown.code shouldBe RPCError.ErrorCode.REQUEST_TIMEOUT
        thrown.data?.jsonObject?.get("timeout")?.jsonPrimitive?.long shouldBe 5.seconds.inWholeMilliseconds

        val cancelledJson = transport.sentWithOptions
            .map { it.first }
            .filterIsInstance<JSONRPCNotification>()
            .single { it.method == Method.Defined.NotificationsCancelled.value }
        val cancelledParams = McpJson.decodeFromJsonElement<CancelledNotificationParams>(cancelledJson.params!!)
        cancelledParams.requestId shouldBe sent.id
        cancelledParams.reason.shouldNotBeNull() shouldContain "timed out"
    }

    @Test
    fun `caller cancellation sends CancelledNotification and rethrows the original exception`() = runTest {
        val protocol = TestProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)

        val cancelled = CompletableDeferred<CancellationException>()
        val job = launch {
            try {
                protocol.request<EmptyResult>(PingRequest())
            } catch (e: CancellationException) {
                cancelled.complete(e)
                throw e
            }
        }
        transport.awaitRequest()
        job.cancelAndJoin()

        cancelled.await()
        val wire = transport.sentWithOptions.map { it.first }.filterIsInstance<JSONRPCNotification>()
            .filter { it.method == Method.Defined.NotificationsCancelled.value }
        wire shouldHaveSize 1
    }

    @Test
    fun `cancelling the initialize request performs local cleanup but sends no CancelledNotification`() = runTest {
        val protocol = TestProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)

        val job = launch {
            protocol.request<EmptyResult>(
                InitializeRequest(
                    InitializeRequestParams(
                        protocolVersion = LATEST_PROTOCOL_VERSION,
                        capabilities = ClientCapabilities(),
                        clientInfo = Implementation(name = "t", version = "1"),
                    ),
                ),
            )
        }
        val sent = transport.awaitRequest()
        sent.method shouldBe Method.Defined.Initialize.value
        job.cancelAndJoin()

        transport.sentWithOptions.map { it.first }.filterIsInstance<JSONRPCNotification>()
            .filter { it.method == Method.Defined.NotificationsCancelled.value } shouldHaveSize 0
        protocol.responseHandlers shouldBe emptyMap()
    }

    @Test
    fun `late response for a cancelled request is ignored silently`() = runTest {
        val protocol = TestProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)

        val job = launch { protocol.request<EmptyResult>(PingRequest()) }
        val sent = transport.awaitRequest()
        job.cancelAndJoin()

        transport.deliver(JSONRPCResponse(id = sent.id, result = EmptyResult()))

        protocol.errors shouldHaveSize 0 // no "unknown message ID" onError
    }

    @Test
    fun `response for a genuinely unknown id still reports onError`() = runTest {
        val protocol = TestProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)

        transport.deliver(JSONRPCResponse(id = RequestId(999L), result = EmptyResult()))

        protocol.errors shouldHaveSize 1
    }
}
