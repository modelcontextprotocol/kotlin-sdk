package io.modelcontextprotocol.kotlin.sdk.shared

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.modelcontextprotocol.kotlin.sdk.InternalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import kotlin.coroutines.cancellation.CancellationException

class AbstractClientTransportTest {

    private val transport = TestClientTransport()

    @Test
    fun `should transition to OPERATIONAL after successful start`() = runTest {
        transport.start()

        transport.currentState shouldBe ClientTransportState.Operational
        transport.initializeCalled shouldBe true
    }

    @Test
    fun `should transition to INITIALIZATION_FAILED and close resources on start error`() = runTest {
        transport.initializeFailure = TestException("Initialization failed")

        shouldThrow<TestException> { transport.start() }

        transport.currentState shouldBe ClientTransportState.InitializationFailed
        transport.closeResourcesCallCount shouldBe 1
    }

    @Test
    fun `should reject starting twice`() = runTest {
        transport.start()

        shouldThrow<IllegalStateException> { transport.start() }.message shouldContain "expected transport state New"
    }

    @Test
    fun `should transition to STOPPED and close resources after close`() = runTest {
        transport.start()
        transport.close()

        transport.currentState shouldBe ClientTransportState.Stopped
        transport.closeResourcesCallCount shouldBe 1
    }

    @Test
    fun `should be idempotent when closed multiple times and call onClose exactly once`() = runTest {
        var onCloseCalls = 0
        transport.onClose { onCloseCalls++ }
        transport.start()

        repeat(3) { transport.close() }

        transport.currentState shouldBe ClientTransportState.Stopped
        transport.closeResourcesCallCount shouldBe 1
        onCloseCalls shouldBe 1
    }

    @Test
    fun `should transition to SHUTDOWN_FAILED and still call onClose on close error`() = runTest {
        var onCloseCalled = false
        transport.onClose { onCloseCalled = true }
        transport.closeFailure = TestException("Close failed")
        transport.start()

        transport.close()

        transport.currentState shouldBe ClientTransportState.ShutdownFailed
        onCloseCalled shouldBe true
    }

    @Test
    fun `should propagate CancellationException on close and still call onClose`() = runTest {
        var onCloseCalled = false
        transport.onClose { onCloseCalled = true }
        transport.closeFailure = CancellationException("Test cancellation")
        transport.start()

        shouldThrow<CancellationException> { transport.close() }

        transport.currentState shouldBe ClientTransportState.ShutdownFailed
        onCloseCalled shouldBe true
    }

    @Test
    fun `should stop without closing resources when closed before start`() = runTest {
        transport.close()

        transport.currentState shouldBe ClientTransportState.Stopped
        transport.closeResourcesCallCount shouldBe 0
    }

    @Test
    fun `should ignore close after a failed start`() = runTest {
        transport.initializeFailure = TestException("Initialization failed")
        shouldThrow<TestException> { transport.start() }

        transport.close()

        transport.currentState shouldBe ClientTransportState.InitializationFailed
        transport.closeResourcesCallCount shouldBe 1 // from the failed start only
    }

    @ParameterizedTest
    @CsvSource("Operational, New", "Stopped, Operational")
    fun `should reject invalid transitions`(from: ClientTransportState, to: ClientTransportState) {
        transport.forceState(from)

        shouldThrow<IllegalArgumentException> { transport.testStateTransition(from, to) }
            .message shouldContain "Invalid transition: $from → $to"
    }

    @Test
    fun `should pass message and options to performSend`() = runTest {
        val message = PingRequest().toJSON()
        val options = TransportSendOptions()
        transport.start()

        transport.send(message, options)

        transport.sentMessages shouldBe listOf(message)
        transport.lastSendOptions shouldBeSameInstanceAs options
    }

    @Test
    fun `should throw when sending before start`() = runTest {
        assertSendRejected()
    }

    @Test
    fun `should throw when sending after close`() = runTest {
        transport.start()
        transport.close()

        assertSendRejected()
    }

    // New and Stopped are covered above; every other non-Operational state must reject sends too.
    @ParameterizedTest
    @EnumSource(
        value = ClientTransportState::class,
        names = ["Initializing", "InitializationFailed", "ShuttingDown", "ShutdownFailed"],
    )
    fun `should throw when sending in non-OPERATIONAL state`(state: ClientTransportState) = runTest {
        transport.forceState(state)

        assertSendRejected()
    }

    @Test
    fun `should call onError and rethrow as-is when performSend throws McpException`() = runTest {
        val errors = mutableListOf<Throwable>()
        transport.onError { errors += it }
        val failure = McpException(RPCError.ErrorCode.INTERNAL_ERROR, "Send failed")
        transport.sendFailure = failure
        transport.start()

        shouldThrow<McpException> { transport.send(PingRequest().toJSON()) } shouldBeSameInstanceAs failure
        errors shouldBe listOf(failure)
    }

    @Test
    fun `should call onError and wrap in INTERNAL_ERROR when performSend throws non-MCP exception`() = runTest {
        val errors = mutableListOf<Throwable>()
        transport.onError { errors += it }
        val failure = TestException("Network error")
        transport.sendFailure = failure
        transport.start()

        val exception = shouldThrow<McpException> { transport.send(PingRequest().toJSON()) }

        exception.code shouldBe RPCError.ErrorCode.INTERNAL_ERROR
        exception.cause shouldBeSameInstanceAs failure
        errors shouldBe listOf(failure)
    }

    @Test
    fun `should NOT call onError when performSend throws CancellationException`() = runTest {
        var errorCalls = 0
        transport.onError { errorCalls++ }
        transport.sendFailure = CancellationException("Operation cancelled")
        transport.start()

        shouldThrow<CancellationException> { transport.send(PingRequest().toJSON()) }

        errorCalls shouldBe 0
    }

    private suspend fun assertSendRejected() {
        val exception = shouldThrow<McpException> { transport.send(PingRequest().toJSON()) }

        exception.code shouldBe RPCError.ErrorCode.CONNECTION_CLOSED
        exception.message shouldContain "Transport is not ready"
        transport.sentMessages shouldBe emptyList()
    }

    @OptIn(InternalMcpApi::class)
    private class TestClientTransport : AbstractClientTransport() {
        override val logger: KLogger = KotlinLogging.logger {}
        val sentMessages = mutableListOf<JSONRPCMessage>()
        var lastSendOptions: TransportSendOptions? = null
        var initializeCalled = false
        var closeResourcesCallCount = 0
        var initializeFailure: Exception? = null
        var closeFailure: Exception? = null
        var sendFailure: Exception? = null

        val currentState: ClientTransportState
            get() = state

        override suspend fun initialize() {
            initializeCalled = true
            initializeFailure?.let { throw it }
        }

        override suspend fun performSend(message: JSONRPCMessage, options: TransportSendOptions?) {
            sendFailure?.let { throw it }
            sentMessages.add(message)
            lastSendOptions = options
        }

        override suspend fun closeResources() {
            closeResourcesCallCount++
            closeFailure?.let { throw it }
        }

        fun testStateTransition(from: ClientTransportState, to: ClientTransportState) = stateTransition(from, to)

        fun forceState(newState: ClientTransportState) = updateState(newState)
    }

    private class TestException(message: String) : Exception(message)
}
