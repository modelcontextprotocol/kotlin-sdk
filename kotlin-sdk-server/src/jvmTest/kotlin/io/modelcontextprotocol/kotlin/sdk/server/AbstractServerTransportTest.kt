package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.spyk
import io.modelcontextprotocol.kotlin.sdk.InternalMcpApi
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
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
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalAtomicApi::class)
class AbstractServerTransportTest {

    private lateinit var transport: TestServerTransport

    @BeforeEach
    fun beforeEach() {
        transport = spyk(TestServerTransport())
    }

    @Nested
    @DisplayName("Lifecycle")
    inner class LifecycleTests {

        @Test
        fun `should start with New state`() {
            transport.currentState shouldBe ServerTransportState.New
        }

        @Test
        fun `should transition to Active after successful start`() = runTest {
            transport.start()

            transport.currentState shouldBe ServerTransportState.Active
            transport.initializeCalled shouldBe true
        }

        @Test
        fun `should call initialize during start`() = runTest {
            transport.start()

            transport.initializeCalled shouldBe true
            transport.currentState shouldBe ServerTransportState.Active
        }

        @Test
        fun `should transition to InitializationFailed on start error`() = runTest {
            transport.shouldFailInitialization = true

            shouldThrow<TestException> {
                transport.start()
            }

            transport.currentState shouldBe ServerTransportState.InitializationFailed
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
            exception.message shouldContain "expected transport state New"
        }

        @Test
        fun `should transition to Stopped after successful close`() = runTest {
            transport.start()
            transport.close()

            transport.currentState shouldBe ServerTransportState.Stopped
            transport.closeResourcesCalled shouldBe true
        }

        @Test
        fun `should call closeResources during close`() = runTest {
            transport.start()

            transport.close()

            transport.closeResourcesCalled shouldBe true
            transport.currentState shouldBe ServerTransportState.Stopped
        }

        @Test
        fun `should be idempotent when closed multiple times`() = runTest {
            transport.start()
            transport.close()

            transport.close()
            transport.close()

            transport.currentState shouldBe ServerTransportState.Stopped
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
        fun `should transition to ShutdownFailed on close error`() = runTest {
            transport.shouldFailClose = true
            transport.start()

            transport.close()

            transport.currentState shouldBe ServerTransportState.ShutdownFailed
        }

        @Test
        fun `should call onClose even when close fails`() = runTest {
            var onCloseCalled = false
            transport.onClose { onCloseCalled = true }
            transport.shouldFailClose = true
            transport.start()

            transport.close()

            onCloseCalled shouldBe true
        }

        @Test
        fun `should propagate CancellationException on close`() = runTest {
            transport.shouldThrowCancellation = true
            transport.start()

            shouldThrow<CancellationException> {
                transport.close()
            }

            transport.currentState shouldBe ServerTransportState.ShutdownFailed
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
            "New, Initializing",
            "New, Stopped",
            "Initializing, Active",
            "Initializing, InitializationFailed",
            "Active, ShuttingDown",
            "ShuttingDown, Stopped",
            "ShuttingDown, ShutdownFailed",
        )
        fun `should allow valid transitions`(fromName: String, toName: String) {
            val from = ServerTransportState.valueOf(fromName)
            val to = ServerTransportState.valueOf(toName)

            transport.forceState(from)
            transport.testStateTransition(from, to)

            transport.currentState shouldBe to
        }

        @ParameterizedTest
        @CsvSource(
            "New, Active",
            "New, InitializationFailed",
            "New, ShuttingDown",
            "New, ShutdownFailed",
            "Initializing, New",
            "Initializing, Initializing",
            "Initializing, ShuttingDown",
            "Initializing, Stopped",
            "Active, New",
            "Active, Initializing",
            "Active, InitializationFailed",
            "Active, Active",
            "Active, Stopped",
            "Active, ShutdownFailed",
            "ShuttingDown, New",
            "ShuttingDown, Initializing",
            "ShuttingDown, Active",
            "ShuttingDown, ShuttingDown",
            "InitializationFailed, New",
            "InitializationFailed, Active",
            "Stopped, New",
            "Stopped, Active",
            "ShutdownFailed, New",
            "ShutdownFailed, Active",
        )
        fun `should reject invalid transitions`(fromName: String, toName: String) {
            val from = ServerTransportState.valueOf(fromName)
            val to = ServerTransportState.valueOf(toName)

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
        fun `should send message successfully when Active`() = runTest {
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
            exception.message shouldContain "Transport is not active"
        }

        @Test
        fun `should throw when sending after close`() = runTest {
            transport.start()
            transport.close()

            val exception = shouldThrow<McpException> {
                transport.send(PingRequest().toJSON())
            }

            exception.code shouldBe RPCError.ErrorCode.CONNECTION_CLOSED
            exception.message shouldContain "Transport is not active"
        }

        @ParameterizedTest
        @EnumSource(
            value = ServerTransportState::class,
            names = ["Active"],
            mode = EnumSource.Mode.EXCLUDE,
        )
        fun `should throw when sending in non-Active state`(state: ServerTransportState) = runTest {
            transport.forceState(state)

            val exception = shouldThrow<McpException> {
                transport.send(PingRequest().toJSON())
            }

            exception.code shouldBe RPCError.ErrorCode.CONNECTION_CLOSED
            exception.message shouldContain "Transport is not active"
            transport.sentMessages.isEmpty() shouldBe true
        }

        @Test
        fun `should call onError when performSend throws McpException`() = runTest {
            var capturedError: Throwable? = null
            transport.onError { capturedError = it }
            transport.shouldFailSend = true
            transport.sendException = McpException(RPCError.ErrorCode.INTERNAL_ERROR, "Send failed")

            transport.start()
            val exception = shouldThrow<McpException> {
                transport.send(PingRequest().toJSON())
            }

            capturedError shouldBe transport.sendException
            exception shouldBe transport.sendException
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
        fun `should be idempotent when closing from New state`() = runTest {
            transport.close()

            transport.currentState shouldBe ServerTransportState.Stopped
            transport.closeResourcesCalled shouldBe false
        }

        @Test
        fun `should be idempotent when closing from InitializationFailed state`() = runTest {
            transport.shouldFailInitialization = true
            shouldThrow<TestException> { transport.start() }

            transport.close()

            transport.currentState shouldBe ServerTransportState.InitializationFailed
            transport.closeResourcesCallCount shouldBe 1
        }

        @ParameterizedTest
        @EnumSource(
            value = ServerTransportState::class,
            names = ["ShuttingDown", "ShutdownFailed", "Stopped"],
        )
        fun `should be idempotent when closing from terminal or shutdown states`(state: ServerTransportState) =
            runTest {
                transport.forceState(state)
                val initialCloseCount = transport.closeResourcesCallCount

                transport.close()

                transport.currentState shouldBe state
                transport.closeResourcesCallCount shouldBe initialCloseCount
            }
    }

    @OptIn(InternalMcpApi::class)
    class TestServerTransport : AbstractServerTransport() {
        override val logger: KLogger = KotlinLogging.logger {}
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

        val currentState: ServerTransportState
            get() = state

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

        fun testStateTransition(from: ServerTransportState, to: ServerTransportState) {
            stateTransition(from, to)
        }

        fun forceState(newState: ServerTransportState) = updateState(newState)
    }

    private class TestException(message: String) : Exception(message)
}
