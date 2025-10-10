package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.get
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.sse.KotlinServerForTsClient
import io.modelcontextprotocol.kotlin.sdk.integration.utils.Retry
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.awaitility.kotlin.await
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.PullPolicy
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

enum class TransportKind { SSE, STDIO, DEFAULT }

@Retry(times = 3)
abstract class TsTestBase {
    protected open val transportKind: TransportKind = TransportKind.DEFAULT

    companion object {
        private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

        private fun tsDockerImage() = System.getenv("TS_SDK_IMAGE")
            ?: throw IllegalStateException("TS_SDK_IMAGE environment variable is not set")

        private fun getTsFilesPath(subdir: String): String {
            val userDir = System.getProperty("user.dir")
            return "$userDir/src/jvmTest/kotlin/io/modelcontextprotocol/kotlin/sdk/integration/typescript/$subdir"
        }

        fun findFreePort() = ServerSocket(0).use { it.localPort }

        fun killProcessOnPort(port: Int) {
            val command = if (isWindows) {
                listOf(
                    "cmd.exe",
                    "/c",
                    "netstat -ano | findstr :$port | for /f \"tokens=5\" %a in ('more') do taskkill /F /PID %a 2>nul || echo No process found",
                )
            } else {
                listOf("bash", "-c", "lsof -ti:$port | xargs kill -9 2>/dev/null || true")
            }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
        }

        private fun runDockerCommand(
            image: String = tsDockerImage(),
            command: List<String>,
            interactive: Boolean = true,
            environmentVariables: Map<String, String> = emptyMap(),
            extraArgs: List<String> = emptyList(),
            allowFailure: Boolean = false,
        ): Process {
            val dockerArgs = buildList {
                add("docker")
                add("run")
                add("--rm")
                if (interactive) add("-i")
                add("-v")
                add("${getTsFilesPath("sse")}:/app/sse")
                add("-v")
                add("${getTsFilesPath("stdio")}:/app/stdio")
                addAll(extraArgs)
                environmentVariables.forEach { (key, value) ->
                    add("-e")
                    add("$key=$value")
                }
                add(image)
                addAll(command)
            }

            return ProcessBuilder(dockerArgs)
                .redirectErrorStream(false)
                .start()
                .also { if (!allowFailure) it.startErrorLogger() }
        }

        private fun Process.startErrorLogger() = Thread {
            errorStream.bufferedReader().useLines { lines ->
                lines.forEach { println("[ERR] $it") }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    // ===== Wait utilities =====
    protected fun waitForPort(host: String = "localhost", port: Int, timeoutSeconds: Long = 10) = runCatching {
        await.atMost(timeoutSeconds, TimeUnit.SECONDS)
            .pollDelay(200, TimeUnit.MILLISECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until {
                runCatching { Socket(host, port).close() }.isSuccess
            }
        true
    }.getOrDefault(false)

    protected fun waitForHttpReady(url: String, timeoutSeconds: Long = 10) = runCatching {
        await.atMost(timeoutSeconds, TimeUnit.SECONDS)
            .pollDelay(200, TimeUnit.MILLISECONDS)
            .pollInterval(150, TimeUnit.MILLISECONDS)
            .until {
                runCatching {
                    HttpClient(CIO) {
                        install(HttpTimeout) {
                            requestTimeoutMillis = 1000
                            connectTimeoutMillis = 1000
                            socketTimeoutMillis = 1000
                        }
                        followRedirects = false
                    }.use { client ->
                        runBlocking { client.get(url).status.value in 200..499 }
                    }
                }.getOrDefault(false)
            }
        true
    }.getOrDefault(false)

    // ===== TypeScript Server (SSE/HTTP) =====
    protected fun startTypeScriptServer(port: Int): ContainerProcess {
        val container = GenericContainer(tsDockerImage()).apply {
            withImagePullPolicy(PullPolicy.alwaysPull())
            withExposedPorts(port)
            mapOf(
                "MCP_HOST" to "0.0.0.0",
                "MCP_PORT" to port.toString(),
                "MCP_PATH" to "/mcp",
            ).forEach { (k, v) -> withEnv(k, v) }
            withFileSystemBind(getTsFilesPath("sse"), "/app/sse", BindMode.READ_ONLY)
            withFileSystemBind(getTsFilesPath("stdio"), "/app/stdio", BindMode.READ_ONLY)
            withCommand("npx", "--prefix", "/opt/typescript-sdk", "tsx", "/app/sse/simpleStreamableHttp.ts")
            withReuse(false)
        }

        runCatching { container.start() }.onFailure {
            runCatching { container.stop() }
            throw it
        }

        val host = container.host
        val mappedPort = container.getMappedPort(port)
        require(waitForPort(host, mappedPort, 60)) {
            runCatching { container.stop() }
            "TypeScript server did not become ready on $host:$mappedPort"
        }
        require(waitForHttpReady("http://$host:$mappedPort/mcp")) {
            runCatching { container.stop() }
            "TypeScript server HTTP endpoint /mcp not ready on $host:$mappedPort"
        }

        println("TypeScript server started on $host:$mappedPort (container port: $port)")
        Thread.sleep(300)

        return ContainerProcess(container, mappedPort)
    }

    protected class ContainerProcess(private val container: GenericContainer<*>, val mappedPort: Int) : Process() {
        override fun destroy() = runCatching { container.stop() }.let { }
        override fun destroyForcibly() = apply { destroy() }
        override fun exitValue() = if (container.isRunning) throw IllegalThreadStateException() else 0
        override fun isAlive() = container.isRunning
        override fun waitFor(): Int {
            while (container.isRunning) {
                runCatching { Thread.sleep(50) }
            }
            return 0
        }
        override fun getInputStream() = ByteArrayInputStream(ByteArray(0))
        override fun getErrorStream() = ByteArrayInputStream(ByteArray(0))
        override fun getOutputStream() = object : OutputStream() {
            override fun write(b: Int) {}
        }
    }

    protected fun stopProcess(process: Process, waitSeconds: Long = 3, name: String = "Process") {
        process.destroy()
        val stopped = process.waitFor(waitSeconds, TimeUnit.SECONDS)
        if (!stopped) process.destroyForcibly()
        println("$name stopped ${if (stopped) "gracefully" else "forcibly"}")
    }

    // ===== SSE Client =====
    protected suspend fun newClient(serverUrl: String) = HttpClient(CIO) { install(SSE) }.mcpStreamableHttp(serverUrl)

    protected suspend fun <T> withClient(serverUrl: String, block: suspend (Client) -> T): T {
        val client = newClient(serverUrl)
        return try {
            withTimeout(20.seconds) { block(client) }
        } finally {
            runCatching { withTimeout(3.seconds) { client.close() } }
        }
    }

    // ===== STDIO Client =====
    protected fun startTypeScriptServerStdio(): Process {
        val process = runDockerCommand(
            command = listOf("npx", "--prefix", "/opt/typescript-sdk", "tsx", "/app/stdio/simpleStdio.ts"),
        )

        await.atMost(2, TimeUnit.SECONDS)
            .pollDelay(200, TimeUnit.MILLISECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until { process.isAlive }

        return process
    }

    protected suspend fun newClientStdio(process: Process): Client {
        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered(),
        )
        return Client(Implementation("test", "1.0")).apply { connect(transport) }
    }

    protected suspend fun <T> withClientStdio(block: suspend (Client, Process) -> T): T {
        val proc = startTypeScriptServerStdio()
        val client = newClientStdio(proc)
        return try {
            withTimeout(5.seconds) { block(client, proc) }
        } finally {
            runCatching { withTimeout(3.seconds) { client.close() } }
            runCatching { stopProcess(proc, name = "TypeScript stdio server") }
        }
    }

    // ===== HTTP Client (TypeScript → Kotlin Server) =====
    protected fun runHttpClient(serverUrl: String, vararg args: String) = runTsClient(serverUrl, args.toList())

    protected fun runHttpClientAllowingFailure(serverUrl: String, vararg args: String) =
        runTsClient(serverUrl, args.toList(), allowFailure = true)

    private fun runTsClient(serverUrl: String, args: List<String>, allowFailure: Boolean = false): String {
        val containerUrl = serverUrl.replace("localhost", "host.docker.internal")
        val command = buildList {
            add("npx")
            add("--prefix")
            add("/opt/typescript-sdk")
            add("tsx")
            add("/app/sse/myClient.ts")
            add(containerUrl)
            addAll(args)
        }

        val process = runDockerCommand(
            command = command,
            environmentVariables = mapOf("NODE_PATH" to "/opt/typescript-sdk/node_modules"),
            extraArgs = listOf("--add-host=host.docker.internal:host-gateway"),
            allowFailure = allowFailure,
        )

        // Capture both stdout and stderr from the TS client to ensure error messages are returned to tests
        val output = StringBuilder()

        fun captureStream(stream: java.io.InputStream, prefix: String = ""): Thread = Thread {
            stream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val msg = if (prefix.isEmpty()) line else "$prefix$line"
                    println(msg)
                    output.append(line).append('\n')
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        val stdoutThread = captureStream(process.inputStream)
        val stderrThread = captureStream(process.errorStream, "[TS-CLIENT][err] ")

        val finished = if (allowFailure) {
            process.waitFor(25, TimeUnit.SECONDS)
        } else {
            process.waitFor()
            true
        }

        if (!finished) {
            process.destroyForcibly()
        }

        stdoutThread.join(1000)
        stderrThread.join(1000)

        return output.toString()
    }

    // ===== STDIO Client (TypeScript → Kotlin Server) =====
    protected fun runStdioClient(vararg args: String): String {
        val process = runDockerCommand(
            command = listOf("npx", "--prefix", "/opt/typescript-sdk", "tsx", "/app/stdio/myClient.ts") + args,
            allowFailure = true,
        )

        val server = KotlinServerForTsClient().createMcpServer()
        val transport = StdioServerTransport(
            inputStream = process.inputStream.asSource().buffered(),
            outputStream = process.outputStream.asSink().buffered(),
        )

        Thread {
            runCatching { runBlocking { server.connect(transport) } }
                .onFailure { println("[STDIO-SERVER] Error: ${it.message}") }
        }.apply {
            isDaemon = true
            start()
        }

        val output = StringBuilder()
        Thread {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach {
                    println("[TS-CLIENT-STDIO][err] $it")
                    output.append(it).append('\n')
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        if (!process.waitFor(25, TimeUnit.SECONDS)) {
            println("Stdio client timeout; destroying")
            process.destroyForcibly()
        }

        runCatching { runBlocking { transport.close() } }
        return output.toString()
    }
}
