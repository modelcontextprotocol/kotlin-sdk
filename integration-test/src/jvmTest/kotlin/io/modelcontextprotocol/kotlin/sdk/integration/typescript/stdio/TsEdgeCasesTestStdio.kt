package io.modelcontextprotocol.kotlin.sdk.integration.typescript.stdio

import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TransportKind
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TsTestBase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TsEdgeCasesTestStdio : TsTestBase() {

    override val transportKind: TransportKind = TransportKind.STDIO

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testNonExistentToolOverStdio() {
        val output = runStdioClient("non-existent-tool", "TestUser")
        assertTrue(output.contains("Tool \"non-existent-tool\" not found"), "Should report non-existent tool.\n$output")
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testSpecialCharactersOverStdio() {
        val specialChars = "!@#$+-[].,?"
        val tempFile = File.createTempFile("special_chars", ".txt").apply {
            writeText(specialChars)
            deleteOnExit()
        }
        val content = tempFile.readText()
        val output = runStdioClient("greet", content)
        assertTrue(output.contains("Hello, $specialChars!"), "Tool should handle special characters.\n$output")
        assertTrue(output.contains("Disconnected from server"), "Client should disconnect cleanly.\n$output")
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun testLargePayloadOverStdio() {
        val largeName = "A".repeat(10 * 1024)
        val tempFile = File.createTempFile("large_name", ".txt").apply {
            writeText(largeName)
            deleteOnExit()
        }
        val content = tempFile.readText()
        val output = runStdioClient("greet", content)
        tempFile.delete()
        assertTrue(
            output.contains("Hello,") && output.contains("A".repeat(20)),
            "Should handle large payloads.\n$output",
        )
        assertTrue(output.contains("Disconnected from server"), "Client should disconnect cleanly.\n$output")
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun testComplexConcurrentRequestsOverStdio() {
        val commands: List<Array<String>> = listOf(
            arrayOf("greet", "Client1"),
            arrayOf("multi-greet", "Client2"),
            arrayOf("greet", "Client3"),
            emptyArray(),
            arrayOf("multi-greet", "Client5"),
        )

        val threads = commands.mapIndexed { index, args ->
            Thread {
                val output = runStdioClient(*args)
                assertTrue(
                    output.contains("Disconnected from server"),
                    "Client $index should disconnect cleanly.\n$output",
                )
                when {
                    args.contentEquals(arrayOf("greet", "Client1")) ->
                        assertTrue(
                            output.contains("Hello, Client1!"),
                            "Client 1 should receive correct greeting.\n$output",
                        )

                    args.contentEquals(arrayOf("multi-greet", "Client2")) ->
                        assertTrue(
                            output.contains("Multiple greetings") || output.contains("greeting"),
                            "Client 2 should receive multiple greetings.\n$output",
                        )

                    args.contentEquals(arrayOf("greet", "Client3")) ->
                        assertTrue(
                            output.contains("Hello, Client3!"),
                            "Client 3 should receive correct greeting.\n$output",
                        )

                    args.isEmpty() ->
                        assertTrue(
                            output.contains("Available utils:"),
                            "Client 4 should list available tools.\n$output",
                        )

                    args.contentEquals(arrayOf("multi-greet", "Client5")) ->
                        assertTrue(
                            output.contains("Multiple greetings") || output.contains("greeting"),
                            "Client 5 should receive multiple greetings.\n$output",
                        )
                }
            }.apply { start() }
        }

        threads.forEach { it.join() }
    }

    @Test
    @Timeout(120, unit = TimeUnit.SECONDS)
    fun testRapidSequentialRequestsOverStdio() {
        val outputs = (1..10).map { i ->
            val output = runStdioClient("greet", "RapidClient$i")
            assertTrue(output.contains("Hello, RapidClient$i!"), "Client $i should receive correct greeting.\n$output")
            assertTrue(output.contains("Disconnected from server"), "Client $i should disconnect cleanly.\n$output")
            output
        }
        assertEquals(10, outputs.size, "All 10 rapid requests should complete successfully")
    }
}
