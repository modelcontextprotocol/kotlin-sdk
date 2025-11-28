package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.util.concurrent.TimeUnit

/**
 * Integration tests for StdioClientTransport with real process I/O.
 *
 * These tests use real ProcessBuilder and shell commands, so they run sequentially
 * to avoid resource contention issues with parallel execution.
 */
@Execution(ExecutionMode.SAME_THREAD)
class StdioClientTransportIntegrationTest {

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun `handle stdio error`(): Unit = runBlocking {
        val processBuilder = if (System.getProperty("os.name").lowercase().contains("win")) {
            ProcessBuilder("cmd", "/c", "pause 0.5 && echo simulated error 1>&2 && exit 1")
        } else {
            ProcessBuilder("sh", "-c", "sleep 0.5 && echo 'simulated error' >&2 && exit 1")
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
            true
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
}
