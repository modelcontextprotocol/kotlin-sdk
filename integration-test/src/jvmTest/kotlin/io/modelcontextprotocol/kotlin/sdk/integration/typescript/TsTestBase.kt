package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.sse.KotlinServerForTsClient
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.test.utils.Retry
import io.modelcontextprotocol.kotlin.test.utils.TypeScriptRunner
import io.modelcontextprotocol.kotlin.test.utils.isWindows
import kotlinx.coroutines.withTimeout
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.BeforeAll
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.seconds

enum class TransportKind { SSE, STDIO }

@Retry(times = 3)
abstract class TsTestBase {

    protected abstract val transportKind: TransportKind

    companion object {
        @JvmStatic
        protected val projectRoot: File get() = File(System.getProperty("user.dir"))

        @JvmStatic
        protected val tsClientDir: File
            get() = File(projectRoot, "src/jvmTest/typescript")

        @JvmStatic
        protected val tsSourceDir: File
            get() = File(
                projectRoot,
                "src/jvmTest/kotlin/io/modelcontextprotocol/kotlin/sdk/integration/typescript",
            )

        @JvmStatic
        private var sharedSseServer: Process? = null

        @JvmStatic
        private var sharedSsePort: Int = 0

        @JvmStatic
        @Synchronized
        protected fun getSharedSseUrl(): String {
            if (sharedSseServer == null || !sharedSseServer!!.isAlive) {
                sharedSsePort = io.modelcontextprotocol.kotlin.test.utils.findFreePort()
                val server = TypeScriptServer(tsClientDir)
                sharedSseServer = server.startSse(sharedSsePort)
                println("Shared TypeScript SSE server started on port $sharedSsePort")

                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        sharedSseServer?.let {
                            println("Stopping shared TypeScript SSE server")
                            io.modelcontextprotocol.kotlin.test.utils.stopProcess(it)
                        }
                    },
                )
            }
            return "http://localhost:$sharedSsePort/mcp"
        }

        @JvmStatic
        private val tempRootDir: File = createTempDirectory("typescript-sdk-").toFile().apply { deleteOnExit() }

        @JvmStatic
        protected val sdkDir: File = File(tempRootDir, "typescript-sdk")

        @JvmStatic
        @BeforeAll
        @Deprecated("Use setupTypeScriptSdkWithDependencies instead")
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
                require(exitCode == 0) {
                    "Failed to clone TypeScript SDK repository: exit code $exitCode"
                }
            }

            println("Installing TypeScript SDK dependencies")
            val npmInstallResult = executeCommand("npm install", sdkDir, allowFailure = true, timeoutSeconds = null)
            if (npmInstallResult.contains("EUNSUPPORTEDPROTOCOL") || npmInstallResult.contains("catalog:")) {
                println("npm install failed due to catalog protocol. Trying pnpm...")
                try {
                    // Try to use npx pnpm to ensure it's available
                    executeCommand("npx pnpm install", sdkDir, allowFailure = false, timeoutSeconds = null)
                } catch (e: Exception) {
                    println("pnpm install failed or not found: ${e.message}")
                    println("Attempting to patch package.json to remove catalog: protocol and use npm")
                    patchPackageJson(sdkDir)
                    // Also patch other package.json files in the workspace if they exist
                    sdkDir.walk().filter { it.name == "package.json" }.forEach { patchPackageJson(it.parentFile) }
                    executeCommand("npm install", sdkDir, allowFailure = false, timeoutSeconds = null)
                }
            }
        }

        @Deprecated("never patch package.json")
        private fun patchPackageJson(dir: File) {
            val packageJson = File(dir, "package.json")
            if (!packageJson.exists()) return
            var content = packageJson.readText()
            // Replace "catalog:something" with "*" or a reasonable default if we knew it.
            // Since we don't know the exact versions in the catalog, "*" is a risky but possible fallback for tests.
            // However, a better approach is to use pnpm if available.
            content = content.replace(Regex(""""catalog:[^"]*""""), "\"*\"")
            // Also replace workspace: protocol if we are using npm fallback
            content = content.replace(Regex(""""workspace:[^"]*""""), "\"*\"")
            // And any other unsupported protocols that might be in the package.json
            content = content.replace(Regex(""""link:[^"]*""""), "\"*\"")
            packageJson.writeText(content)
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
                require(allowFailure || exitCode == 0) {
                    "Command execution failed with exit code $exitCode: $command\n" +
                        "Working dir: ${workingDir.absolutePath}\nOutput:\n$output"
                }
            } else {
                process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            }

            return output.toString()
        }
    }

    private fun waitForProcessTermination(process: Process, timeoutSeconds: Long): Boolean {
        io.modelcontextprotocol.kotlin.test.utils.stopProcess(process)
        return true
    }

    protected fun waitForPort(host: String = "localhost", port: Int, timeoutSeconds: Long = 10): Boolean =
        io.modelcontextprotocol.kotlin.test.utils.waitForPort(host, port, timeoutSeconds)

    protected fun executeCommandAllowingFailure(command: String, workingDir: File, timeoutSeconds: Long = 20): String =
        executeCommand(command, workingDir, allowFailure = true, timeoutSeconds = timeoutSeconds)

    protected fun startTypeScriptServer(port: Int): Process {
        val server = TypeScriptServer(tsClientDir)
        return server.startSse(port)
    }

    protected fun stopProcess(process: Process, waitSeconds: Long = 3, name: String = "TypeScript server") {
        io.modelcontextprotocol.kotlin.test.utils.stopProcess(process)
        println("$name stopped")
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
        val server = TypeScriptServer(tsClientDir)
        return server.startStdio()
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
        val subDirName = when (transportKind) {
            TransportKind.STDIO -> "stdio"
            TransportKind.SSE -> "sse"
        }
        val clientPath = File(tsSourceDir, "$subDirName/myClient.ts").absolutePath

        val process = TypeScriptRunner.run(
            typescriptDir = tsClientDir,
            scriptPath = clientPath,
            arguments = args.toList(),
            env = mapOf("TYPESCRIPT_SDK_DIR" to sdkDir.absolutePath),
            redirectErrorStream = false,
            logPrefix = "TS-CLIENT-STDIO",
        )

        // Create Kotlin server and attach stdio transport to the process streams
        val server: Server = KotlinServerForTsClient().createMcpServer()
        val transport = StdioServerTransport(
            inputStream = process.inputStream.asSource().buffered(),
            outputStream = process.outputStream.asSink().buffered(),
        )

        // Connect server in a background thread to avoid blocking
        val serverThread = Thread {
            try {
                kotlinx.coroutines.runBlocking { server.createSession(transport) }
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
