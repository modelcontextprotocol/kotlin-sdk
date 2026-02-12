package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

/**
 * Unit tests for [AbstractClientTransport] state machine and lifecycle.
 *
 * Tests behavioral correctness of state transitions, lifecycle management,
 * and message sending without relying on mocking frameworks.
 */
@OptIn(ExperimentalAtomicApi::class)
class AbstractClientTransportTest {

    companion object {
        @JvmStatic
        fun nonMcpExceptions() = listOf(
            TestException("Generic failure"),
            java.io.IOException("Network error"),
            IllegalStateException("Invalid state"),
        )
    }

    private lateinit var transport: TestClientTransport

    @BeforeEach
    fun beforeEach() {
        transport = TestClientTransport()
    }

    @Nested
    @DisplayName("Lifecycle")
    inner class LifecycleTests {

        @Test
        fun `should start with NEW state`() {
            transport.currentState shouldBe ClientTransportState.NEW
        }

        @Test
        fun `should transition to OPERATIONAL after successful start`() = runTest {
            transport.start()

            transport.currentState shouldBe ClientTransportState.OPERATIONAL
            transport.initializeCalled shouldBe true
        }

        @Test
        fun `should call initialize during start`() = runTest {
            transport.start()

            transport.initializeCalled shouldBe true
            transport.currentState shouldBe ClientTransportState.OPERATIONAL
        }

        @Test
        fun `should transition to INITIALIZATION_FAILED on start error`() = runTest {
            transport.shouldFailInitialization = true

            shouldThrow<TestException> {
                transport.start()
            }

            transport.currentState shouldBe ClientTransportState.INITIALIZATION_FAILED
            transport.closeResourcesCalled shouldBe true
        }

        @Test
        fun `should call closeResources on initialization failure`() = runTest {
            transport.shouldFailInitialization = true

            shouldThrow<TestException> {
                transport.start()
            }

            transport.closeResourcesCalled shouldBe true
        }

        @Test
        fun `should reject starting twice`() = runTest {
            transport.start()

            val exception = shouldThrow<IllegalStateException> {
                transport.start()
            }
            exception.message shouldContain "expected transport state NEW"
        }

        @Test
        fun `should transition to STOPPED after successful close`() = runTest {
            transport.start()
            transport.close()

            transport.currentState shouldBe ClientTransportState.STOPPED
            transport.closeResourcesCalled shouldBe true
        }

        @Test
        fun `should call closeResources during close`() = runTest {
            transport.start()

            transport.close()

            transport.closeResourcesCalled shouldBe true
            transport.currentState shouldBe ClientTransportState.STOPPED
        }

        @Test
        fun `should be idempotent when closed multiple times`() = runTest {
            // given
            transport.start()
            transport.close()
            // when
            transport.close()
            transport.close()

            transport.currentState shouldBe ClientTransportState.STOPPED
            transport.closeResourcesCallCount shouldBe 1
        }

        @Test
        fun `should call onClose callback exactly once on multiple close calls`() = runTest {
            var onCloseCallCount = 0
            transport.onClose { onCloseCallCount++ }

            transport.start()
            transport.close()
            transport.close()

            onCloseCallCount shouldBe 1
        }

        @Test
        fun `should transition to SHUTDOWN_FAILED on close error`() = runTest {
            transport.shouldFailClose = true
            transport.start()

            transport.close()

            transport.currentState shouldBe ClientTransportState.SHUTDOWN_FAILED
        }

        @Test
        fun `should call onClose even when close fails`() = runTest {
            // given
            var onCloseCalled = false
            transport.onClose { onCloseCalled = true }
            transport.shouldFailClose = true
            transport.start()
            // when
            transport.close()
            // then
            onCloseCalled shouldBe true
        }

        @Test
        fun `should propagate CancellationException on close`() = runTest {
            // given
            transport.shouldThrowCancellation = true
            transport.start()
            // when-then
            shouldThrow<CancellationException> {
                transport.close()
            }

            transport.currentState shouldBe ClientTransportState.SHUTDOWN_FAILED
        }

        @Test
        fun `should call onClose even when CancellationException is thrown`() = runTest {
            var onCloseCalled = false
            transport.onClose { onCloseCalled = true }
            transport.shouldThrowCancellation = true
            transport.start()

            shouldThrow<CancellationException> {
                transport.close()
            }

            onCloseCalled shouldBe true
        }
    }

    @Nested
    @DisplayName("State Transitions")
    inner class StateTransitionTests {

        @ParameterizedTest
        @CsvSource(
            "NEW, INITIALIZING",
            "NEW, STOPPED",
            "INITIALIZING, OPERATIONAL",
            "INITIALIZING, INITIALIZATION_FAILED",
            "OPERATIONAL, SHUTTING_DOWN",
            "SHUTTING_DOWN, STOPPED",
            "SHUTTING_DOWN, SHUTDOWN_FAILED",
        )
        fun `should allow valid transitions`(fromName: String, toName: String) {
            val from = ClientTransportState.valueOf(fromName)
            val to = ClientTransportState.valueOf(toName)

            transport.forceState(from)
            transport.testStateTransition(from, to)

            transport.currentState shouldBe to
        }

        @ParameterizedTest
        @CsvSource(
            // From NEW: only INITIALIZING is valid
            "NEW, OPERATIONAL",
            "NEW, INITIALIZATION_FAILED",
            "NEW, SHUTTING_DOWN",
            "NEW, SHUTDOWN_FAILED",
            // From INITIALIZING: only OPERATIONAL or INITIALIZATION_FAILED are valid
            "INITIALIZING, NEW",
            "INITIALIZING, INITIALIZING",
            "INITIALIZING, SHUTTING_DOWN",
            "INITIALIZING, STOPPED",
            "INITIALIZING, SHUTDOWN_FAILED",
            // From OPERATIONAL: only SHUTTING_DOWN is valid
            "OPERATIONAL, NEW",
            "OPERATIONAL, INITIALIZING",
            "OPERATIONAL, INITIALIZATION_FAILED",
            "OPERATIONAL, OPERATIONAL",
            "OPERATIONAL, STOPPED",
            "OPERATIONAL, SHUTDOWN_FAILED",
            // From SHUTTING_DOWN: only STOPPED or SHUTDOWN_FAILED are valid
            "SHUTTING_DOWN, NEW",
            "SHUTTING_DOWN, INITIALIZING",
            "SHUTTING_DOWN, INITIALIZATION_FAILED",
            "SHUTTING_DOWN, OPERATIONAL",
            "SHUTTING_DOWN, SHUTTING_DOWN",
            // From terminal states: no transitions allowed
            "INITIALIZATION_FAILED, NEW",
            "INITIALIZATION_FAILED, OPERATIONAL",
            "STOPPED, NEW",
            "STOPPED, OPERATIONAL",
            "SHUTDOWN_FAILED, NEW",
            "SHUTDOWN_FAILED, OPERATIONAL",
        )
        fun `should reject invalid transitions`(fromName: String, toName: String) {
            val from = ClientTransportState.valueOf(fromName)
            val to = ClientTransportState.valueOf(toName)

            transport.forceState(from)

            val exception = shouldThrow<IllegalArgumentException> {
                transport.testStateTransition(from, to)
            }
            exception.message shouldContain "Invalid transition: $from → $to"
        }
    }

    @Nested
    @DisplayName("Send Operations")
    inner class SendTests {

        @Test
        fun `should send message successfully when OPERATIONAL`() = runTest {
            val message = PingRequest().toJSON()

            transport.start()
            transport.send(message)

            transport.sentMessages shouldBe listOf(message)
        }

        @Test
        fun `should pass options to performSend`() = runTest {
            val message = PingRequest().toJSON()
            val options = TransportSendOptions()

            transport.start()
            transport.send(message, options)

            transport.sentMessages shouldBe listOf(message)
            transport.lastSendOptions shouldBe options
        }

        @Test
        fun `should throw when sending before start`() = runTest {
            val exception = shouldThrow<McpException> {
                transport.send(PingRequest().toJSON())
            }

            exception.code shouldBe RPCError.ErrorCode.CONNECTION_CLOSED
            exception.message shouldContain "not started"
        }

        @Test
        fun `should throw when sending after close`() = runTest {
            transport.start()
            transport.close()

            val exception = shouldThrow<McpException> {
                transport.send(PingRequest().toJSON())
            }

            exception.code shouldBe RPCError.ErrorCode.CONNECTION_CLOSED
            exception.message shouldContain "not started"
        }

        @ParameterizedTest
        @EnumSource(
            value = ClientTransportState::class,
            names = ["OPERATIONAL"],
            mode = EnumSource.Mode.EXCLUDE,
        )
        fun `should throw when sending in non-OPERATIONAL state`(state: ClientTransportState) = runTest {
            transport.forceState(state)

            val exception = shouldThrow<McpException> {
                transport.send(PingRequest().toJSON())
            }

            exception.code shouldBe RPCError.ErrorCode.CONNECTION_CLOSED
            exception.message shouldContain "not started"
            transport.sentMessages.isEmpty() shouldBe true
        }

        @Test
        fun `should call onError when performSend throws McpException`() = runTest {
            var errorCallCount = 0
            var capturedError: Throwable? = null
            transport.onError { error ->
                errorCallCount++
                capturedError = error
            }
            transport.shouldFailSend = true
            transport.sendException = McpException(RPCError.ErrorCode.INTERNAL_ERROR, "Send failed")

            transport.start()
            val exception = shouldThrow<McpException> {
                transport.send(PingRequest().toJSON())
            }

            errorCallCount shouldBe 1
            capturedError shouldBe transport.sendException
            exception shouldBe transport.sendException
        }

        @ParameterizedTest
        @MethodSource("io.modelcontextprotocol.kotlin.sdk.shared.AbstractClientTransportTest#nonMcpExceptions")
        fun `should call onError when performSend throws non-MCP exception`(testException: Throwable) = runTest {
            var errorCallCount = 0
            var capturedError: Throwable? = null
            transport.onError { error ->
                errorCallCount++
                capturedError = error
            }
            transport.shouldFailSend = true
            transport.sendException = testException

            transport.start()
            val exception = shouldThrow<McpException> {
                transport.send(PingRequest().toJSON())
            }

            errorCallCount shouldBe 1
            capturedError shouldBe testException
            exception.code shouldBe RPCError.ErrorCode.INTERNAL_ERROR
            exception.cause shouldBe testException
        }

        @Test
        fun `should NOT call onError when performSend throws CancellationException`() = runTest {
            var errorCallCount = 0
            transport.onError { errorCallCount++ }
            transport.shouldFailSend = true
            transport.sendException = CancellationException("Operation cancelled")

            transport.start()
            shouldThrow<CancellationException> {
                transport.send(PingRequest().toJSON())
            }

            errorCallCount shouldBe 0
        }
    }

    @Nested
    @DisplayName("Idempotency")
    inner class IdempotencyTests {

        @Test
        fun `should be idempotent when closing from NEW state`() = runTest {
            transport.close()

            transport.currentState shouldBe ClientTransportState.STOPPED
            transport.closeResourcesCalled shouldBe false
        }

        @Test
        fun `should be idempotent when closing from INITIALIZATION_FAILED state`() = runTest {
            transport.shouldFailInitialization = true
            shouldThrow<TestException> { transport.start() }

            transport.close()

            transport.currentState shouldBe ClientTransportState.INITIALIZATION_FAILED
            // closeResources was called during a failed start, but not during this close
            transport.closeResourcesCallCount shouldBe 1
        }

        @ParameterizedTest
        @EnumSource(
            value = ClientTransportState::class,
            names = ["SHUTTING_DOWN", "SHUTDOWN_FAILED", "STOPPED"],
        )
        fun `should be idempotent when closing from terminal or shutdown states`(state: ClientTransportState) =
            runTest {
                transport.forceState(state)
                val initialCloseCount = transport.closeResourcesCallCount

                transport.close()

                transport.currentState shouldBe state
                transport.closeResourcesCallCount shouldBe initialCloseCount
            }
    }

    /**
     * Simple test implementation of [AbstractClientTransport] for behavioral testing.
     * Tracks method calls without mocking frameworks.
     */
    private class TestClientTransport : AbstractClientTransport() {
        val sentMessages = mutableListOf<JSONRPCMessage>()
        var lastSendOptions: TransportSendOptions? = null
        var initializeCalled = false
        var closeResourcesCalled = false
        var closeResourcesCallCount = 0
        var shouldFailInitialization = false
        var shouldFailClose = false
        var shouldThrowCancellation = false
        var shouldFailSend = false
        var sendException: Throwable? = null

        val currentState: ClientTransportState
            get() = state.load()

        override suspend fun initialize() {
            initializeCalled = true
            if (shouldFailInitialization) {
                throw TestException("Initialization failed")
            }
        }

        override suspend fun performSend(message: JSONRPCMessage, options: TransportSendOptions?) {
            if (shouldFailSend) {
                throw sendException ?: TestException("Send failed")
            }
            sentMessages.add(message)
            lastSendOptions = options
        }

        override suspend fun closeResources() {
            closeResourcesCalled = true
            closeResourcesCallCount++

            when {
                shouldThrowCancellation -> throw CancellationException("Test cancellation")
                shouldFailClose -> throw TestException("Close failed")
            }
        }

        fun testStateTransition(from: ClientTransportState, to: ClientTransportState) {
            stateTransition(from, to)
        }

        fun forceState(newState: ClientTransportState) {
            state.store(newState)
        }
    }

    private class TestException(message: String) : Exception(message)
}
