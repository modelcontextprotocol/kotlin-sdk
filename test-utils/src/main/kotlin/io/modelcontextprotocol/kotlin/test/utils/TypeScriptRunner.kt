package io.modelcontextprotocol.kotlin.test.utils

import java.io.File

/**
 * Component for running arbitrary TypeScript files using npx tsx.
 */
public object TypeScriptRunner {

    /**
     * Runs a TypeScript file using npx tsx.
     *
     * @param typescriptDir The working directory for the process
     * @param scriptPath The path to the TypeScript file relative to [typescriptDir] or absolute
     * @param env Environment variables for the process
     * @param redirectErrorStream Whether to redirect error stream to output stream
     * @param logPrefix Prefix for log lines
     * @return The started Process
     */
    public fun run(
        typescriptDir: File,
        scriptPath: String,
        arguments: List<String> = emptyList(),
        env: Map<String, String> = emptyMap(),
        redirectErrorStream: Boolean = false,
        logPrefix: String = "TS-RUNNER",
    ): Process {
        val command = mutableListOf("npx", "tsx", scriptPath)
        command.addAll(arguments)
        val pb = ProcessBuilder(command)
        pb.directory(typescriptDir)
        env.forEach { (k, v) -> pb.environment()[k] = v }
        pb.redirectErrorStream(redirectErrorStream)

        val proc = pb.start()

        if (redirectErrorStream) {
            createProcessOutputReader(proc, logPrefix).start()
        } else {
            createProcessErrorReader(proc, logPrefix).start()
        }

        return proc
    }

    /**
     * Installs npm dependencies in the specified TypeScript directory.
     *
     * @param typescriptDir The directory containing package.json
     * @throws RuntimeException if npm install fails
     */
    public fun installDependencies(typescriptDir: File) {
        require(typescriptDir.isDirectory()) { "Type script directory does not exist" }
        println("Installing TypeScript dependencies in ${typescriptDir.absolutePath}")
        val pb = ProcessBuilder("npm", "install")
        pb.directory(typescriptDir)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        proc.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { println("[NPM] $it") }
        }
        val exitCode = proc.waitFor()
        require(exitCode == 0) { "npm install failed with exit code $exitCode" }
    }
}
