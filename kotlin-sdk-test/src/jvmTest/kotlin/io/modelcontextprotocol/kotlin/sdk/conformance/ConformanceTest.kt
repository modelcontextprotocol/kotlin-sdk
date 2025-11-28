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
import kotlin.properties.Delegates

private val logger = KotlinLogging.logger {}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConformanceTest {

    private var serverProcess: Process? = null
    private var serverPort: Int by Delegates.notNull()
    private val serverErrorOutput = StringBuffer()

    companion object {
        private val SERVER_SCENARIOS = listOf(
            "server-initialize",
            "tools-list",
            "tools-call-simple-text",
            "resources-list",
            "prompts-list",
            // TODO: Fix
            // - resources-read-text
            // - prompts-get-simple
        )

        private val CLIENT_SCENARIOS = listOf(
            "initialize",
            // TODO: Fix
            // "tools-call",
        )

        private const val DEFAULT_TEST_TIMEOUT_SECONDS = 30L
        private const val DEFAULT_SERVER_STARTUP_TIMEOUT_SECONDS = 10
        private const val INITIAL_BACKOFF_MS = 50L
        private const val MAX_BACKOFF_MS = 500L
        private const val BACKOFF_MULTIPLIER = 1.5
        private const val CONNECTION_TIMEOUT_MS = 500
        private const val GRACEFUL_SHUTDOWN_SECONDS = 5L
        private const val FORCE_SHUTDOWN_SECONDS = 2L

        private fun findFreePort(): Int = ServerSocket(0).use { it.localPort }

        private fun getRuntimeClasspath(): String = ManagementFactory.getRuntimeMXBean().classPath

        private fun getTestClasspath(): String = System.getProperty("test.classpath") ?: getRuntimeClasspath()

        private fun waitForServerReady(
            url: String,
            timeoutSeconds: Int = DEFAULT_SERVER_STARTUP_TIMEOUT_SECONDS,
        ): Boolean {
            val deadline = System.currentTimeMillis() + (timeoutSeconds * 1000)
            var lastError: Exception? = null
            var backoffMs = INITIAL_BACKOFF_MS

            while (System.currentTimeMillis() < deadline) {
                try {
                    val connection = URI(url).toURL().openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = CONNECTION_TIMEOUT_MS
                    connection.readTimeout = CONNECTION_TIMEOUT_MS
                    connection.connect()

                    val responseCode = connection.responseCode
                    connection.disconnect()
                    logger.debug { "Server responded with code: $responseCode" }
                    return true
                } catch (e: Exception) {
                    lastError = e
                    Thread.sleep(backoffMs)
                    backoffMs = (backoffMs * BACKOFF_MULTIPLIER).toLong().coerceAtMost(MAX_BACKOFF_MS)
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
            "-cp",
            getRuntimeClasspath(),
            "io.modelcontextprotocol.kotlin.sdk.conformance.ConformanceServerKt",
            serverPort.toString(),
        )

        val process = processBuilder.start()
        serverProcess = process

        // capture stderr in the background
        Thread {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        serverErrorOutput.appendLine(line)
                        logger.debug { "Server stderr: $line" }
                    }
                }
            } catch (e: Exception) {
                logger.trace(e) { "Error reading server stderr" }
            }
        }.apply {
            name = "server-stderr-reader"
            isDaemon = true
        }.start()

        logger.info { "Waiting for server to start..." }
        val serverReady = waitForServerReady(serverUrl)

        if (!serverReady) {
            val errorInfo = if (serverErrorOutput.isNotEmpty()) {
                "\n\nServer error output:\n$serverErrorOutput"
            } else {
                ""
            }
            serverProcess?.destroyForcibly()
            throw IllegalStateException(
                "Server failed to start within $DEFAULT_SERVER_STARTUP_TIMEOUT_SECONDS seconds. " +
                    "Check if port $serverPort is available.$errorInfo",
            )
        }

        logger.info { "Server started successfully at $serverUrl" }
    }

    @AfterAll
    fun stopServer() {
        serverProcess?.also { process ->
            logger.info { "Stopping conformance test server (PID: ${process.pid()})" }

            try {
                process.destroy()
                val terminated = process.waitFor(GRACEFUL_SHUTDOWN_SECONDS, TimeUnit.SECONDS)

                if (!terminated) {
                    logger.warn { "Server did not terminate gracefully, forcing shutdown..." }
                    process.destroyForcibly()
                    process.waitFor(FORCE_SHUTDOWN_SECONDS, TimeUnit.SECONDS)
                } else {
                    logger.info { "Server stopped gracefully" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error stopping server process" }
            } finally {
                serverProcess = null
            }
        } ?: logger.debug { "No server process to stop" }
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
    fun `MCP Client Conformance Tests`(): List<DynamicTest> = CLIENT_SCENARIOS.map { scenario ->
        DynamicTest.dynamicTest("Client: $scenario") {
            runClientConformanceTest(scenario)
        }
    }

    private fun runServerConformanceTest(scenario: String, serverUrl: String) {
        val processBuilder = ProcessBuilder(
            "npx",
            "@modelcontextprotocol/conformance",
            "server",
            "--url",
            serverUrl,
            "--scenario",
            scenario,
        ).apply {
            inheritIO()
        }

        runConformanceTest("server", scenario, processBuilder)
    }

    private fun runClientConformanceTest(scenario: String) {
        val testClasspath = getTestClasspath()

        val clientCommand = listOf(
            "java",
            "-cp",
            testClasspath,
            "io.modelcontextprotocol.kotlin.sdk.conformance.ConformanceClientKt",
        )

        val processBuilder = ProcessBuilder(
            "npx",
            "@modelcontextprotocol/conformance",
            "client",
            "--command",
            clientCommand.joinToString(" "),
            "--scenario",
            scenario,
        ).apply {
            inheritIO()
        }

        runConformanceTest("client", scenario, processBuilder)
    }

    private fun runConformanceTest(type: String, scenario: String, processBuilder: ProcessBuilder) {
        val capitalizedType = type.replaceFirstChar { it.uppercase() }
        logger.info { "Running $type conformance test: $scenario" }

        val timeoutSeconds = System.getenv("CONFORMANCE_TEST_TIMEOUT_SECONDS")?.toLongOrNull()
            ?: DEFAULT_TEST_TIMEOUT_SECONDS

        val process = processBuilder.start()
        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

        if (!completed) {
            logger.error {
                "$capitalizedType conformance test '$scenario' timed out after $timeoutSeconds seconds"
            }
            process.destroyForcibly()
            throw AssertionError(
                "❌ $capitalizedType conformance test '$scenario' timed out after $timeoutSeconds seconds",
            )
        }

        when (val exitCode = process.exitValue()) {
            0 -> logger.info { "✅ $capitalizedType conformance test '$scenario' passed!" }

            else -> {
                logger.error {
                    "$capitalizedType conformance test '$scenario' failed with exit code: $exitCode"
                }
                throw AssertionError(
                    "❌ $capitalizedType conformance test '$scenario' failed (exit code: $exitCode). Check test output above for details.",
                )
            }
        }
    }
}
