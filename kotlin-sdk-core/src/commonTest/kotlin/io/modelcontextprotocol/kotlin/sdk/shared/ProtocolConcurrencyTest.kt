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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class ProtocolConcurrencyTest {

    /** Connects a [TestProtocol] whose handlers run on the test scheduler; [concurrent] flips the init gate. */
    private suspend fun TestScope.connected(
        maxConcurrentHandlers: Int = DEFAULT_MAX_CONCURRENT_HANDLERS,
        maxInFlightHandlers: Int = DEFAULT_MAX_IN_FLIGHT_HANDLERS,
        concurrent: Boolean = true,
    ): Pair<TestProtocol, RecordingTransport> = connectedProtocol(
        ProtocolOptions(
            handlerCoroutineContext = StandardTestDispatcher(testScheduler),
            maxConcurrentHandlers = maxConcurrentHandlers,
            maxInFlightHandlers = maxInFlightHandlers,
        ),
    ).also { (protocol, _) -> if (concurrent) protocol.enableConcurrency() }

    @Test
    fun `a suspended slow handler does not block a later fast request`() = runTest {
        val (protocol, transport) = connected()
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

    @Test
    fun `dispatch is serial until concurrent dispatch is enabled`() = runTest {
        val (protocol, transport) = connected(concurrent = false)
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
        val (protocol, transport) = connected(concurrent = false)
        var userHandlerCalled = false
        protocol.setNotificationHandler<InitializedNotification>(Method.Defined.NotificationsInitialized) {
            userHandlerCalled = true
            COMPLETED
        }

        transport.deliver(JSONRPCNotification(method = Method.Defined.NotificationsInitialized.value))

        protocol.initializedNotificationCount shouldBe 1
        userHandlerCalled shouldBe true
    }

    @Test
    fun `cancelled notification cancels the in-flight handler and suppresses the response`() =
        cancelledRequestGetsNoReply { throw it }

    @Test
    fun `handler that swallows cancellation and returns normally still gets no response`() =
        cancelledRequestGetsNoReply { }

    @Test
    fun `handler throwing non-CE while cancelled gets no error response`() =
        cancelledRequestGetsNoReply { throw IllegalStateException("boom") }

    /**
     * Parks a handler, cancels it with `notifications/cancelled` and lets [onCancel] react. Whatever the
     * reaction, the cancelled request must produce nothing on the wire and nothing in [TestProtocol.errors].
     */
    private fun cancelledRequestGetsNoReply(onCancel: (CancellationException) -> Unit): TestResult = runTest {
        val (protocol, transport) = connected()
        protocol.fallbackRequestHandler = { _, _ ->
            try {
                delay(10.seconds)
            } catch (e: CancellationException) {
                onCancel(e)
            }
            EmptyResult()
        }

        transport.deliver(JSONRPCRequest(id = RequestId(5L), method = "test/slow"))
        transport.deliver(cancelledNotification(requestId = RequestId(5L), reason = "user gave up"))
        advanceUntilIdle()

        transport.sentWithOptions shouldBe emptyList()
        protocol.errors shouldBe emptyList()
    }

    @Test
    fun `cancelled notification for an unknown id is a silent no-op`() = runTest {
        val (protocol, transport) = connected()

        transport.deliver(cancelledNotification(requestId = RequestId(404L), reason = "nope"))
        advanceUntilIdle()

        protocol.errors shouldBe emptyList()
    }

    @Test
    fun `handler leaking CancellationException without being cancelled produces INTERNAL_ERROR`() = runTest {
        val (protocol, transport) = connected()
        protocol.fallbackRequestHandler = { _, _ -> throw CancellationException("leaked") }

        transport.deliver(JSONRPCRequest(id = RequestId(6L), method = "test/leak"))
        advanceUntilIdle()

        val error = errorsOn(transport).single()
        error.id shouldBe RequestId(6L)
        error.error.code shouldBe RPCError.ErrorCode.INTERNAL_ERROR
    }

    @Test
    fun `suspension-free notification handlers observe strict arrival order`() = runTest {
        val (protocol, transport) = connected()
        val seen = mutableListOf<Int>()
        protocol.fallbackNotificationHandler = { notification ->
            seen += notification.params!!.jsonObject.getValue("i").jsonPrimitive.int
        }

        repeat(20) { i ->
            transport.deliver(JSONRPCNotification(method = "test/event", params = buildJsonObject { put("i", i) }))
        }

        // no scheduler step: UNDISPATCHED handlers already ran inside deliver(), in arrival order
        seen shouldBe (0 until 20).toList()
    }

    @Test
    fun `reconnect resets the dispatch gate to serial`() = runTest {
        val (protocol, _) = connected()
        protocol.close()
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.fallbackRequestHandler = { _, _ ->
            delay(10.seconds)
            EmptyResult()
        }

        val delivery = launch { transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/slow")) }
        runCurrent()

        delivery.isCompleted shouldBe false // serial again: the delivering coroutine waits for the handler
    }

    @Test
    fun `close cancels in-flight handlers suppresses responses and fails pending outbound requests`() = runTest {
        val (protocol, transport) = connected()
        var handlerCancelled = false
        protocol.fallbackRequestHandler = { _, _ ->
            try {
                awaitCancellation()
            } finally {
                handlerCancelled = true
            }
        }
        transport.deliver(JSONRPCRequest(id = RequestId(9L), method = "test/slow"))

        // shouldThrow inside the async so its failure does not cancel the (non-supervisor) test scope.
        val pending = async { shouldThrow<McpException> { protocol.request<EmptyResult>(PingRequest()) } }
        transport.awaitRequest() // outbound ping on the wire

        protocol.close()
        runCurrent()

        handlerCancelled shouldBe true
        responsesOn(transport) shouldBe emptyList()
        errorsOn(transport) shouldBe emptyList()
        pending.await().code shouldBe RPCError.ErrorCode.CONNECTION_CLOSED
    }

    @Test
    fun `message delivered after close is dropped without crash or response`() = runTest {
        val (protocol, transport) = connected()
        protocol.close()

        transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/anything"))
        advanceUntilIdle()

        transport.sentWithOptions shouldBe emptyList()
    }

    @Test
    fun `at most maxConcurrentHandlers handlers run concurrently while control messages bypass`() = runTest {
        val (protocol, transport) = connected(maxConcurrentHandlers = 2)
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
        val outbound = transport.awaitRequest()
        transport.deliver(JSONRPCResponse(id = outbound.id, result = EmptyResult()))
        runCurrent()
        pending.isCompleted shouldBe true

        release.complete(Unit)
        advanceUntilIdle()
        entered shouldBe 4
        responsesOn(transport) shouldHaveSize 5
    }

    @Test
    fun `flood beyond maxInFlightHandlers is rejected fail-fast and the read loop never suspends`() = runTest {
        val (protocol, transport) = connected(maxConcurrentHandlers = 1, maxInFlightHandlers = 4)
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
        val (protocol, transport) = connected(maxConcurrentHandlers = 1, maxInFlightHandlers = 2)
        // a parked request holds the sole execution permit and one in-flight slot
        protocol.fallbackRequestHandler = { _, _ -> awaitCancellation() }
        transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/hog"))

        // notification 1 is admitted (parked behind the permit); notification 2 overflows and is dropped
        transport.deliver(JSONRPCNotification(method = "test/event"))
        transport.deliver(JSONRPCNotification(method = "test/event"))

        protocol.errors.single().message.orEmpty() shouldContain "too many in-flight messages"
        errorsOn(transport) shouldBe emptyList() // no wire error for dropped notifications
    }

    @Test
    fun `cancelling saturating handlers releases permits and lets parked handlers run`() = runTest {
        val (protocol, transport) = connected(maxConcurrentHandlers = 1)
        protocol.fallbackRequestHandler = { request, _ ->
            if (request.method == "test/hog") awaitCancellation()
            EmptyResult()
        }
        transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/hog")) // holds the permit
        transport.deliver(JSONRPCRequest(id = RequestId(2L), method = "test/next")) // parked on the semaphore
        runCurrent()
        responsesOn(transport) shouldBe emptyList()

        // cancel the hog through the control-message bypass while saturated
        transport.deliver(cancelledNotification(requestId = RequestId(1L), reason = "make room"))
        advanceUntilIdle()

        responsesOn(transport).map { it.id } shouldBe listOf(RequestId(2L)) // hog suppressed, next answered
    }

    @Test
    fun `a parked handler can itself be cancelled before it ever runs`() = runTest {
        val (protocol, transport) = connected(maxConcurrentHandlers = 1)
        protocol.fallbackRequestHandler = { request, _ ->
            if (request.method == "test/hog") awaitCancellation()
            EmptyResult()
        }
        transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/hog")) // holds the permit
        transport.deliver(JSONRPCRequest(id = RequestId(2L), method = "test/next")) // parked on the semaphore

        transport.deliver(cancelledNotification(requestId = RequestId(2L), reason = "give up while parked"))
        transport.deliver(cancelledNotification(requestId = RequestId(1L), reason = "make room"))
        advanceUntilIdle()

        // id=2 was cancelled while waiting for the permit, so its handler never ran and nothing was sent
        transport.sentWithOptions shouldBe emptyList()
    }
}
