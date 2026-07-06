package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class ProtocolConcurrencyTest {

    private fun TestScope.newProtocol(): TestProtocol =
        TestProtocol(ProtocolOptions(handlerCoroutineContext = StandardTestDispatcher(testScheduler)))

    // the #176 repro
    @Test
    fun `a suspended slow handler does not block a later fast request`() = runTest {
        val protocol = newProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        protocol.fallbackRequestHandler = { request, _ ->
            if (request.method == "test/slow") delay(10.seconds)
            EmptyResult()
        }

        transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/slow"))
        transport.deliver(JSONRPCRequest(id = RequestId(2L), method = "test/fast"))
        runCurrent()

        responsesOn(transport).map { it.id } shouldBe listOf(RequestId(2L))

        advanceTimeBy(11.seconds)
        runCurrent()
        responsesOn(transport).map { it.id } shouldBe listOf(RequestId(2L), RequestId(1L))
    }

    // serial until the gate flips
    @Test
    fun `dispatch is serial until concurrent dispatch is enabled`() = runTest {
        val protocol = newProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)
        // gate NOT flipped

        protocol.fallbackRequestHandler = { request, _ ->
            if (request.method == "test/slow") delay(10.seconds)
            EmptyResult()
        }

        val delivery = launch {
            transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/slow"))
            transport.deliver(JSONRPCRequest(id = RequestId(2L), method = "test/fast"))
        }
        runCurrent()
        // inline handling: the delivering coroutine is parked inside the slow handler
        delivery.isCompleted shouldBe false
        responsesOn(transport) shouldBe emptyList()

        advanceTimeBy(11.seconds)
        runCurrent()
        delivery.isCompleted shouldBe true
        responsesOn(transport).map { it.id } shouldBe listOf(RequestId(1L), RequestId(2L))
    }

    // the hook fires before handler lookup and cannot be disabled by a user handler
    @Test
    fun `initialized notification invokes the router hook even when a user handler is registered`() = runTest {
        val protocol = newProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)

        var userHandlerCalled = false
        protocol.setNotificationHandler<InitializedNotification>(Method.Defined.NotificationsInitialized) {
            userHandlerCalled = true
            COMPLETED
        }
        transport.deliver(JSONRPCNotification(method = Method.Defined.NotificationsInitialized.value))
        runCurrent()

        protocol.initializedNotificationCount shouldBe 1
        userHandlerCalled shouldBe true
    }

    @Test
    fun `cancelled notification cancels the in-flight handler and suppresses the response`() = runTest {
        val protocol = newProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        val entered = CompletableDeferred<Unit>()
        val sawCancellation = CompletableDeferred<Unit>()
        protocol.fallbackRequestHandler = { _, _ ->
            entered.complete(Unit)
            try {
                delay(10.seconds)
            } catch (e: CancellationException) {
                sawCancellation.complete(Unit)
                throw e
            }
            EmptyResult()
        }

        transport.deliver(JSONRPCRequest(id = RequestId(5L), method = "test/slow"))
        runCurrent()
        entered.await()

        transport.deliver(cancelledNotification(requestId = RequestId(5L), reason = "user gave up"))
        runCurrent()
        sawCancellation.await()
        advanceUntilIdle()

        responsesOn(transport) shouldBe emptyList()
        errorsOn(transport) shouldBe emptyList() // JSONRPCError messages on the wire
        protocol.errors shouldBe emptyList()
    }

    @Test
    fun `cancelled notification for an unknown id is a silent no-op`() = runTest {
        val protocol = newProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        transport.deliver(cancelledNotification(requestId = RequestId(404L), reason = "nope"))
        advanceUntilIdle()

        protocol.errors shouldBe emptyList()
    }

    @Test
    fun `handler leaking CancellationException without being cancelled produces INTERNAL_ERROR`() = runTest {
        val protocol = newProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        protocol.fallbackRequestHandler = { _, _ -> throw CancellationException("leaked") }

        transport.deliver(JSONRPCRequest(id = RequestId(6L), method = "test/leak"))
        advanceUntilIdle()

        val error = errorsOn(transport).single()
        error.id shouldBe RequestId(6L)
        error.error.code shouldBe RPCError.ErrorCode.INTERNAL_ERROR
    }

    // suppression race variants
    @Test
    fun `handler that swallows cancellation and returns normally still gets no response`() = runTest {
        val protocol = newProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        val entered = CompletableDeferred<Unit>()
        protocol.fallbackRequestHandler = { _, _ ->
            entered.complete(Unit)
            try {
                delay(10.seconds)
            } catch (_: CancellationException) {
                // swallow (anti-pattern) and return normally
            }
            EmptyResult()
        }

        transport.deliver(JSONRPCRequest(id = RequestId(7L), method = "test/swallow"))
        runCurrent()
        entered.await()
        transport.deliver(cancelledNotification(requestId = RequestId(7L), reason = "stop"))
        advanceUntilIdle()

        responsesOn(transport) shouldBe emptyList()
    }

    @Test
    fun `handler throwing non-CE while cancelled gets no error response`() = runTest {
        val protocol = newProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        val entered = CompletableDeferred<Unit>()
        protocol.fallbackRequestHandler = { _, _ ->
            entered.complete(Unit)
            try {
                delay(10.seconds)
            } catch (_: CancellationException) {
                throw IllegalStateException("boom")
            }
            EmptyResult()
        }

        transport.deliver(JSONRPCRequest(id = RequestId(8L), method = "test/throw"))
        runCurrent()
        entered.await()
        transport.deliver(cancelledNotification(requestId = RequestId(8L), reason = "stop"))
        advanceUntilIdle()

        errorsOn(transport) shouldBe emptyList()
        protocol.errors shouldBe emptyList()
    }

    // arrival-order start for suspension-free notification handlers
    @Test
    fun `suspension-free notification handlers observe strict arrival order`() = runTest {
        val protocol = newProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        val seen = mutableListOf<Int>()
        protocol.fallbackNotificationHandler = { notification ->
            seen.add((notification.params as JsonObject).getValue("i").jsonPrimitive.int)
        }

        repeat(20) { i ->
            transport.deliver(
                JSONRPCNotification(method = "test/event", params = buildJsonObject { put("i", i) }),
            )
        }
        advanceUntilIdle()

        seen shouldBe (0 until 20).toList()
    }

    // reconnect resets the gate and uses a fresh scope
    @Test
    fun `reconnect resets the dispatch gate to serial`() = runTest {
        val protocol = newProtocol()
        val transport1 = RecordingTransport()
        protocol.connect(transport1)
        protocol.enableConcurrency()
        protocol.close()

        val transport2 = RecordingTransport()
        protocol.connect(transport2)
        protocol.fallbackRequestHandler = { request, _ ->
            if (request.method == "test/slow") delay(10.seconds)
            EmptyResult()
        }

        val delivery = launch {
            transport2.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/slow"))
            transport2.deliver(JSONRPCRequest(id = RequestId(2L), method = "test/fast"))
        }
        runCurrent()
        delivery.isCompleted shouldBe false // serial again: gate reset
        advanceTimeBy(11.seconds)
        runCurrent()
        responsesOn(transport2).map { it.id } shouldBe listOf(RequestId(1L), RequestId(2L))
    }

    @Test
    fun `close cancels in-flight handlers suppresses responses and fails pending outbound requests`() = runTest {
        val protocol = newProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        val entered = CompletableDeferred<Unit>()
        val sawCancellation = CompletableDeferred<Unit>()
        protocol.fallbackRequestHandler = { _, _ ->
            entered.complete(Unit)
            try {
                delay(10.seconds)
            } catch (e: CancellationException) {
                sawCancellation.complete(Unit)
                throw e
            }
            EmptyResult()
        }
        transport.deliver(JSONRPCRequest(id = RequestId(9L), method = "test/slow"))
        runCurrent()
        entered.await()

        // shouldThrow inside the async so its failure does not cancel the (non-supervisor) test scope.
        val pending = async { shouldThrow<McpException> { protocol.request<EmptyResult>(PingRequest()) } }
        transport.awaitRequest() // outbound ping on the wire

        protocol.close()
        runCurrent()

        sawCancellation.await()
        responsesOn(transport) shouldBe emptyList()
        val thrown = pending.await()
        thrown.code shouldBe RPCError.ErrorCode.CONNECTION_CLOSED
    }

    // post-close dispatch gate
    @Test
    fun `message delivered after close is dropped without crash or response`() = runTest {
        val protocol = newProtocol()
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()
        protocol.close()

        transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/anything"))
        advanceUntilIdle()

        responsesOn(transport) shouldBe emptyList()
        errorsOn(transport) shouldBe emptyList()
    }
}
