package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import io.modelcontextprotocol.kotlin.test.utils.TypeScriptRunner
import io.modelcontextprotocol.kotlin.test.utils.killProcessOnPort
import io.modelcontextprotocol.kotlin.test.utils.stopProcess
import io.modelcontextprotocol.kotlin.test.utils.waitForPort
import org.awaitility.kotlin.await
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Factory class for starting and managing TypeScript MCP servers for integration tests.
 *
 * This class provides methods to:
 * - Start TypeScript servers using SSE or STDIO transports via npx tsx
 * - Install npm dependencies in the TypeScript test directory
 * - Manage server lifecycle (start/stop)
 * - Handle port management and process cleanup
 *
 * The servers are started using npx tsx to execute TypeScript files directly:
 * - `npx tsx server/stdio-server.ts` for STDIO transport
 * - `npx tsx server/sse-server.ts` for SSE transport
 *
 * Usage:
 * ```kotlin
 * val typescriptDir = File("kotlin-sdk-test/src/jvmTest/typescript")
 * val server = TypeScriptServer(typescriptDir)
 *
 * // Start SSE server
 * val port = TypeScriptServer.findFreePort()
 * val process = server.startSse(port)
 *
 * // Stop server
 * server.stop()
 * ```
 */
class TypeScriptServer(private val typescriptDir: File) {
    private var process: Process? = null

    /**
     * Starts a TypeScript MCP server using SSE transport.
     *
     * @param port The port number to bind the SSE server to
     * @return The started Process
     * @throws IllegalStateException if the server fails to start within timeout
     */
    fun startSse(port: Int): Process {
        killProcessOnPort(port)
        val serverPath = File(typescriptDir, "server/sse-server.ts").absolutePath
        val proc = TypeScriptRunner.run(
            typescriptDir = typescriptDir,
            scriptPath = serverPath,
            env = mapOf("MCP_PORT" to port.toString()),
            redirectErrorStream = true,
            logPrefix = "TS-SERVER-SSE",
        )
        process = proc

        check(waitForPort(port = port, timeoutSeconds = 60)) {
            proc.destroyForcibly()
            "TypeScript SSE server did not become ready on localhost:$port within timeout"
        }
        return proc
    }

    /**
     * Starts a TypeScript MCP server using STDIO transport.
     *
     * @return The started Process
     */
    fun startStdio(): Process {
        val serverPath = File(typescriptDir, "server/stdio-server.ts").absolutePath
        val proc = TypeScriptRunner.run(
            typescriptDir = typescriptDir,
            scriptPath = serverPath,
            redirectErrorStream = false,
            logPrefix = "TS-SERVER-STDIO",
        )
        process = proc

        await.atMost(5, TimeUnit.SECONDS)
            .pollDelay(200, TimeUnit.MILLISECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until { proc.isAlive }

        return proc
    }

    /**
     * Stops the currently running server process.
     *
     * Attempts graceful shutdown first, then forces termination if necessary.
     */
    fun stop() {
        process?.let {
            stopProcess(it)
        }
        process = null
    }

    companion object {
        /**
         * Installs npm dependencies in the specified TypeScript directory.
         *
         * @param typescriptDir The directory containing package.json
         * @throws RuntimeException if npm install fails
         */
        @JvmStatic
        fun installDependencies(typescriptDir: File) {
            TypeScriptRunner.installDependencies(typescriptDir)
        }

        /**
         * Finds an available port by opening and closing a ServerSocket.
         *
         * @return An available port number
         */
        @JvmStatic
        fun findFreePort(): Int = io.modelcontextprotocol.kotlin.test.utils.findFreePort()
    }
}
