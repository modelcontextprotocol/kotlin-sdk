package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.shared.BaseTransportTest
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Timeout(30, unit = TimeUnit.SECONDS)
class StdioClientTransportTest : BaseTransportTest() {

    @Test
    fun `handle stdio error`(): Unit = runBlocking {
        val processBuilder = if (System.getProperty("os.name").lowercase().contains("win")) {
            ProcessBuilder("cmd", "/c", "pause 1 && echo simulated error 1>&2 && exit 1")
        } else {
            ProcessBuilder("sh", "-c", "sleep 1 && echo 'simulated error' >&2 && exit 1")
        }

        val process = processBuilder.start()

        val stdin = process.inputStream.asSource().buffered()
        val stdout = process.outputStream.asSink().buffered()
        val stderr = process.errorStream.asSource().buffered()

        val transport = StdioClientTransport(
            input = stdin,
            output = stdout,
            error = stderr,
        ) {
            println("💥Ah-oh!, error: \"$it\"")
            StdioClientTransport.StderrSeverity.FATAL
        }

        val client = Client(
            clientInfo = Implementation(
                name = "test-client",
                version = "1.0",
            ),
        )

        // The error in stderr should cause connecting to fail
        assertThrows<McpException> {
            client.connect(transport)
        }

        process.destroyForcibly()
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun `should start then close cleanly`() = runTest {
        // Run process "/usr/bin/tee"
        val processBuilder = ProcessBuilder("/usr/bin/tee")
        val process = processBuilder.start()

        val input = process.inputStream.asSource().buffered()
        val output = process.outputStream.asSink().buffered()
        val error = process.errorStream.asSource().buffered()

        val transport = StdioClientTransport(
            input = input,
            output = output,
            error = error,
        )

        transport.onError { error ->
            fail("Unexpected error: $error")
        }

        val didClose = AtomicBoolean(false)
        transport.onClose { didClose.store(true) }

        transport.start()
        delay(1.seconds)

        assertFalse(didClose.load(), "Transport should not be closed immediately after start")

        // Destroy process BEFORE close() to unblock stdin reader
        process.destroyForcibly()
        delay(100.milliseconds) // Give time for EOF to propagate

        transport.close()

        assertTrue(didClose.load(), "Transport should be closed after close() call")
    }

    @Test
    fun `should read messages`() = runTest {
        val processBuilder = ProcessBuilder("/usr/bin/tee")
        val process = processBuilder.start()

        val input = process.inputStream.asSource().buffered()
        val output = process.outputStream.asSink().buffered()

        val transport = StdioClientTransport(
            input = input,
            output = output,
        )

        testTransportRead(transport)

        process.waitFor()
        process.destroyForcibly()
    }

    @Test
    fun `should ignore first output messages`() = runTest {
        val processBuilder = ProcessBuilder("/usr/bin/tee")
        val process = processBuilder.start()
        process.outputStream.write("Stdio server started".toByteArray())

        val input = process.inputStream.asSource().buffered()
        val output = process.outputStream.asSink().buffered()

        val transport = StdioClientTransport(
            input = input,
            output = output,
        )

        testTransportRead(transport)

        process.waitFor()
        process.destroyForcibly()
    }
}
