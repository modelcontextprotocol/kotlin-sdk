package io.modelcontextprotocol.kotlin.sdk.integration.typescript.sse

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.AbstractKotlinClientTsServerTest
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TransportKind
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.time.Duration.Companion.seconds

class KotlinClientTsServerTestSse : AbstractKotlinClientTsServerTest() {

    override val transportKind = TransportKind.SSE

    private var port: Int = 0
    private val host = "localhost"
    private lateinit var serverUrl: String
    private lateinit var tsServerProcess: ContainerProcess

    @BeforeEach
    fun setUpSse() {
        port = findFreePort()
        tsServerProcess = startTypeScriptServer(port)
        serverUrl = "http://$host:${tsServerProcess.mappedPort}/mcp"
        println("TypeScript server started on port ${tsServerProcess.mappedPort}")
    }

    @AfterEach
    fun tearDownSse() {
        if (::tsServerProcess.isInitialized) {
            try {
                println("Stopping TypeScript server")
                stopProcess(tsServerProcess)
            } catch (e: Exception) {
                println("Warning: Error during TypeScript server stop: ${e.message}")
            }
        }
    }

    override suspend fun <T> useClient(block: suspend (Client) -> T): T = withClient(serverUrl) { client ->
        try {
            withTimeout(20.seconds) { block(client) }
        } finally {
            try {
                withTimeout(3.seconds) { client.close() }
            } catch (_: Exception) {}
        }
    }
}
