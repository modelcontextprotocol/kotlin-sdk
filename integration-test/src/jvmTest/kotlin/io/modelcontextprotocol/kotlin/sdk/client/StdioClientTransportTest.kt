package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.assertions.nondeterministic.eventually
import io.modelcontextprotocol.kotlin.sdk.shared.BaseTransportTest
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.test.utils.createSleepyProcessBuilder
import io.modelcontextprotocol.kotlin.test.utils.createTeeProcessBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

@Timeout(30, unit = TimeUnit.SECONDS)
@DisabledOnOs(OS.WINDOWS) // TODO: fix running on windows
class StdioClientTransportTest : BaseTransportTest() {

    @Test
    fun `handle stdio error`(): Unit = runBlocking(Dispatchers.IO) {
        val processBuilder = createSleepyProcessBuilder()

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
        // Run loopback process
        val processBuilder = createTeeProcessBuilder()
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

        // Verify transport stays open after start (use eventually for cross-platform reliability)
        eventually(2.seconds) {
            assertFalse(didClose.load(), "Transport should not be closed immediately after start")
        }

        // Destroy process BEFORE close() to unblock stdin reader
        process.destroyForcibly()

        transport.close()

        // Verify transport is closed after close() call
        eventually(2.seconds) {
            assertTrue(didClose.load(), "Transport should be closed after close() call")
        }
    }

    @Test
    fun `should read messages`() = runTest {
        val processBuilder = createTeeProcessBuilder()
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
        val processBuilder = createTeeProcessBuilder()
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
