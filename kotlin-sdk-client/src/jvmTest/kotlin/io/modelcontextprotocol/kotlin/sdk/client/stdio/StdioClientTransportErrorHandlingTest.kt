package io.modelcontextprotocol.kotlin.sdk.client.stdio

import io.kotest.assertions.nondeterministic.eventually
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.writeString
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.stream.Stream
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for StdioClientTransport error handling: EOF, IO errors, and edge cases.
 */
class StdioClientTransportErrorHandlingTest {

    private lateinit var transport: StdioClientTransport

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun `should continue on stderr EOF`(): Unit = runBlocking(Dispatchers.IO) {
        // Empty stderr = immediate EOF
        val stderrBuffer = Buffer()

        // Create a pipe for stdin that stays open (simulates real stdin behavior)
        val pipedOutputStream = PipedOutputStream()
        val pipedInputStream = PipedInputStream(pipedOutputStream)

        // Write one message to stdin
        pipedOutputStream.write("""data: {"jsonrpc":"2.0","method":"ping","id":1}\n\n""".toByteArray())
        pipedOutputStream.flush()
        // Keep the pipe open by not closing pipedOutputStream - this prevents stdin EOF

        val outputBuffer = Buffer()

        transport = StdioClientTransport(
            input = pipedInputStream.asSource().buffered(),
            output = outputBuffer,
            error = stderrBuffer,
        )

        val closeCalled = AtomicBoolean(false)
        transport.onClose { closeCalled.store(true) }

        transport.start()

        // Wait for stderr EOF and stdin message to be processed
        delay(500.milliseconds)

        // Transport should still be alive because stdin is still open (not EOF'd)
        closeCalled.load() shouldBe false

        // Close pipes to trigger stdin EOF.
        // `transport.close()` cann't help here, since the underlying Java read() is blocked on I/O operation
        pipedOutputStream.close()
        pipedInputStream.close()

        // Transport should close when stdin EOF is detected
        eventually(2.seconds) {
            closeCalled.load() shouldBe true
        }
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

        // FATAL stderr should trigger close, wait for it to complete
        eventually(2.seconds) {
            closeCallCount shouldBe 1
        }

        // Explicit close after error already closed it (should be no-op)
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

        // Empty input should close cleanly without error
        // Wait for EOF processing to complete, verify no error was called
        eventually(2.seconds) {
            errorCalled.shouldBeFalse()
        }
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

        // Create stdin pipe that stays open to prevent transport from closing
        val pipedOutputStream = PipedOutputStream()
        val pipedInputStream = PipedInputStream(pipedOutputStream)
        // Keep pipe open (don't write or close) - stdin will block on read, not EOF

        transport = StdioClientTransport(
            input = pipedInputStream.asSource().buffered(),
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

        // Cleanup
        pipedOutputStream.close()
        pipedInputStream.close()
    }

    fun createNonEmptyBuffer(block: () -> String): Buffer {
        val buffer = Buffer()
        buffer.writeString(block())
        return buffer
    }
}
