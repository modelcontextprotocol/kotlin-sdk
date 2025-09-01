package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import io.modelcontextprotocol.kotlin.sdk.integration.utils.TestUtils.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeScriptEdgeCasesTest : TypeScriptTestBase() {

    private var port: Int = 0
    private lateinit var serverUrl: String
    private var httpServer: KotlinServerForTypeScriptClient? = null

    @BeforeEach
    fun setUp() {
        port = findFreePort()
        serverUrl = "http://localhost:$port/mcp"
        killProcessOnPort(port)
        httpServer = KotlinServerForTypeScriptClient()
        httpServer?.start(port)
        if (!waitForPort(port = port)) {
            throw IllegalStateException("Kotlin test server did not become ready on localhost:$port within timeout")
        }
        println("Kotlin server started on port $port")
    }

    @AfterEach
    fun tearDown() {
        try {
            httpServer?.stop()
            println("HTTP server stopped")
        } catch (e: Exception) {
            println("Error during server shutdown: ${e.message}")
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testErrorHandling() = runTest {
        val nonExistentToolCommand = "npx tsx myClient.ts $serverUrl non-existent-tool"
        val nonExistentToolOutput = executeCommandAllowingFailure(nonExistentToolCommand, tsClientDir)

        assertTrue(
            nonExistentToolOutput.contains("Tool \"non-existent-tool\" not found"),
            "Client should handle non-existent tool gracefully",
        )

        val invalidUrlCommand = "npx tsx myClient.ts http://localhost:${port + 1000}/mcp greet TestUser"
        val invalidUrlOutput = executeCommandAllowingFailure(invalidUrlCommand, tsClientDir)

        assertTrue(
            invalidUrlOutput.contains("Invalid URL") && invalidUrlOutput.contains("ERR_INVALID_URL"),
            "Client should handle connection errors gracefully",
        )
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testSpecialCharacters() = runTest {
        val specialChars = "!@#$+-[].,?"

        val tempFile = File.createTempFile("special_chars", ".txt")
        tempFile.writeText(specialChars)
        tempFile.deleteOnExit()

        val specialCharsContent = tempFile.readText()
        val specialCharsCommand = "npx tsx myClient.ts $serverUrl greet \"$specialCharsContent\""
        val specialCharsOutput = executeCommand(specialCharsCommand, tsClientDir)

        assertTrue(
            specialCharsOutput.contains("Hello, $specialChars!"),
            "Tool should handle special characters in arguments",
        )
        assertTrue(
            specialCharsOutput.contains("Disconnected from server"),
            "Client should disconnect cleanly after handling special characters",
        )
    }

    // skip on windows as it can't handle long commands
    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun testLargePayload() = runTest {
        val largeName = "A".repeat(10 * 1024)

        val tempFile = File.createTempFile("large_name", ".txt")
        tempFile.writeText(largeName)
        tempFile.deleteOnExit()

        val largeNameContent = tempFile.readText()
        val largePayloadCommand = "npx tsx myClient.ts $serverUrl greet \"$largeNameContent\""
        val largePayloadOutput = executeCommand(largePayloadCommand, tsClientDir)

        tempFile.delete()

        assertTrue(
            largePayloadOutput.contains("Hello,") && largePayloadOutput.contains("A".repeat(20)),
            "Tool should handle large payloads",
        )
        assertTrue(
            largePayloadOutput.contains("Disconnected from server"),
            "Client should disconnect cleanly after handling large payload",
        )
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun testComplexConcurrentRequests() = runTest {
        val commands = listOf(
            "npx tsx myClient.ts $serverUrl greet \"Client1\"",
            "npx tsx myClient.ts $serverUrl multi-greet \"Client2\"",
            "npx tsx myClient.ts $serverUrl greet \"Client3\"",
            "npx tsx myClient.ts $serverUrl",
            "npx tsx myClient.ts $serverUrl multi-greet \"Client5\"",
        )

        val threads = commands.mapIndexed { index, command ->
            Thread {
                println("Starting client $index")
                val output = executeCommand(command, tsClientDir)
                println("Client $index completed")

                assertTrue(
                    output.contains("Connected to server"),
                    "Client $index should connect to server",
                )
                assertTrue(
                    output.contains("Disconnected from server"),
                    "Client $index should disconnect cleanly",
                )

                when {
                    command.contains("greet \"Client1\"") ->
                        assertTrue(output.contains("Hello, Client1!"), "Client 1 should receive correct greeting")

                    command.contains("multi-greet \"Client2\"") ->
                        assertTrue(output.contains("Multiple greetings"), "Client 2 should receive multiple greetings")

                    command.contains("greet \"Client3\"") ->
                        assertTrue(output.contains("Hello, Client3!"), "Client 3 should receive correct greeting")

                    !command.contains("greet") && !command.contains("multi-greet") ->
                        assertTrue(output.contains("Available utils:"), "Client 4 should list available tools")

                    command.contains("multi-greet \"Client5\"") ->
                        assertTrue(output.contains("Multiple greetings"), "Client 5 should receive multiple greetings")
                }
            }.apply { start() }
        }

        threads.forEach { it.join() }
    }

    @Test
    @Timeout(120, unit = TimeUnit.SECONDS)
    fun testRapidSequentialRequests() = runTest {
        val outputs = (1..10).map { i ->
            val command = "npx tsx myClient.ts $serverUrl greet \"RapidClient$i\""
            val output = executeCommand(command, tsClientDir)

            assertTrue(
                output.contains("Connected to server"),
                "Client $i should connect to server",
            )
            assertTrue(
                output.contains("Hello, RapidClient$i!"),
                "Client $i should receive correct greeting",
            )
            assertTrue(
                output.contains("Disconnected from server"),
                "Client $i should disconnect cleanly",
            )

            output
        }

        assertEquals(10, outputs.size, "All 10 rapid requests should complete successfully")
    }
}
