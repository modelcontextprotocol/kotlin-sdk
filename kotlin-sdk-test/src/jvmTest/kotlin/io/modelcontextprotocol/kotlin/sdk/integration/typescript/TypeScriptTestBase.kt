package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.integration.utils.Retry
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeAll
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@Retry(times = 3)
abstract class TypeScriptTestBase {

    protected val projectRoot: File get() = File(System.getProperty("user.dir"))
    protected val tsClientDir: File
        get() = File(
            projectRoot,
            "src/jvmTest/kotlin/io/modelcontextprotocol/kotlin/sdk/integration/typescript",
        )

    companion object {
        @JvmStatic
        private val tempRootDir: File = Files.createTempDirectory("typescript-sdk-").toFile().apply { deleteOnExit() }

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
                "netstat -ano | findstr :$port | for /f \"tokens=5\" %a in ('more') do taskkill /F /PID %a 2>nul || echo No process found"
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
                        "Command execution failed with exit code $exitCode: $command\nWorking dir: ${workingDir.absolutePath}\nOutput:\n$output",
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

    protected fun waitForPort(host: String = "localhost", port: Int, timeoutSeconds: Long = 10): Boolean {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket(host, port).use { return true }
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
        return false
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
}
