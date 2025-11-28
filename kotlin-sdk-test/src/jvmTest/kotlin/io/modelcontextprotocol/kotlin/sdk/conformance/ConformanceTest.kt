package io.modelcontextprotocol.kotlin.sdk.conformance

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.management.ManagementFactory
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConformanceTest {

    private var serverProcess: Process? = null
    private var serverPort: Int = 0
    private val serverErrorOutput = StringBuilder()

    companion object {
        private val SERVER_SCENARIOS = listOf(
            "server-initialize",
            "tools-list",
            "tools-call-simple-text",
            "resources-list",
            "prompts-list",
            // TODO: Fix
            // The following scenarios are failing (likely due to us not meeting the latest specification):
            // - resources-read-text
            // - prompts-get-simple
        )

        private val CLIENT_SCENARIOS = listOf(
            "initialize",
            // TODO: Fix
            // The following scenarios are failing (likely due to us not meeting the latest specification):
            // "tools-call",
        )

        private const val DEFAULT_TEST_TIMEOUT_SECONDS = 30L
        private const val DEFAULT_SERVER_STARTUP_TIMEOUT_SECONDS = 10

        private fun findFreePort(): Int {
            return ServerSocket(0).use { it.localPort }
        }

        private fun getRuntimeClasspath(): String {
            return ManagementFactory.getRuntimeMXBean().classPath
        }

        private fun getTestClasspath(): String {
            return System.getProperty("test.classpath") ?: getRuntimeClasspath()
        }

        private fun waitForServerReady(
            url: String,
            timeoutSeconds: Int = DEFAULT_SERVER_STARTUP_TIMEOUT_SECONDS,
        ): Boolean {
            val deadline = System.currentTimeMillis() + (timeoutSeconds * 1000)
            var lastError: Exception? = null
            var backoffMs = 50L

            while (System.currentTimeMillis() < deadline) {
                try {
                    val connection = URI(url).toURL().openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 500
                    connection.readTimeout = 500
                    connection.connect()

                    val responseCode = connection.responseCode
                    connection.disconnect()
                    logger.debug { "Server responded with code: $responseCode" }
                    return true
                } catch (e: Exception) {
                    lastError = e
                    Thread.sleep(backoffMs)
                    backoffMs = (backoffMs * 1.5).toLong().coerceAtMost(500)
                }
            }

            logger.error { "Server did not start within $timeoutSeconds seconds. Last error: ${lastError?.message}" }
            return false
        }
    }

    @BeforeAll
    fun startServer() {
        serverPort = findFreePort()
        val serverUrl = "http://127.0.0.1:$serverPort/mcp"

        logger.info { "Starting conformance test server on port $serverPort" }

        val processBuilder = ProcessBuilder(
            "java",
            "-cp", getRuntimeClasspath(),
            "io.modelcontextprotocol.kotlin.sdk.conformance.ConformanceServerKt",
            serverPort.toString()
        )

        serverProcess = processBuilder.start()

        // capture stderr in the background
        Thread {
            try {
                BufferedReader(InputStreamReader(serverProcess!!.errorStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        serverErrorOutput.appendLine(line)
                        logger.debug { "Server stderr: $line" }
                    }
                }
            } catch (e: Exception) {
                logger.trace(e) { "Error reading server stderr" }
            }
        }.start()

        logger.info { "Waiting for server to start..." }
        val serverReady = waitForServerReady(serverUrl)

        if (!serverReady) {
            val errorInfo = if (serverErrorOutput.isNotEmpty()) {
                "\n\nServer error output:\n${serverErrorOutput}"
            } else {
                ""
            }
            serverProcess?.destroyForcibly()
            throw IllegalStateException(
                "Server failed to start within $DEFAULT_SERVER_STARTUP_TIMEOUT_SECONDS seconds. " +
                    "Check if port $serverPort is available.$errorInfo"
            )
        }

        logger.info { "Server started successfully at $serverUrl" }
    }

    @AfterAll
    fun stopServer() {
        if (serverProcess == null) {
            logger.debug { "No server process to stop" }
            return
        }

        logger.info { "Stopping conformance test server (PID: ${serverProcess?.pid()})" }

        try {
            serverProcess?.destroy()
            val terminated = serverProcess?.waitFor(5, TimeUnit.SECONDS) ?: false

            if (!terminated) {
                logger.warn { "Server did not terminate gracefully, forcing shutdown..." }
                serverProcess?.destroyForcibly()
                serverProcess?.waitFor(2, TimeUnit.SECONDS) ?: false
            } else {
                logger.info { "Server stopped gracefully" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error stopping server process" }
        } finally {
            serverProcess = null
        }
    }

    @TestFactory
    fun `MCP Server Conformance Tests`(): List<DynamicTest> {
        val serverUrl = "http://127.0.0.1:$serverPort/mcp"

        return SERVER_SCENARIOS.map { scenario ->
            DynamicTest.dynamicTest("Server: $scenario") {
                runServerConformanceTest(scenario, serverUrl)
            }
        }
    }

    @TestFactory
    fun `MCP Client Conformance Tests`(): List<DynamicTest> {
        return CLIENT_SCENARIOS.map { scenario ->
            DynamicTest.dynamicTest("Client: $scenario") {
                runClientConformanceTest(scenario)
            }
        }
    }

    private fun runServerConformanceTest(scenario: String, serverUrl: String) {
        logger.info { "Running server conformance test: $scenario" }

        val timeoutSeconds = System.getenv("CONFORMANCE_TEST_TIMEOUT_SECONDS")?.toLongOrNull()
            ?: DEFAULT_TEST_TIMEOUT_SECONDS

        val process = ProcessBuilder(
            "npx",
            "@modelcontextprotocol/conformance",
            "server",
            "--url", serverUrl,
            "--scenario", scenario
        ).apply {
            inheritIO()
        }.start()

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

        if (!completed) {
            logger.error { "Server conformance test '$scenario' timed out after $timeoutSeconds seconds" }
            process.destroyForcibly()
            throw AssertionError("❌ Server conformance test '$scenario' timed out after $timeoutSeconds seconds")
        }

        val exitCode = process.exitValue()

        if (exitCode != 0) {
            logger.error { "Server conformance test '$scenario' failed with exit code: $exitCode" }
            throw AssertionError("❌ Server conformance test '$scenario' failed (exit code: $exitCode). Check test output above for details.")
        }

        logger.info { "✅ Server conformance test '$scenario' passed!" }
    }

    private fun runClientConformanceTest(scenario: String) {
        logger.info { "Running client conformance test: $scenario" }

        val timeoutSeconds = System.getenv("CONFORMANCE_TEST_TIMEOUT_SECONDS")?.toLongOrNull()
            ?: DEFAULT_TEST_TIMEOUT_SECONDS

        val testClasspath = getTestClasspath()

        val clientCommand = listOf(
            "java",
            "-cp", testClasspath,
            "io.modelcontextprotocol.kotlin.sdk.conformance.ConformanceClientKt"
        )

        val process = ProcessBuilder(
            "npx",
            "@modelcontextprotocol/conformance",
            "client",
            "--command", clientCommand.joinToString(" "),
            "--scenario", scenario
        ).apply {
            inheritIO()
        }.start()

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

        if (!completed) {
            logger.error { "Client conformance test '$scenario' timed out after $timeoutSeconds seconds" }
            process.destroyForcibly()
            throw AssertionError("❌ Client conformance test '$scenario' timed out after $timeoutSeconds seconds")
        }

        val exitCode = process.exitValue()

        if (exitCode != 0) {
            logger.error { "Client conformance test '$scenario' failed with exit code: $exitCode" }
            throw AssertionError("❌ Client conformance test '$scenario' failed (exit code: $exitCode). Check test output above for details.")
        }

        logger.info { "✅ Client conformance test '$scenario' passed!" }
    }
}
