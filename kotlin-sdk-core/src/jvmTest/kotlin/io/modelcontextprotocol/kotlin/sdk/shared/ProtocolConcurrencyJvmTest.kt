package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Real-dispatcher counterparts to [ProtocolConcurrencyTest].
 *
 * These run under [runBlocking] (real time, real threads on [Dispatchers.Default]) rather than
 * `runTest`: a virtual-time scheduler auto-advances past the `withTimeout` guards and cannot
 * observe genuine cross-thread parallelism, so the concurrency assertions could pass vacuously.
 */
class ProtocolConcurrencyJvmTest {

    @Test
    fun `fast request completes while a slow handler is parked on Dispatchers Default`() = runBlocking {
        val protocol = TestProtocol(ProtocolOptions(handlerCoroutineContext = Dispatchers.Default))
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        val slowEntered = CompletableDeferred<Unit>()
        val releaseSlow = CompletableDeferred<Unit>()
        protocol.fallbackRequestHandler = { request, _ ->
            if (request.method == "test/slow") {
                slowEntered.complete(Unit)
                releaseSlow.await()
            }
            EmptyResult()
        }

        transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/slow"))
        withTimeout(5.seconds) { slowEntered.await() }
        transport.deliver(JSONRPCRequest(id = RequestId(2L), method = "test/fast"))

        val fast = withTimeout(5.seconds) { transport.awaitSent() }
        (fast as JSONRPCResponse).id shouldBe RequestId(2L)

        releaseSlow.complete(Unit)
        val slow = withTimeout(5.seconds) { transport.awaitSent() }
        (slow as JSONRPCResponse).id shouldBe RequestId(1L)
        Unit
    }

    @Test
    fun `dispatch stays serial before the gate flips on Dispatchers Default`() = runBlocking {
        val protocol = TestProtocol(ProtocolOptions(handlerCoroutineContext = Dispatchers.Default))
        val transport = RecordingTransport()
        protocol.connect(transport)
        // gate NOT flipped

        val slowEntered = CompletableDeferred<Unit>()
        val releaseSlow = CompletableDeferred<Unit>()
        protocol.fallbackRequestHandler = { request, _ ->
            if (request.method == "test/slow") {
                slowEntered.complete(Unit)
                releaseSlow.await()
            }
            EmptyResult()
        }

        // A single delivering coroutine models the transport read loop: the second deliver cannot
        // start until the first returns, so a parked slow handler blocks the fast request.
        val delivery = launch {
            transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "test/slow"))
            transport.deliver(JSONRPCRequest(id = RequestId(2L), method = "test/fast"))
        }
        withTimeout(5.seconds) { slowEntered.await() }

        // The delivering coroutine is parked inside the slow handler; fast was never delivered.
        transport.sentMessages.tryReceive().isFailure shouldBe true

        releaseSlow.complete(Unit)
        val first = withTimeout(5.seconds) { transport.awaitSent() }
        (first as JSONRPCResponse).id shouldBe RequestId(1L)
        val second = withTimeout(5.seconds) { transport.awaitSent() }
        (second as JSONRPCResponse).id shouldBe RequestId(2L)
        delivery.join()
    }

    @Test
    fun `suspension-free notification handlers keep arrival order on Dispatchers Default`() = runBlocking {
        val protocol = TestProtocol(ProtocolOptions(handlerCoroutineContext = Dispatchers.Default))
        val transport = RecordingTransport()
        protocol.connect(transport)
        protocol.enableConcurrency()

        // safe: appended only from the delivering coroutine (UNDISPATCHED, suspension-free)
        val seen = mutableListOf<Int>()
        protocol.fallbackNotificationHandler = { notification ->
            seen.add((notification.params as JsonObject).getValue("i").jsonPrimitive.int)
        }
        repeat(100) { i ->
            transport.deliver(JSONRPCNotification(method = "test/event", params = buildJsonObject { put("i", i) }))
        }
        seen shouldBe (0 until 100).toList()
    }
}
