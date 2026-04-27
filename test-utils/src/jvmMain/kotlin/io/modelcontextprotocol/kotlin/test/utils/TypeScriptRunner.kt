package io.modelcontextprotocol.kotlin.test.utils

import org.junit.jupiter.api.fail
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
        log: Boolean = true,
    ): Process {
        val command = mutableListOf("npx", "tsx", scriptPath)
        command.addAll(arguments)
        val pb = ProcessBuilder(command)
        pb.directory(typescriptDir)
        env.forEach { (k, v) -> pb.environment()[k] = v }
        pb.redirectErrorStream(redirectErrorStream)

        val proc = pb.start()

        if (log) {
            if (redirectErrorStream) {
                createProcessOutputReader(proc, logPrefix).start()
            } else {
                createProcessErrorReader(proc, logPrefix).start()
            }
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
        require(typescriptDir.isDirectory()) { "Type script directory does not exist: ${typescriptDir.absolutePath}" }
        println("Installing TypeScript dependencies in ${typescriptDir.absolutePath}")

        val packageJson = File(typescriptDir, "package.json")
        val content = if (packageJson.exists()) packageJson.readText() else ""
        val hasCatalog = content.contains("catalog:")
        val hasWorkspace = content.contains("workspace:")
        val hasPnpmWorkspace = File(typescriptDir, "pnpm-workspace.yaml").exists()

        if (hasCatalog || hasWorkspace || hasPnpmWorkspace) {
            println("Detected pnpm-specific protocols or workspace. Attempting pnpm install...")
            if (tryCommand(listOf("pnpm", "install"), typescriptDir) ||
                tryCommand(listOf("npx", "pnpm", "install"), typescriptDir)
            ) {
                patchPackageJsonRecursively(typescriptDir, patchProtocols = false)
                return
            }
        }

        // Try standard npm install
        if (tryCommand(listOf("npm", "install"), typescriptDir)) {
            patchPackageJsonRecursively(typescriptDir, patchProtocols = false)
            return
        }

        // If npm fails and we have catalog/workspace, try patching and npm again
        println("npm install failed. Patching package.json and trying again...")
        patchPackageJsonRecursively(typescriptDir, patchProtocols = true)
        if (tryCommand(listOf("npm", "install"), typescriptDir)) return

        fail("Failed to install TypeScript dependencies in ${typescriptDir.absolutePath}")
    }

    private fun tryCommand(command: List<String>, workingDir: File): Boolean {
        val fullCommand = if (isWindows) {
            listOf("cmd.exe", "/c") + command
        } else {
            command
        }
        println("Executing: ${fullCommand.joinToString(" ")}")
        return try {
            val pb = ProcessBuilder(fullCommand)
            pb.directory(workingDir)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            proc.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { println("[TS-INSTALL] $it") }
            }
            proc.waitFor() == 0
        } catch (e: Exception) {
            println("Command failed: ${e.message}")
            false
        }
    }

    private fun patchPackageJsonRecursively(dir: File, patchProtocols: Boolean) {
        dir.walkTopDown()
            .onEnter { it.name != "node_modules" }
            .filter { it.name == "package.json" }
            .forEach { file ->
                var content = file.readText()
                var modified = false

                if (patchProtocols) {
                    val newContent = content
                        .replace(Regex(""""catalog:[^"]*""""), "\"*\"")
                        .replace(Regex(""""workspace:[^"]*""""), "\"*\"")
                        .replace(Regex(""""link:[^"]*""""), "\"*\"")
                    if (newContent != content) {
                        content = newContent
                        modified = true
                    }
                }

                // Add entry point if missing and src/index.ts exists to help tsx resolution
                val srcIndex = File(file.parentFile, "src/index.ts")
                if (srcIndex.exists() && !content.contains("\"exports\"") && !content.contains("\"main\"")) {
                    println("Adding exports to ${file.absolutePath}")
                    content = content.replace(Regex("""^\{"""), "{\n  \"exports\": { \".\": \"./src/index.ts\" },")
                    modified = true
                }

                if (modified) {
                    file.writeText(content)
                }
            }
    }
}
