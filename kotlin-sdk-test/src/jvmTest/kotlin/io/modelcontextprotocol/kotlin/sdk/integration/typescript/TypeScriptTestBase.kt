package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import org.junit.jupiter.api.BeforeAll
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

abstract class TypeScriptTestBase {

    protected val projectRoot: File get() = File(System.getProperty("user.dir"))
    protected val tsClientDir: File get() = File(projectRoot, "src/jvmTest/kotlin/io/modelcontextprotocol/kotlin/sdk/integration/utils")

    companion object {
        @JvmStatic
        private val tempRootDir: File =
            java.nio.file.Files.createTempDirectory("typescript-sdk-").toFile().apply { deleteOnExit() }

        @JvmStatic
        protected val sdkDir: File = File(tempRootDir, "typescript-sdk")

        /**
         * clone TypeScript SDK and install dependencies
         */
        @JvmStatic
        @BeforeAll
        fun setupTypeScriptSdk() {
            println("Cloning TypeScript SDK repository")
            if (!sdkDir.exists()) {
                val cloneCommand =
                    "git clone --depth 1 https://github.com/modelcontextprotocol/typescript-sdk.git ${sdkDir.absolutePath}"
                val process = ProcessBuilder()
                    .command("bash", "-c", cloneCommand)
                    .redirectErrorStream(true)
                    .start()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    throw RuntimeException("Failed to clone TypeScript SDK repository: exit code $exitCode")
                }
            }

            println("Installing TypeScript SDK dependencies")
            executeCommand("npm install", sdkDir)
        }

        @JvmStatic
        protected fun executeCommand(command: String, workingDir: File): String {
            // Prefer running TypeScript via ts-node to avoid npx network delays on CI
            val process = ProcessBuilder()
                .command("bash", "-c", "TYPESCRIPT_SDK_DIR='${sdkDir.absolutePath}' $command")
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

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Command execution failed with exit code $exitCode: $command\nOutput:\n$output")
            }

            return output.toString()
        }

        @JvmStatic
        protected fun killProcessOnPort(port: Int) {
            executeCommand("lsof -ti:$port | xargs kill -9 2>/dev/null || true", File("."))
        }

        @JvmStatic
        protected fun findFreePort(): Int {
            ServerSocket(0).use { socket ->
                return socket.localPort
            }
        }
    }

    protected fun waitForProcessTermination(process: Process, timeoutSeconds: Long): Boolean {
        if (process.isAlive && !process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
            return false
        }
        return true
    }

    protected fun createProcessOutputReader(process: Process, prefix: String): Thread {
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

    protected fun waitForPort(host: String, port: Int, timeoutSeconds: Long = 10): Boolean {
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

    protected fun executeCommandAllowingFailure(command: String, workingDir: File, timeoutSeconds: Long = 20): String {
        val process = ProcessBuilder()
            .command("bash", "-c", "TYPESCRIPT_SDK_DIR='${sdkDir.absolutePath}' $command")
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()

        val output = StringBuilder()
        process.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                println(line)
                output.append(line).append("\n")
            }
        }

        process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        return output.toString()
    }
}