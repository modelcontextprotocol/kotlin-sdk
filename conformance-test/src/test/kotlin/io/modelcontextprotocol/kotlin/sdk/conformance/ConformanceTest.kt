package io.modelcontextprotocol.kotlin.sdk.conformance

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.test.utils.NPX
import io.modelcontextprotocol.kotlin.test.utils.createProcessOutputReader
import io.modelcontextprotocol.kotlin.test.utils.findFreePort
import io.modelcontextprotocol.kotlin.test.utils.waitForPort
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class ConformanceTest {

    @Test
    @Timeout(300, unit = TimeUnit.SECONDS)
    fun serverConformance() {
        val port = findFreePort()
        val server = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                json(McpJson)
            }
            mcpStreamableHttp {
                createConformanceServer()
            }
        }.start(wait = false)

        try {
            val ready = waitForPort("localhost", port, 30)
            check(ready) { "Server failed to start on port $port within 30 seconds" }

            val baselineFile = File("conformance-baseline.yml")
            val command = mutableListOf(
                NPX,
                "@modelcontextprotocol/conformance",
                "server",
                "--url",
                "http://localhost:$port/mcp",
            )
            if (baselineFile.exists()) {
                command += listOf("--expected-failures", baselineFile.absolutePath)
            }

            val process = ProcessBuilder(command)
                .directory(File("."))
                .redirectErrorStream(true)
                .start()

            createProcessOutputReader(process, "CONFORMANCE-SERVER").start()
            val exitCode = process.waitFor()
            assertEquals(0, exitCode, "Server conformance tests failed (exit code: $exitCode)")
        } finally {
            server.stop(1000, 2000)
        }
    }

    @Test
    @Timeout(300, unit = TimeUnit.SECONDS)
    fun clientConformance() {
        val clientScript = File("build/install/conformance-test/bin/conformance-client")
        check(clientScript.exists()) {
            "Client script not found at ${clientScript.absolutePath}. Run 'installDist' first."
        }

        val baselineFile = File("conformance-baseline.yml")
        val command = mutableListOf(
            NPX,
            "@modelcontextprotocol/conformance",
            "client",
            "--command",
            clientScript.absolutePath,
            "--suite",
            "core",
        )
        if (baselineFile.exists()) {
            command += listOf("--expected-failures", baselineFile.absolutePath)
        }

        val process = ProcessBuilder(command)
            .directory(File("."))
            .redirectErrorStream(true)
            .start()

        createProcessOutputReader(process, "CONFORMANCE-CLIENT").start()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, "Client conformance tests failed (exit code: $exitCode)")
    }
}
