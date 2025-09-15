package io.modelcontextprotocol.kotlin.sdk.integration.typescript.sse

import io.modelcontextprotocol.kotlin.sdk.integration.typescript.AbstractTsClientKotlinServerTest
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TransportKind
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

    override fun beforeServer() {}
    override fun afterServer() {}

    override fun runClient(vararg args: String): String {
        val cmd = buildString {
            append("npx tsx myClient.ts ")
            append(serverUrl)
            if (args.isNotEmpty()) {
                append(' ')
                append(args.joinToString(" "))
            }
        }
        return executeCommand(cmd, tsClientDir)
    }
}
