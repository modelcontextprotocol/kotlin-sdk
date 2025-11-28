package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Unit tests for StdioClientTransport stderr error handling behavior.
 *
 * This test suite verifies the transport correctly distinguishes between:
 * - Fatal errors (processStdError returns true) - should terminate transport and invoke onError/onClose
 * - Non-fatal warnings (processStdError returns false) - should continue operation without terminating
 *
 * Uses mock sources to simulate stdin/stderr streams without real process I/O.
 */
@Timeout(10, unit = TimeUnit.SECONDS)
class StdioClientTransportTest {

    private lateinit var transport: StdioClientTransport

    @Test
    fun `should invoke onError and onClose when processStdError returns true for fatal error`(): Unit = runBlocking {
        val errorDetected = AtomicBoolean(false)
        val onErrorCalled = AtomicBoolean(false)
        val onCloseCalled = AtomicBoolean(false)
        val capturedError = AtomicReference<Throwable?>()

        // Create input that blocks (simulates waiting for server response)
        val inputSource = ControllableBlockingSource()

        // Create error stream that provides a fatal error message
        val errorMessage = "fatal error: connection failed\n"
        val errorSource = ByteArraySource(errorMessage.encodeToByteArray())

        // Create simple output sink that accepts writes
        val outputSink = NoOpSink()

        transport = StdioClientTransport(
            input = inputSource.buffered(),
            output = outputSink.buffered(),
            error = errorSource.buffered(),
            processStdError = {
                errorDetected.set(true)
                true // Fatal error - should terminate transport
            },
        )

        // Set up callbacks to track invocations
        transport.onError { error ->
            capturedError.set(error)
            onErrorCalled.set(true)
        }
        transport.onClose {
            onCloseCalled.set(true)
        }

        // Start the transport
        transport.start()

        // Use awaitility for elegant, readable async assertions
        await untilAsserted {
            errorDetected.get() shouldBe true
            onErrorCalled.get() shouldBe true
            onCloseCalled.get() shouldBe true
        }

        // Verify the error is of expected type
        val error = await untilNotNull { capturedError.get() }
        (error is McpException) shouldBe true

        // Clean up
        inputSource.unblock()
    }

    @Test
    @Suppress("MaxLineLength")
    fun `should NOT invoke onError when processStdError returns false for non-fatal warning`(): Unit = runBlocking {
        val warningDetected = AtomicBoolean(false)
        val onErrorCalled = AtomicBoolean(false)
        val onCloseCalled = AtomicBoolean(false)
        val capturedWarningMessage = AtomicReference<String?>()

        // Use blocking input so stderr has time to be processed before EOF
        val inputSource = ControllableBlockingSource()

        // Create error stream that provides a non-fatal warning
        val warningMessage = "warning: deprecated feature used\n"
        val errorSource = ByteArraySource(warningMessage.encodeToByteArray())

        // Create simple output sink
        val outputSink = NoOpSink()

        transport = StdioClientTransport(
            input = inputSource.buffered(),
            output = outputSink.buffered(),
            error = errorSource.buffered(),
        ) { msg ->
            warningDetected.set(true)
            capturedWarningMessage.set(msg)
            false // Non-fatal warning - should NOT terminate transport
        }

        // Set up callbacks to track invocations
        transport.onError {
            onErrorCalled.set(true)
        }
        transport.onClose {
            onCloseCalled.set(true)
        }

        // Start the transport
        transport.start()

        // Wait for warning to be processed - use awaitility DSL
        await untilAsserted {
            warningDetected.get() shouldBe true
            capturedWarningMessage.get() shouldBe "warning: deprecated feature used"
        }

        // Verify warning did NOT trigger error callback
        onErrorCalled.get() shouldBe false

        // Now unblock stdin to trigger close
        inputSource.unblock()

        // onClose WILL be called due to EOF on stdin/stderr - this is expected behavior
        // The key difference is that onError was NOT called
        await untilAsserted {
            onCloseCalled.get() shouldBe true
        }
    }

    @Test
    fun `should handle empty stderr stream gracefully`(): Unit = runBlocking {
        val onErrorCalled = AtomicBoolean(false)
        val onCloseCalled = AtomicBoolean(false)
        val processStdErrorCalled = AtomicBoolean(false)

        // Create empty streams
        val inputSource = ByteArraySource().buffered()
        val errorSource = ByteArraySource().buffered()
        val outputSink = NoOpSink().buffered()

        transport = StdioClientTransport(
            input = inputSource,
            output = outputSink,
            error = errorSource,
            processStdError = {
                processStdErrorCalled.set(true)
                false
            },
        )

        transport.onError { onErrorCalled.set(true) }
        transport.onClose { onCloseCalled.set(true) }

        transport.start()

        // Should close cleanly without processing any errors - use awaitility
        await untilAsserted {
            onCloseCalled.get() shouldBe true
            processStdErrorCalled.get() shouldBe false
            onErrorCalled.get() shouldBe false
        }
    }

    @Test
    fun `should process first stderr line and discard remaining buffer`(): Unit = runBlocking {
        val errorMessagesProcessed = mutableListOf<String>()
        val onCloseCalled = AtomicBoolean(false)

        // Create error stream with multiple lines
        // NOTE: StdioClientTransport.kt:78 calls readBuffer.clear() after reading one line,
        // so only the FIRST line will be processed - this is the actual implementation behavior
        val multipleLines = """
            warning: first warning
            warning: second warning will be discarded
            warning: third warning will be discarded

        """.trimIndent()
        val errorSource = ByteArraySource(multipleLines.encodeToByteArray())

        val inputSource = ByteArraySource()
        val outputSink = NoOpSink()

        transport = StdioClientTransport(
            input = inputSource.buffered(),
            output = outputSink.buffered(),
            error = errorSource.buffered(),
            processStdError = { msg ->
                synchronized(errorMessagesProcessed) {
                    errorMessagesProcessed.add(msg)
                }
                false // Non-fatal
            },
        )

        transport.onClose { onCloseCalled.set(true) }
        transport.start()

        // Wait for first message to be processed and transport to close - use awaitility
        await untilAsserted {
            onCloseCalled.get() shouldBe true
            errorMessagesProcessed.size shouldBe 1
            errorMessagesProcessed[0] shouldBe "warning: first warning"
        }
    }
}
