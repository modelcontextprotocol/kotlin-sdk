package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
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

    // spec §8 test 6
    @Test
    fun `at most maxConcurrentHandlers handlers run concurrently while control messages bypass`() = runTest {
        val protocol = TestProtocol(
            ProtocolOptions(
                handlerCoroutineContext = StandardTestDispatcher(testScheduler),
                maxConcurrentHandlers = 2,
            ),
        )
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        var entered = 0
        val release = CompletableDeferred<Unit>()
        protocol.fallbackRequestHandler = { _, _ ->
            entered += 1
            release.await()
            EmptyResult()
        }

        repeat(4) { i -> transport.deliver(JSONRPCRequest(id = RequestId(i.toLong()), method = "test/slow")) }
        runCurrent()
        entered shouldBe 2 // exactly two running, two parked on the semaphore

        // ping (bypass) is answered while saturated
        transport.deliver(JSONRPCRequest(id = RequestId(100L), method = Method.Defined.Ping.value))
        runCurrent()
        responsesOn(transport).map { it.id } shouldBe listOf(RequestId(100L))

        // responses (bypass, inline) are processed while saturated
        val pending = async { protocol.request<EmptyResult>(PingRequest()) }
        runCurrent()
        val outbound = transport.awaitRequest()
        transport.deliver(JSONRPCResponse(id = outbound.id, result = EmptyResult()))
        runCurrent()
        pending.isCompleted shouldBe true

        release.complete(Unit)
        advanceUntilIdle()
        entered shouldBe 4
        responsesOn(transport) shouldHaveSize 5
    }

    // spec §8 test 18
    @Test
    fun `flood beyond maxInFlightHandlers is rejected fail-fast and the read loop never suspends`() = runTest {
        val protocol = TestProtocol(
            ProtocolOptions(
                handlerCoroutineContext = StandardTestDispatcher(testScheduler),
                maxConcurrentHandlers = 1,
                maxInFlightHandlers = 4,
            ),
        )
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        val release = CompletableDeferred<Unit>()
        protocol.fallbackRequestHandler = { _, _ ->
            release.await()
            EmptyResult()
        }

        // 10 requests delivered back-to-back; delivery must complete without advancing
        // virtual time — i.e. the read loop was never suspended on admission.
        val delivery = launch {
            repeat(10) { i -> transport.deliver(JSONRPCRequest(id = RequestId(i.toLong()), method = "test/slow")) }
        }
        runCurrent()
        delivery.isCompleted shouldBe true

        // 4 admitted (1 running + 3 parked), 6 rejected immediately
        val busyErrors = errorsOn(transport)
        busyErrors shouldHaveSize 6
        busyErrors.forEach {
            it.error.code shouldBe RPCError.ErrorCode.INTERNAL_ERROR
            it.error.message shouldBe "Server is busy: too many in-flight messages"
        }

        // ping still answered at full saturation
        transport.deliver(JSONRPCRequest(id = RequestId(100L), method = Method.Defined.Ping.value))
        runCurrent()
        responsesOn(transport).map { it.id } shouldBe listOf(RequestId(100L))

        release.complete(Unit)
        advanceUntilIdle()
        responsesOn(transport) shouldHaveSize 5 // ping + the 4 admitted
    }

    @Test
    fun `overflowing notifications are dropped and reported via onError`() = runTest {
        val protocol = TestProtocol(
            ProtocolOptions(
                handlerCoroutineContext = StandardTestDispatcher(testScheduler),
                maxConcurrentHandlers = 1,
                maxInFlightHandlers = 2,
            ),
        )
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        // parks one handler with a request: holds the sole execution permit and one in-flight slot
        val release = CompletableDeferred<Unit>()
        protocol.fallbackRequestHandler = { _, _ ->
            release.await()
            EmptyResult()
        }
        transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/hog"))
        runCurrent()

        // notification 1 is admitted (parked on the semaphore, behind the hogged permit);
        // notification 2 overflows the admission cap and is dropped instead of parked.
        val notificationRelease = CompletableDeferred<Unit>()
        protocol.fallbackNotificationHandler = { notificationRelease.await() }
        transport.deliver(JSONRPCNotification(method = "test/event"))
        transport.deliver(JSONRPCNotification(method = "test/event"))
        runCurrent()

        protocol.errors shouldHaveSize 1
        protocol.errors.single().message.orEmpty() shouldContain "too many in-flight messages"
        errorsOn(transport) shouldBe emptyList() // no wire error for dropped notifications

        release.complete(Unit)
        notificationRelease.complete(Unit)
        advanceUntilIdle()
    }

    // spec §8 test 5
    @Test
    fun `cancelling saturating handlers releases permits and lets parked handlers run`() = runTest {
        val protocol = TestProtocol(
            ProtocolOptions(
                handlerCoroutineContext = StandardTestDispatcher(testScheduler),
                maxConcurrentHandlers = 1,
            ),
        )
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        val secondCompleted = CompletableDeferred<Unit>()
        protocol.fallbackRequestHandler = { request, _ ->
            if (request.method == "test/hog") {
                delay(1000.seconds) // cancellable park
                error("unreachable")
            }
            secondCompleted.complete(Unit)
            EmptyResult()
        }

        transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/hog")) // holds the permit
        transport.deliver(JSONRPCRequest(id = RequestId(2L), method = "test/next")) // parked on the semaphore
        runCurrent()
        secondCompleted.isCompleted shouldBe false

        // cancel the hog THROUGH the bypass while saturated
        transport.deliver(cancelledNotification(requestId = RequestId(1L), reason = "make room"))
        advanceUntilIdle()

        secondCompleted.isCompleted shouldBe true
        responsesOn(transport).map { it.id } shouldBe listOf(RequestId(2L)) // hog suppressed, next answered
    }

    @Test
    fun `a parked handler can itself be cancelled before it ever runs`() = runTest {
        val protocol = TestProtocol(
            ProtocolOptions(
                handlerCoroutineContext = StandardTestDispatcher(testScheduler),
                maxConcurrentHandlers = 1,
            ),
        )
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        protocol.fallbackRequestHandler = { request, _ ->
            if (request.method == "test/hog") {
                delay(1000.seconds) // cancellable park
                error("unreachable")
            }
            EmptyResult()
        }

        transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/hog")) // holds the permit
        transport.deliver(JSONRPCRequest(id = RequestId(2L), method = "test/next")) // parked on the semaphore
        runCurrent()

        // cancel the parked handler before it ever acquires the permit, then cancel the hog too
        transport.deliver(cancelledNotification(requestId = RequestId(2L), reason = "give up while parked"))
        transport.deliver(cancelledNotification(requestId = RequestId(1L), reason = "make room"))
        advanceUntilIdle()

        // no response for id=2 at all: its withPermit acquire was cancelled before onRequest ever ran
        responsesOn(transport) shouldBe emptyList()
        errorsOn(transport) shouldBe emptyList()
    }
}
