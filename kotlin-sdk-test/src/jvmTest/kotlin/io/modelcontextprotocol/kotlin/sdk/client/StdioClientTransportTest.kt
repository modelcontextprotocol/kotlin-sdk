package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.shared.BaseTransportTest
import kotlinx.coroutines.test.runTest
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.Test

class StdioClientTransportTest : BaseTransportTest() {
    @Test
    fun `should start then close cleanly`() = runTest {
        // Run process "/usr/bin/tee"
        val processBuilder = ProcessBuilder("/usr/bin/tee")
        val process = processBuilder.start()

        val input = process.inputStream.asSource().buffered()
        val output = process.outputStream.asSink().buffered()

        val client = StdioClientTransport(
            input = input,
            output = output,
        )

        testTransportOpenClose(client)

        process.destroy()
    }

    @Test
    fun `should read messages`() = runTest {
        val processBuilder = ProcessBuilder("/usr/bin/tee")
        val process = processBuilder.start()

        val input = process.inputStream.asSource().buffered()
        val output = process.outputStream.asSink().buffered()

        val client = StdioClientTransport(
            input = input,
            output = output,
        )

        testTransportRead(client)

        process.waitFor()
        process.destroy()
    }

    @Test
    fun `should ignore first output messages`() = runTest {
        val processBuilder = ProcessBuilder("/usr/bin/tee")
        val process = processBuilder.start()
        process.outputStream.write("Stdio server started".toByteArray())

        val input = process.inputStream.asSource().buffered()
        val output = process.outputStream.asSink().buffered()

        val client = StdioClientTransport(
            input = input,
            output = output,
        )

        testTransportRead(client)

        process.waitFor()
        process.destroy()
    }
}
