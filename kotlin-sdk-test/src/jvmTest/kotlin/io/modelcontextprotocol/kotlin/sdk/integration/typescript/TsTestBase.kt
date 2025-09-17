package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.sse.KotlinServerForTsClient
import io.modelcontextprotocol.kotlin.sdk.integration.utils.Retry
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.withTimeout
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeAll
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.seconds

enum class TransportKind { SSE, STDIO, DEFAULT }

@Retry(times = 3)
abstract class TsTestBase {

    protected open val transportKind: TransportKind = TransportKind.DEFAULT

    protected val projectRoot: File get() = File(System.getProperty("user.dir"))
    protected val tsClientDir: File
        get() {
            val base = File(
                projectRoot,
                "src/jvmTest/kotlin/io/modelcontextprotocol/kotlin/sdk/integration/typescript",
            )

            // Allow override via system property for CI: -Dts.transport=stdio|sse
            val fromProp = System.getProperty("ts.transport")?.lowercase()
            val overrideSubDir = when (fromProp) {
                "stdio" -> "stdio"
                "sse" -> "sse"
                else -> null
            }

            val subDirName = overrideSubDir ?: when (transportKind) {
                TransportKind.STDIO -> "stdio"
                TransportKind.SSE -> "sse"
                TransportKind.DEFAULT -> null
            }
            if (subDirName != null) {
                val sub = File(base, subDirName)
                if (sub.exists()) return sub
            }
            return base
        }

    companion object {
        @JvmStatic
        private val tempRootDir: File = createTempDirectory("typescript-sdk-").toFile().apply { deleteOnExit() }

        @JvmStatic
        protected val sdkDir: File = File(tempRootDir, "typescript-sdk")

        @JvmStatic
        @BeforeAll
        fun setupTypeScriptSdk() {
            println("Cloning TypeScript SDK repository")

            if (!sdkDir.exists()) {
                val process = ProcessBuilder(
                    "git",
                    "clone",
                    "--depth",
                    "1",
                    "https://github.com/modelcontextprotocol/typescript-sdk.git",
                    sdkDir.absolutePath,
                )
                    .redirectErrorStream(true)
                    .start()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    throw RuntimeException("Failed to clone TypeScript SDK repository: exit code $exitCode")
                }
            }

            println("Installing TypeScript SDK dependencies")
            executeCommand("npm install", sdkDir, allowFailure = false, timeoutSeconds = null)
        }

        @JvmStatic
        protected fun killProcessOnPort(port: Int) {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val killCommand = if (isWindows) {
                "netstat -ano | findstr :$port | for /f \"tokens=5\" %a in ('more')" +
                    " do taskkill /F /PID %a 2>nul || echo No process found"
            } else {
                "lsof -ti:$port | xargs kill -9 2>/dev/null || true"
            }
            executeCommand(killCommand, File("."), allowFailure = true, timeoutSeconds = null)
        }

        @JvmStatic
        protected fun findFreePort(): Int {
            ServerSocket(0).use { socket ->
                return socket.localPort
            }
        }

        @JvmStatic
        protected fun executeCommand(
            command: String,
            workingDir: File,
            allowFailure: Boolean = false,
            timeoutSeconds: Long? = null,
        ): String {
            if (!workingDir.exists()) {
                if (!workingDir.mkdirs()) {
                    throw RuntimeException("Failed to create working directory: ${workingDir.absolutePath}")
                }
            }

            if (!workingDir.isDirectory || !workingDir.canRead()) {
                throw RuntimeException("Working directory is not accessible: ${workingDir.absolutePath}")
            }

            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val processBuilder = if (isWindows) {
                ProcessBuilder()
                    .command("cmd.exe", "/c", "set TYPESCRIPT_SDK_DIR=${sdkDir.absolutePath} && $command")
            } else {
                ProcessBuilder()
                    .command("bash", "-c", "TYPESCRIPT_SDK_DIR='${sdkDir.absolutePath}' $command")
            }

            val process = processBuilder
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    println(line)
                    output.append(line).append("\n")
                }
            }

            if (timeoutSeconds == null) {
                val exitCode = process.waitFor()
                if (!allowFailure && exitCode != 0) {
                    throw RuntimeException(
                        "Command execution failed with exit code $exitCode: $command\n" +
                            "Working dir: ${workingDir.absolutePath}\nOutput:\n$output",
                    )
                }
            } else {
                process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            }

            return output.toString()
        }
    }

    private fun waitForProcessTermination(process: Process, timeoutSeconds: Long): Boolean {
        if (process.isAlive && !process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
            return false
        }
        return true
    }

    private fun createProcessOutputReader(process: Process, prefix: String = "TS-SERVER"): Thread {
        val outputReader = Thread {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        println("[$prefix] $line")
                    }
                }
            } catch (e: Exception) {
                println("Warning: Error reading process output: ${e.message}")
            }
        }
        outputReader.isDaemon = true
        return outputReader
    }

    private fun createProcessErrorReader(process: Process, prefix: String = "TS-SERVER"): Thread {
        val errorReader = Thread {
            try {
                process.errorStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        println("[$prefix][err] $line")
                    }
                }
            } catch (e: Exception) {
                println("Warning: Error reading process error stream: ${e.message}")
            }
        }
        errorReader.isDaemon = true
        return errorReader
    }

    protected fun waitForPort(host: String = "localhost", port: Int, timeoutSeconds: Long = 10): Boolean = try {
        await.atMost(timeoutSeconds, TimeUnit.SECONDS)
            .pollDelay(200, TimeUnit.MILLISECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until {
                try {
                    Socket(host, port).use { true }
                } catch (_: Exception) {
                    false
                }
            }
        true
    } catch (_: Exception) {
        false
    }

    protected fun executeCommandAllowingFailure(command: String, workingDir: File, timeoutSeconds: Long = 20): String =
        executeCommand(command, workingDir, allowFailure = true, timeoutSeconds = timeoutSeconds)

    protected fun startTypeScriptServer(port: Int): Process {
        killProcessOnPort(port)

        if (!sdkDir.exists() || !sdkDir.isDirectory) {
            throw IllegalStateException(
                "TypeScript SDK directory does not exist or is not accessible: ${sdkDir.absolutePath}",
            )
        }

        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val localServerPath = File(tsClientDir, "simpleStreamableHttp.ts").absolutePath
        val processBuilder = if (isWindows) {
            ProcessBuilder()
                .command(
                    "cmd.exe",
                    "/c",
                    "set MCP_PORT=$port && set NODE_PATH=${sdkDir.absolutePath}\\node_modules && npx --prefix \"${sdkDir.absolutePath}\" tsx \"$localServerPath\"",
                )
        } else {
            ProcessBuilder()
                .command(
                    "bash",
                    "-c",
                    "MCP_PORT=$port NODE_PATH='${sdkDir.absolutePath}/node_modules' npx --prefix '${sdkDir.absolutePath}' tsx \"$localServerPath\"",
                )
        }

        processBuilder.environment()["TYPESCRIPT_SDK_DIR"] = sdkDir.absolutePath

        val process = processBuilder
            .directory(tsClientDir)
            .redirectErrorStream(true)
            .start()

        createProcessOutputReader(process).start()

        if (!waitForPort(port = port, timeoutSeconds = 20)) {
            throw IllegalStateException("TypeScript server did not become ready on localhost:$port within timeout")
        }
        return process
    }

    protected fun stopProcess(process: Process, waitSeconds: Long = 3, name: String = "TypeScript server") {
        process.destroy()
        if (waitForProcessTermination(process, waitSeconds)) {
            println("$name stopped gracefully")
        } else {
            println("$name did not stop gracefully, forced termination")
        }
    }

    // ===== SSE client helpers =====
    protected suspend fun newClient(serverUrl: String): Client =
        HttpClient(CIO) { install(SSE) }.mcpStreamableHttp(serverUrl)

    protected suspend fun <T> withClient(serverUrl: String, block: suspend (Client) -> T): T {
        val client = newClient(serverUrl)
        return try {
            withTimeout(20.seconds) { block(client) }
        } finally {
            try {
                withTimeout(3.seconds) { client.close() }
            } catch (_: Exception) {
                // ignore errors
            }
        }
    }

    // ===== STDIO client + server helpers =====
    protected fun startTypeScriptServerStdio(): Process {
        if (!sdkDir.exists() || !sdkDir.isDirectory) {
            throw IllegalStateException(
                "TypeScript SDK directory does not exist or is not accessible: ${sdkDir.absolutePath}",
            )
        }
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val localServerPath = File(tsClientDir, "simpleStdio.ts").absolutePath
        val processBuilder = if (isWindows) {
            ProcessBuilder()
                .command(
                    "cmd.exe",
                    "/c",
                    "set NODE_PATH=${sdkDir.absolutePath}\\node_modules && npx --prefix \"${sdkDir.absolutePath}\" tsx \"$localServerPath\"",
                )
        } else {
            ProcessBuilder()
                .command(
                    "bash",
                    "-c",
                    "NODE_PATH='${sdkDir.absolutePath}/node_modules' npx --prefix '${sdkDir.absolutePath}' tsx \"$localServerPath\"",
                )
        }
        processBuilder.environment()["TYPESCRIPT_SDK_DIR"] = sdkDir.absolutePath
        val process = processBuilder
            .directory(tsClientDir)
            .redirectErrorStream(false)
            .start()
        // For stdio transports, do NOT read from stdout (it's used for protocol). Read stderr for logs only.
        createProcessErrorReader(process, prefix = "TS-SERVER-STDIO").start()
        // Give the process a moment to start
        await.atMost(2, TimeUnit.SECONDS)
            .pollDelay(200, TimeUnit.MILLISECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until { process.isAlive }
        return process
    }

    protected suspend fun newClientStdio(process: Process): Client {
        val input: Source = process.inputStream.asSource().buffered()
        val output: Sink = process.outputStream.asSink().buffered()
        val transport = StdioClientTransport(input = input, output = output)
        val client = Client(Implementation("test", "1.0"))
        client.connect(transport)
        return client
    }

    protected suspend fun <T> withClientStdio(block: suspend (Client, Process) -> T): T {
        val proc = startTypeScriptServerStdio()
        val client = newClientStdio(proc)
        return try {
            withTimeout(20.seconds) { block(client, proc) }
        } finally {
            try {
                withTimeout(3.seconds) { client.close() }
            } catch (_: Exception) {
            }
            try {
                stopProcess(proc, name = "TypeScript stdio server")
            } catch (_: Exception) {
            }
        }
    }

    // ===== Helpers to run TypeScript client over STDIO against Kotlin server over STDIO =====
    protected fun runStdioClient(vararg args: String): String {
        // Start Node stdio client (it will speak MCP over its stdout/stdin)
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val clientPath = File(tsClientDir, "myClient.ts").absolutePath

        val process = if (isWindows) {
            ProcessBuilder()
                .command(
                    "cmd.exe",
                    "/c",
                    (
                        "set TYPESCRIPT_SDK_DIR=${sdkDir.absolutePath} && " +
                            "set NODE_PATH=${sdkDir.absolutePath}\\node_modules && " +
                            "npx --prefix \"${sdkDir.absolutePath}\" tsx \"$clientPath\" " +
                            args.joinToString(" ")
                        ),
                )
                .directory(tsClientDir)
                .redirectErrorStream(false)
                .start()
        } else {
            ProcessBuilder()
                .command(
                    "bash",
                    "-c",
                    (
                        "TYPESCRIPT_SDK_DIR='${sdkDir.absolutePath}' " +
                            "NODE_PATH='${sdkDir.absolutePath}/node_modules' " +
                            "npx --prefix '${sdkDir.absolutePath}' tsx \"$clientPath\" " +
                            args.joinToString(" ")
                        ),
                )
                .directory(tsClientDir)
                .redirectErrorStream(false)
                .start()
        }

        // Create Kotlin server and attach stdio transport to the process streams
        val server: Server = KotlinServerForTsClient().createMcpServer()
        val transport = StdioServerTransport(
            inputStream = process.inputStream.asSource().buffered(),
            outputStream = process.outputStream.asSink().buffered(),
        )

        // Connect server in a background thread to avoid blocking
        val serverThread = Thread {
            try {
                kotlinx.coroutines.runBlocking { server.connect(transport) }
            } catch (e: Exception) {
                println("[STDIO-SERVER] Error connecting: ${e.message}")
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        // Read ONLY stderr from client for human-readable output
        val output = StringBuilder()
        val errReader = Thread {
            try {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        println("[TS-CLIENT-STDIO][err] $line")
                        output.append(line).append('\n')
                    }
                }
            } catch (e: Exception) {
                println("Warning: Error reading stdio client stderr: ${e.message}")
            }
        }
        errReader.isDaemon = true
        errReader.start()

        // Wait up to 25s for client to exit
        val finished = process.waitFor(25, TimeUnit.SECONDS)
        if (!finished) {
            println("Stdio client did not finish in time; destroying")
            process.destroyForcibly()
        }

        try {
            kotlinx.coroutines.runBlocking { transport.close() }
        } catch (_: Exception) {
        }

        return output.toString()
    }
}
