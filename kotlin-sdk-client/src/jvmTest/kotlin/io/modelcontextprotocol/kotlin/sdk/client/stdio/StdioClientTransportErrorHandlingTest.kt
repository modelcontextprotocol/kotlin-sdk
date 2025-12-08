package io.modelcontextprotocol.kotlin.sdk.client.stdio

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.coEvery
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.writeString
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for StdioClientTransport error handling: EOF, IO errors, and edge cases.
 */
class StdioClientTransportErrorHandlingTest {

    private lateinit var transport: StdioClientTransport

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun `should continue on stderr EOF`() = runTest {
        val stderrBuffer = Buffer()
        // Empty stderr = immediate EOF

        val inputBuffer = createNonEmptyBuffer {
            """data: {"jsonrpc":"2.0","method":"ping","id":1}\n\n"""
        }
        val outputBuffer = Buffer()

        transport = StdioClientTransport(
            input = inputBuffer,
            output = outputBuffer,
            error = stderrBuffer,
        )

        val closeCalled = AtomicBoolean(false)
        transport.onClose { closeCalled.store(true) }

        transport.start()
        delay(200.milliseconds)

        // Stderr EOF should not close transport
        closeCalled.load() shouldBe false

        transport.close()
        closeCalled.load() shouldBe true
    }

    @Test
    fun `should call onClose exactly once on error scenarios`() = runTest {
        val stderrBuffer = createNonEmptyBuffer {
            "FATAL: critical error\n"
        }

        val inputBuffer = Buffer()
        val outputBuffer = Buffer()

        var closeCallCount = 0

        transport = StdioClientTransport(
            input = inputBuffer,
            output = outputBuffer,
            error = stderrBuffer,
            classifyStderr = { StdioClientTransport.StderrSeverity.FATAL },
        )

        transport.onClose { closeCallCount++ }

        transport.start()
        delay(100.milliseconds)

        // Explicit close after error already closed it
        transport.close()

        closeCallCount shouldBe 1
    }

    @Test
    fun `should handle empty input gracefully`() = runTest {
        val inputBuffer = Buffer()
        val outputBuffer = Buffer()

        transport = StdioClientTransport(
            input = inputBuffer,
            output = outputBuffer,
        )

        var errorCalled = false
        transport.onError { errorCalled = true }

        transport.start()
        delay(100.milliseconds)

        // Empty input should close cleanly without error
        errorCalled.shouldBeFalse()
    }

    companion object {
        @JvmStatic
        fun exceptions(): Stream<Arguments> = Stream.of(
            Arguments.of(
                CancellationException(),
                false, // should not wrap, propagate
                null,
            ),
            Arguments.of(
                McpException(-1, "dummy"),
                false, // should not wrap, propagate
                null,
            ),
            Arguments.of(
                ClosedSendChannelException("dummy"),
                true, // should wrap in McpException
                ErrorCode.CONNECTION_CLOSED,
            ),
            Arguments.of(
                Exception(),
                true,
                ErrorCode.INTERNAL_ERROR,
            ),
            Arguments.of(
                OutOfMemoryError(),
                true,
                ErrorCode.INTERNAL_ERROR,
            ),

        )
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    fun `Send should handle exceptions`(throwable: Throwable, shouldWrap: Boolean, expectedCode: Int?) = runTest {
        val sendChannel: Channel<JSONRPCMessage> = mockk(relaxed = true)

        val stdin = createNonEmptyBuffer { "id: 1\ndata:\n" }
        transport = StdioClientTransport(
            input = stdin,
            output = Buffer(),
            sendChannel = sendChannel,
        )

        coEvery { sendChannel.send(any()) } throws throwable

        transport.start()

        // Cancel the coroutine while it's suspended in send()
        val exception = shouldThrow<Throwable> {
            transport.send(JSONRPCRequest(id = "test-1", method = "test/method"))
        }

        if (shouldWrap) {
            exception.shouldBeInstanceOf<McpException> {
                it.cause shouldBeSameInstanceAs throwable
                it.code shouldBe expectedCode
            }
        } else {
            exception shouldBeSameInstanceAs throwable
        }
    }

    fun createNonEmptyBuffer(block: () -> String): Buffer {
        val buffer = Buffer()
        buffer.writeString(block())
        return buffer
    }
}
