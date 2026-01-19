package io.modelcontextprotocol.kotlin.sdk.conformance

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.test.utils.NPX
import io.modelcontextprotocol.kotlin.test.utils.findFreePort
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.management.ManagementFactory
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempFile
import kotlin.properties.Delegates

private val logger = KotlinLogging.logger {}

private const val CONFORMANCE_VERSION = "0.1.8"

enum class TransportType {
    SSE,
    WEBSOCKET,
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConformanceTest {

    private var serverProcess: Process? = null
    private var serverPort: Int by Delegates.notNull()
    private val serverErrorOutput = mutableListOf<String>()
    private val maxErrorLines = 500

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

        private val SERVER_TRANSPORT_TYPES = listOf(
            TransportType.SSE,
            // TODO: Fix
//            TransportType.WEBSOCKET,
        )

        private val CLIENT_TRANSPORT_TYPES = listOf(
            TransportType.SSE,
            TransportType.WEBSOCKET,
        )

        private const val DEFAULT_TEST_TIMEOUT_SECONDS = 30L
        private const val DEFAULT_SERVER_STARTUP_TIMEOUT_SECONDS = 10
        private const val INITIAL_BACKOFF_MS = 50L
        private const val MAX_BACKOFF_MS = 500L
        private const val BACKOFF_MULTIPLIER = 1.5
        private const val CONNECTION_TIMEOUT_MS = 500
        private const val GRACEFUL_SHUTDOWN_SECONDS = 5L
        private const val FORCE_SHUTDOWN_SECONDS = 2L

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
                        synchronized(serverErrorOutput) {
                            if (serverErrorOutput.size >= maxErrorLines) {
                                serverErrorOutput.removeAt(0)
                            }
                            serverErrorOutput.add(line)
                        }
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
            val errorInfo = synchronized(serverErrorOutput) {
                if (serverErrorOutput.isNotEmpty()) {
                    "\n\nServer error output:\n${serverErrorOutput.joinToString("\n")}"
                } else {
                    ""
                }
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
    fun `MCP Server Conformance Tests`(): List<DynamicTest> = SERVER_TRANSPORT_TYPES.flatMap { transportType ->
        SERVER_SCENARIOS.map { scenario ->
            DynamicTest.dynamicTest("Server [$transportType]: $scenario") {
                runServerConformanceTest(scenario, transportType)
            }
        }
    }

    @TestFactory
    fun `MCP Client Conformance Tests`(): List<DynamicTest> = CLIENT_TRANSPORT_TYPES.flatMap { transportType ->
        CLIENT_SCENARIOS.map { scenario ->
            DynamicTest.dynamicTest("Client [$transportType]: $scenario") {
                runClientConformanceTest(scenario, transportType)
            }
        }
    }

    private fun runServerConformanceTest(scenario: String, transportType: TransportType) {
        val processBuilder = when (transportType) {
            TransportType.SSE -> {
                val serverUrl = "http://127.0.0.1:$serverPort/mcp"
                ProcessBuilder(
                    NPX,
                    "@modelcontextprotocol/conformance@$CONFORMANCE_VERSION",
                    "server",
                    "--url",
                    serverUrl,
                    "--scenario",
                    scenario,
                )
            }

            TransportType.WEBSOCKET -> {
                val serverUrl = "ws://127.0.0.1:$serverPort/ws"
                ProcessBuilder(
                    NPX,
                    "@modelcontextprotocol/conformance@$CONFORMANCE_VERSION",
                    "server",
                    "--url",
                    serverUrl,
                    "--scenario",
                    scenario,
                )
            }
        }

        runConformanceTest("server", scenario, processBuilder, transportType)
    }

    private fun runClientConformanceTest(scenario: String, transportType: TransportType) {
        val testClasspath = getTestClasspath()

        // Create an argfile to avoid Windows command line length limits
        val argFile = createTempFile(suffix = ".args").toFile()
        argFile.deleteOnExit()

        val mainClass = when (transportType) {
            TransportType.SSE -> {
                argFile.writeText(
                    buildString {
                        appendLine("-cp")
                        appendLine(testClasspath)
                        appendLine("io.modelcontextprotocol.kotlin.sdk.conformance.ConformanceClientKt")
                    },
                )
                "http://127.0.0.1:$serverPort/mcp"
            }

            TransportType.WEBSOCKET -> {
                argFile.writeText(
                    buildString {
                        appendLine("-cp")
                        appendLine(testClasspath)
                        appendLine("io.modelcontextprotocol.kotlin.sdk.conformance.WebSocketConformanceClientKt")
                    },
                )
                "ws://127.0.0.1:$serverPort/ws"
            }
        }

        val clientCommand = listOf(
            "java",
            "@${argFile.absolutePath}",
            mainClass,
        )

        val processBuilder = ProcessBuilder(
            NPX,
            "@modelcontextprotocol/conformance@$CONFORMANCE_VERSION",
            "client",
            "--command",
            clientCommand.joinToString(" "),
            "--scenario",
            scenario,
        )

        runConformanceTest("client", scenario, processBuilder, transportType)
    }

    private fun runConformanceTest(
        type: String,
        scenario: String,
        processBuilder: ProcessBuilder,
        transportType: TransportType,
    ) {
        val capitalizedType = type.replaceFirstChar { it.uppercase() }
        logger.info { "Running $type conformance test [$transportType]: $scenario" }

        val timeoutSeconds = System.getenv("CONFORMANCE_TEST_TIMEOUT_SECONDS")?.toLongOrNull()
            ?: DEFAULT_TEST_TIMEOUT_SECONDS

        val process = processBuilder.start()
        Thread {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        logger.debug { "test stderr: $line" }
                    }
                }
            } catch (e: Exception) {
                logger.trace(e) { "Error reading test stderr" }
            }
        }.apply {
            name = "test-stderr-reader"
            isDaemon = true
        }.start()
        Thread {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        logger.debug { "test stdout: $line" }
                    }
                }
            } catch (e: Exception) {
                logger.trace(e) { "Error reading server test stdout" }
            }
        }.apply {
            name = "test-stderr-reader"
            isDaemon = true
        }.start()
        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

        if (!completed) {
            logger.error {
                "$capitalizedType conformance test [$transportType] '$scenario' timed out after $timeoutSeconds seconds"
            }
            process.destroyForcibly()
            throw AssertionError(
                "❌ $capitalizedType conformance test [$transportType] '$scenario' timed out after $timeoutSeconds seconds",
            )
        }

        when (val exitCode = process.exitValue()) {
            0 -> logger.info { "✅ $capitalizedType conformance test [$transportType] '$scenario' passed!" }

            else -> {
                logger.error {
                    "$capitalizedType conformance test [$transportType] '$scenario' failed with exit code: $exitCode"
                }
                throw AssertionError(
                    "❌ $capitalizedType conformance test [$transportType] '$scenario' failed (exit code: $exitCode). Check test output above for details.",
                )
            }
        }
    }
}
