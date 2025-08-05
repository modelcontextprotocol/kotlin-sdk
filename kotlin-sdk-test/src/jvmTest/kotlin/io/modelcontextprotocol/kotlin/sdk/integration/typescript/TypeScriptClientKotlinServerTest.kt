package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import io.modelcontextprotocol.kotlin.sdk.integration.utils.KotlinServerForTypeScriptClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class TypeScriptClientKotlinServerTest : TypeScriptTestBase() {

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
    @Timeout(20, unit = TimeUnit.SECONDS)
    fun testToolCall() {
        // call the "greet" tool
        val testName = "TestUser"
        val command = "npx tsx myClient.ts $serverUrl greet $testName"
        val output = executeCommand(command, tsClientDir)

        assertTrue(
            output.contains("Hello, $testName!"),
            "Tool response should contain the greeting with the provided name"
        )
        assertTrue(output.contains("Tool result:"), "Output should indicate a successful tool call")
        assertTrue(output.contains("Text content:"), "Output should contain the text content section")
        assertTrue(output.contains("Structured content:"), "Output should contain the structured content section")
        assertTrue(
            output.contains("\"greeting\": \"Hello, $testName!\""),
            "Structured content should contain the greeting"
        )
    }

    @Test
    @Timeout(20, unit = TimeUnit.SECONDS)
    fun testToolCallWithSessionManagement() {
        val testName = "SessionTest"
        val command = "npx tsx myClient.ts $serverUrl greet $testName"
        val output = executeCommand(command, tsClientDir)

        assertTrue(output.contains("Connected to server"), "Client should connect to server")
        assertTrue(
            output.contains("Hello, $testName!"),
            "Tool response should contain the greeting with the provided name"
        )
        assertTrue(output.contains("Tool result:"), "Output should indicate a successful tool call")
        assertTrue(output.contains("Disconnected from server"), "Client should disconnect cleanly")

        val multiGreetName = "NotificationTest"
        val multiGreetCommand = "npx tsx myClient.ts $serverUrl multi-greet $multiGreetName"
        val multiGreetOutput = executeCommand(multiGreetCommand, tsClientDir)

        assertTrue(multiGreetOutput.contains("Connected to server"), "Client should connect to server")
        assertTrue(
            multiGreetOutput.contains("Multiple greetings") || multiGreetOutput.contains("greeting"),
            "Tool response should contain greeting message"
        )
        assertTrue(multiGreetOutput.contains("Disconnected from server"), "Client should disconnect cleanly")
    }

    @Test
    @Timeout(120, unit = TimeUnit.SECONDS)
    fun testMultipleClientSequence() {
        val testName1 = "FirstClient"
        val command1 = "npx tsx myClient.ts $serverUrl greet $testName1"
        val output1 = executeCommand(command1, tsClientDir)

        assertTrue(output1.contains("Connected to server"), "First client should connect to server")
        assertTrue(output1.contains("Hello, $testName1!"), "Tool response should contain the greeting for first client")
        assertTrue(output1.contains("Disconnected from server"), "First client should disconnect cleanly")

        val testName2 = "SecondClient"
        val command2 = "npx tsx myClient.ts $serverUrl multi-greet $testName2"
        val output2 = executeCommand(command2, tsClientDir)

        assertTrue(output2.contains("Connected to server"), "Second client should connect to server")
        assertTrue(
            output2.contains("Multiple greetings") || output2.contains("greeting"),
            "Tool response should contain greeting message"
        )
        assertTrue(output2.contains("Disconnected from server"), "Second client should disconnect cleanly")

        val command3 = "npx tsx myClient.ts $serverUrl"
        val output3 = executeCommand(command3, tsClientDir)

        assertTrue(output3.contains("Connected to server"), "Third client should connect to server")
        assertTrue(output3.contains("Available utils:"), "Third client should list available utils")
        assertTrue(output3.contains("greet"), "Greet tool should be available to third client")
        assertTrue(output3.contains("multi-greet"), "Multi-greet tool should be available to third client")
        assertTrue(output3.contains("Disconnected from server"), "Third client should disconnect cleanly")
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testMultipleClientParallel() {
        val clientCount = 3
        val clients = listOf(
            "FirstClient" to "greet",
            "SecondClient" to "multi-greet",
            "ThirdClient" to ""
        )

        val threads = mutableListOf<Thread>()
        val outputs = mutableListOf<Pair<Int, String>>()
        val exceptions = mutableListOf<Exception>()

        for (i in 0 until clientCount) {
            val (clientName, toolName) = clients[i]
            val thread = Thread {
                try {
                    val command = if (toolName.isEmpty()) {
                        "npx tsx myClient.ts $serverUrl"
                    } else {
                        "npx tsx myClient.ts $serverUrl $toolName $clientName"
                    }

                    val output = executeCommand(command, tsClientDir)
                    synchronized(outputs) {
                        outputs.add(i to output)
                    }
                } catch (e: Exception) {
                    synchronized(exceptions) {
                        exceptions.add(e)
                    }
                }
            }
            threads.add(thread)
            thread.start()
            Thread.sleep(100)
        }

        threads.forEach { it.join() }

        assertTrue(exceptions.isEmpty(), "No exceptions should occur: ${exceptions.joinToString { it.message ?: "" }}")

        val sortedOutputs = outputs.sortedBy { it.first }.map { it.second }

        sortedOutputs.forEachIndexed { index, output ->
            val clientName = clients[index].first
            val toolName = clients[index].second

            when (toolName) {
                "greet" -> {
                    val containsGreeting = output.contains("Hello, $clientName!") ||
                            output.contains("\"greeting\": \"Hello, $clientName!\"")
                    assertTrue(
                        containsGreeting,
                        "Tool response should contain the greeting for $clientName"
                    )
                }

                "multi-greet" -> {
                    val containsGreeting = output.contains("Multiple greetings") ||
                            output.contains("greeting") ||
                            output.contains("greet")
                    assertTrue(
                        containsGreeting,
                        "Tool response should contain greeting message for $clientName"
                    )
                }
            }
        }
    }
}