package io.modelcontextprotocol.kotlin.sdk.client.stdio

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.writeString
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

        val inputBuffer = Buffer()
        inputBuffer.writeString("""data: {"jsonrpc":"2.0","method":"ping","id":1}\n\n""")
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
        val stderrBuffer = Buffer()
        stderrBuffer.write("FATAL: critical error\n".encodeToByteArray())

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
}
