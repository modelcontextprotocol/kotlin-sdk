package io.modelcontextprotocol.kotlin.sdk.shared

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test

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

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun `failed handler response reaches the error callback once without a second response`(failures: Int) = runTest {
        val transport = FailingSendTransport(failures)
        val protocol = TestProtocol()
        protocol.fallbackRequestHandler = { _, _ -> EmptyResult() }
        protocol.connect(transport)
        try {
            transport.deliver(JSONRPCRequest(RequestId.NumberId(1), "test/successful-handler"))

            protocol.errors shouldBe listOf(transport.failure)
            transport.sendAttempts shouldBe 1
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

    private class FailingSendTransport(private val failures: Int = Int.MAX_VALUE) : AbstractClientTransport() {
        override val logger: KLogger = KotlinLogging.logger {}
        val failure = McpException(RPCError.ErrorCode.INTERNAL_ERROR, "Send failed")
        var sendAttempts = 0

        override suspend fun initialize() {}

        override suspend fun closeResources() {}

        override suspend fun performSend(message: JSONRPCMessage, options: TransportSendOptions?) {
            sendAttempts++
            if (sendAttempts <= failures) throw failure
        }

        suspend fun deliver(message: JSONRPCMessage) = _onMessage(message)

        fun reportBackgroundError(failure: Throwable) = _onError(failure)
    }
}
