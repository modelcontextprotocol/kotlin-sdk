package io.modelcontextprotocol.kotlin.sdk.integration.typescript.sse

import io.modelcontextprotocol.kotlin.sdk.integration.typescript.AbstractTsClientKotlinServerTest
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TransportKind
import io.modelcontextprotocol.kotlin.test.utils.findFreePort
import io.modelcontextprotocol.kotlin.test.utils.killProcessOnPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class TsClientKotlinServerTestSse : AbstractTsClientKotlinServerTest() {

    override val transportKind = TransportKind.SSE

    private var port: Int = 0
    private lateinit var serverUrl: String
    private var httpServer: KotlinServerForTsClient? = null

    @BeforeEach
    fun setUp() {
        port = findFreePort()
        serverUrl = "http://localhost:$port/mcp"
        killProcessOnPort(port)
        httpServer = KotlinServerForTsClient().also { it.start(port) }
        check(waitForPort(port = port)) { "Kotlin test server did not become ready on localhost:$port within timeout" }
        println("Kotlin server started on port $port")
    }

    @AfterEach
    fun tearDown() {
        try {
            httpServer?.stop()
            println("HTTP server stopped")
        } catch (e: Exception) {
            println("Error during server shutdown: ${e.message}")
        }
    }

    override fun runClient(vararg args: String): String = tsClient.use { client ->
        val process = client.startSse(listOf(serverUrl) + args.toList(), log = true)
        val output = StringBuilder()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                println("[TS-CLIENT-SSE] $line")
                output.append(line).append("\n")
            }
        }
        process.waitFor(25, java.util.concurrent.TimeUnit.SECONDS)
        output.toString()
    }
}
