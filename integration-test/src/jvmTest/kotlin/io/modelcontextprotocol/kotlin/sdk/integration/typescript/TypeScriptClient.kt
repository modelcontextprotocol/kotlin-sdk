package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import io.modelcontextprotocol.kotlin.test.utils.TypeScriptRunner
import io.modelcontextprotocol.kotlin.test.utils.stopProcess
import java.io.File

/**
 * Component for starting and managing TypeScript MCP clients for integration tests.
 *
 * This class provides methods to start TypeScript clients using SSE or STDIO transports.
 * The clients are started using npx tsx to execute TypeScript files directly.
 */
class TypeScriptClient(private val typescriptDir: File) : AutoCloseable {
    private var process: Process? = null

    /**
     * Starts a TypeScript MCP client using SSE transport.
     *
     * @param arguments Arguments to pass to the client (typically server URL, tool name, and tool args)
     * @param log Whether to automatically log process output/error
     * @return The started Process
     */
    fun startSse(arguments: List<String>, log: Boolean = true): Process {
        val scriptPath = File(typescriptDir, "client/sse-client.ts").absolutePath
        val proc = TypeScriptRunner.run(
            typescriptDir = typescriptDir,
            scriptPath = scriptPath,
            arguments = arguments,
            env = emptyMap(),
            redirectErrorStream = false,
            logPrefix = "TS-CLIENT-SSE",
            log = log,
        )
        process = proc
        return proc
    }

    /**
     * Starts a TypeScript MCP client using STDIO transport.
     *
     * @param arguments Arguments to pass to the client (typically tool name and tool args)
     * @param log Whether to automatically log process output/error
     * @return The started Process
     */
    fun startStdio(arguments: List<String>, log: Boolean = true): Process {
        val scriptPath = File(typescriptDir, "client/stdio-client.ts").absolutePath
        val proc = TypeScriptRunner.run(
            typescriptDir = typescriptDir,
            scriptPath = scriptPath,
            arguments = arguments,
            env = emptyMap(),
            redirectErrorStream = false,
            logPrefix = "TS-CLIENT-STDIO",
            log = log,
        )
        process = proc
        return proc
    }

    /**
     * Stops the currently running client process.
     */
    override fun close() {
        process?.let {
            stopProcess(it)
        }
        process = null
    }
}
