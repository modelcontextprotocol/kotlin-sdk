package io.modelcontextprotocol.kotlin.sdk.shared

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class ClientSendErrorRegressionTest {
    @Test
    fun `failed outgoing request reaches the caller without an error callback`() = runTest {
        val transport = FailingSendTransport()
        val protocol = TestProtocol()
        protocol.connect(transport)
        try {
            shouldThrow<McpException> {
                protocol.request<EmptyResult>(PingRequest())
            } shouldBeSameInstanceAs transport.failure

            transport.sendAttempts shouldBe 1
            protocol.errors shouldBe emptyList()
            protocol.responseHandlers shouldBe emptyMap()
        } finally {
            protocol.close()
        }
    }

    @Test
    fun `failed method-not-found response reaches the error callback once`() = runTest {
        val transport = FailingSendTransport()
        val protocol = TestProtocol()
        protocol.connect(transport)
        try {
            transport.deliver(JSONRPCRequest(RequestId.NumberId(1), "test/unknown"))

            transport.sendAttempts shouldBe 1
            protocol.errors shouldBe listOf(transport.failure)
        } finally {
            protocol.close()
        }
    }

    @Test
    fun `failed handler-error response reaches the error callback once`() = runTest {
        val transport = FailingSendTransport()
        val protocol = TestProtocol()
        protocol.fallbackRequestHandler = { _, _ -> throw IllegalStateException("Handler failed") }
        protocol.connect(transport)
        try {
            transport.deliver(JSONRPCRequest(RequestId.NumberId(1), "test/failing-handler"))

            transport.sendAttempts shouldBe 1
            protocol.errors shouldBe listOf(transport.failure)
        } finally {
            protocol.close()
        }
    }

    @Test
    fun `transient handler response failure reports once and answers the peer with an error`() = runTest {
        val (peer, peerTransport) = connectedProtocol()
        val transport = FailingSendTransport(failures = 1, onSend = peerTransport::deliver)
        val protocol = TestProtocol()
        protocol.connect(transport)
        try {
            val pending = async {
                shouldThrow<McpException> { peer.request<EmptyResult>(PingRequest()) }
            }
            val request = peerTransport.awaitRequest()
            transport.deliver(request)

            protocol.errors shouldBe listOf(transport.failure)
            transport.sendAttempts shouldBe 2
            val response = transport.deliveredMessages.single().shouldBeInstanceOf<JSONRPCError>()
            response.id shouldBe request.id
            response.error.code shouldBe transport.failure.code
            response.error.message shouldBe transport.failure.message
            val peerError = withTimeout(1.seconds) { pending.await() }
            peerError.code shouldBe transport.failure.code
            peerError.message shouldBe transport.failure.message
            peer.responseHandlers shouldBe emptyMap()
        } finally {
            protocol.close()
            peer.close()
        }
    }

    @Test
    fun `failed handler response and failed fallback each reach the error callback once`() = runTest {
        val transport = FailingSendTransport(failures = 2)
        val protocol = TestProtocol()
        protocol.fallbackRequestHandler = { _, _ -> EmptyResult() }
        protocol.connect(transport)
        try {
            transport.deliver(JSONRPCRequest(RequestId.NumberId(1), "test/successful-handler"))

            transport.sendAttempts shouldBe 2
            protocol.errors shouldBe listOf(transport.failure, transport.retryFailure)
            transport.deliveredMessages shouldBe emptyList()
        } finally {
            protocol.close()
        }
    }

    @Test
    fun `background errors still reach the error callback`() = runTest {
        val transport = FailingSendTransport()
        val protocol = TestProtocol()
        protocol.connect(transport)
        try {
            val failure = IllegalStateException("Read failed")
            transport.reportBackgroundError(failure)

            protocol.errors shouldBe listOf(failure)
            transport.sendAttempts shouldBe 0
        } finally {
            protocol.close()
        }
    }

    private class FailingSendTransport(
        private val failures: Int = Int.MAX_VALUE,
        private val onSend: suspend (JSONRPCMessage) -> Unit = {},
    ) : AbstractClientTransport() {
        override val logger: KLogger = KotlinLogging.logger {}
        val failure = McpException(RPCError.ErrorCode.INTERNAL_ERROR, "Send failed")
        val retryFailure = McpException(RPCError.ErrorCode.INTERNAL_ERROR, "Fallback send failed")
        val deliveredMessages = mutableListOf<JSONRPCMessage>()
        var sendAttempts = 0

        override suspend fun initialize() {}

        override suspend fun closeResources() {}

        override suspend fun performSend(message: JSONRPCMessage, options: TransportSendOptions?) {
            sendAttempts++
            if (sendAttempts <= failures) throw if (sendAttempts == 1) failure else retryFailure
            deliveredMessages += message
            onSend(message)
        }

        suspend fun deliver(message: JSONRPCMessage) = _onMessage(message)

        fun reportBackgroundError(failure: Throwable) = _onError(failure)
    }
}
