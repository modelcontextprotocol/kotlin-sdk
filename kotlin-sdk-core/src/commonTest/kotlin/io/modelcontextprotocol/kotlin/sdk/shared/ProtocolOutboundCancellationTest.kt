package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class ProtocolOutboundCancellationTest {

    @Test
    fun `request times out awaiting the response and sends CancelledNotification with timeout reason`() = runTest {
        val (protocol, transport) = connectedProtocol()

        // the peer never responds; virtual time runs out the 5 s request timeout
        val thrown = shouldThrow<McpException> {
            protocol.request<EmptyResult>(PingRequest(), RequestOptions(timeout = 5.seconds))
        }

        thrown.code shouldBe RPCError.ErrorCode.REQUEST_TIMEOUT
        thrown.data?.jsonObject?.get("timeout")?.jsonPrimitive?.long shouldBe 5.seconds.inWholeMilliseconds
        val cancelled = cancellationsOn(transport).single()
        cancelled.requestId shouldBe transport.awaitRequest().id
        cancelled.reason.shouldNotBeNull() shouldContain "timed out"
    }

    @Test
    fun `outer withTimeout around request propagates the original timeout exception and notifies the peer`() = runTest {
        val (protocol, transport) = connectedProtocol()

        shouldThrow<TimeoutCancellationException> {
            withTimeout(1.seconds) { protocol.request<EmptyResult>(PingRequest()) }
        }

        cancellationsOn(transport).single().requestId shouldBe transport.awaitRequest().id
    }

    @Test
    fun `caller cancellation sends CancelledNotification and rethrows the original exception`() = runTest {
        val (protocol, transport) = connectedProtocol()
        var thrown: CancellationException? = null
        val job = launch {
            try {
                protocol.request<EmptyResult>(PingRequest())
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }
        val sent = transport.awaitRequest()

        job.cancel(CancellationException("user gave up"))
        job.join()

        thrown?.message shouldBe "user gave up"
        val cancelled = cancellationsOn(transport).single()
        cancelled.requestId shouldBe sent.id
        cancelled.reason shouldBe "user gave up"
    }

    @Test
    fun `cancelling the initialize request performs local cleanup but sends no CancelledNotification`() = runTest {
        val (protocol, transport) = connectedProtocol()
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
        job.cancelAndJoin()

        cancellationsOn(transport) shouldBe emptyList()
        // local cleanup remembered the id, so a late answer is not reported as unknown
        transport.deliver(JSONRPCResponse(id = sent.id, result = EmptyResult()))
        protocol.errors shouldBe emptyList()
    }

    @Test
    fun `late response and progress for a cancelled request are ignored while an unknown id is still reported`() =
        runTest {
            val (protocol, transport) = connectedProtocol()
            val job = launch { protocol.request<EmptyResult>(PingRequest(), RequestOptions(onProgress = {})) }
            val sent = transport.awaitRequest()
            job.cancelAndJoin()

            transport.deliver(ProgressNotification(ProgressNotificationParams(sent.id, 0.5)).toJSON())
            transport.deliver(JSONRPCResponse(id = sent.id, result = EmptyResult()))
            protocol.errors shouldBe emptyList()

            transport.deliver(JSONRPCResponse(id = RequestId(999L), result = EmptyResult()))
            protocol.errors shouldHaveSize 1
        }
}
