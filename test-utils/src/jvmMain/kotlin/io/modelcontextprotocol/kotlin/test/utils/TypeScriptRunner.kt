package io.modelcontextprotocol.kotlin.test.utils

import java.io.File

/**
 * Runs TypeScript files with the project's local `tsx`.
 */
public object TypeScriptRunner {

    /**
     * Starts [script] (relative to [typescriptDir]) with `node_modules/.bin/tsx`,
     * running `npm install` first if `node_modules` is missing.
     *
     * @return the started process; the caller owns its streams and must stop it
     */
    public fun start(typescriptDir: File, script: String, vararg arguments: String): Process {
        installDependenciesIfMissing(typescriptDir)
        val tsx = File(typescriptDir, if (isWindows) "node_modules/.bin/tsx.cmd" else "node_modules/.bin/tsx")
        return ProcessBuilder(listOf(tsx.absolutePath, script) + arguments)
            .directory(typescriptDir)
            .start()
    }

    @Synchronized
    private fun installDependenciesIfMissing(typescriptDir: File) {
        if (File(typescriptDir, "node_modules").exists()) return
        val npmInstall = if (isWindows) listOf("cmd.exe", "/c", "npm", "install") else listOf("npm", "install")
        val process = ProcessBuilder(npmInstall)
            .directory(typescriptDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "npm install failed in ${typescriptDir.absolutePath}:\n$output" }
    }
}
