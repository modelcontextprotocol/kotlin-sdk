package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.sse.*
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class KotlinClientTypeScriptServerTest : TypeScriptTestBase() {

    private var port: Int = 0
    private val host = "localhost"
    private lateinit var serverUrl: String

    private lateinit var client: Client
    private lateinit var tsServerProcess: Process


    @BeforeEach
    fun setUp() {
        port = findFreePort()
        serverUrl = "http://$host:$port/mcp"
        tsServerProcess = startTypeScriptServer(port)
        println("TypeScript server started on port $port")
    }

    @AfterEach
    fun tearDown() {
        if (::client.isInitialized) {
            try {
                runBlocking {
                    withTimeout(3.seconds) {
                        client.close()
                    }
                }
            } catch (e: Exception) {
                println("Warning: Error during client close: ${e.message}")
            }
        }

        if (::tsServerProcess.isInitialized) {
            try {
                println("Stopping TypeScript server")
                stopProcess(tsServerProcess)
            } catch (e: Exception) {
                println("Warning: Error during TypeScript server stop: ${e.message}")
            }
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testKotlinClientConnectsToTypeScriptServer() {
        runBlocking {
            withContext(Dispatchers.IO) {
                client = HttpClient(CIO) {
                    install(SSE)
                }.mcpStreamableHttp(serverUrl)

                assertNotNull(client, "Client should be initialized")

                val pingResult = client.ping()
                assertNotNull(pingResult, "Ping result should not be null")

                val serverImpl = client.serverVersion
                assertNotNull(serverImpl, "Server implementation should not be null")
                println("Connected to TypeScript server: ${serverImpl.name} v${serverImpl.version}")
            }
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testListTools() {
        runBlocking {
            withContext(Dispatchers.IO) {
                client = HttpClient(CIO) {
                    install(SSE)
                }.mcpStreamableHttp(serverUrl)

                val result = client.listTools()
                assertNotNull(result, "Tools list should not be null")
                assertTrue(result.tools.isNotEmpty(), "Tools list should not be empty")

                // Verify specific utils are available
                val toolNames = result.tools.map { it.name }
                assertTrue(toolNames.contains("greet"), "Greet tool should be available")
                assertTrue(toolNames.contains("multi-greet"), "Multi-greet tool should be available")
                assertTrue(toolNames.contains("collect-user-info"), "Collect-user-info tool should be available")

                println("Available utils: ${toolNames.joinToString()}")
            }
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testToolCall() {
        runBlocking {
            withContext(Dispatchers.IO) {
                client = HttpClient(CIO) {
                    install(SSE)
                }.mcpStreamableHttp(serverUrl)

                val testName = "TestUser"
                val arguments = mapOf("name" to testName)

                val result = client.callTool("greet", arguments)
                assertNotNull(result, "Tool call result should not be null")

                val callResult = result as CallToolResult
                val textContent = callResult.content.firstOrNull { it is TextContent } as? TextContent
                assertNotNull(textContent, "Text content should be present in the result")
                assertEquals(
                    "Hello, $testName!",
                    textContent.text,
                    "Tool response should contain the greeting with the provided name"
                )
            }
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testMultipleClients() {
        runBlocking {
            withContext(Dispatchers.IO) {
                // First client connection
                val client1 = HttpClient(CIO) {
                    install(SSE)
                }.mcpStreamableHttp(serverUrl)

                val tools1 = client1.listTools()
                assertNotNull(tools1, "Tools list for first client should not be null")
                assertTrue(tools1.tools.isNotEmpty(), "Tools list for first client should not be empty")

                val client2 = HttpClient(CIO) {
                    install(SSE)
                }.mcpStreamableHttp(serverUrl)

                val tools2 = client2.listTools()
                assertNotNull(tools2, "Tools list for second client should not be null")
                assertTrue(tools2.tools.isNotEmpty(), "Tools list for second client should not be empty")

                val toolNames1 = tools1.tools.map { it.name }
                val toolNames2 = tools2.tools.map { it.name }

                assertTrue(toolNames1.contains("greet"), "Greet tool should be available to first client")
                assertTrue(toolNames1.contains("multi-greet"), "Multi-greet tool should be available to first client")
                assertTrue(toolNames2.contains("greet"), "Greet tool should be available to second client")
                assertTrue(toolNames2.contains("multi-greet"), "Multi-greet tool should be available to second client")

                client1.close()
                client2.close()
            }
        }
    }
}