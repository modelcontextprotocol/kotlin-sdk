package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class KotlinClientTypeScriptServerEdgeCasesTest : TypeScriptTestBase() {

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
    fun testNonExistentTool() {
        runBlocking {
            withContext(Dispatchers.IO) {
                client = HttpClient(CIO) {
                    install(SSE)
                }.mcpStreamableHttp(serverUrl)

                val nonExistentToolName = "non-existent-tool"
                val arguments = mapOf("name" to "TestUser")

                val exception = assertThrows<Exception> {
                    client.callTool(nonExistentToolName, arguments)
                }

                val errorMessage = exception.message ?: ""
                assertTrue(
                    errorMessage.contains("not found") ||
                        errorMessage.contains("unknown") ||
                        errorMessage.contains("error"),
                    "Exception should indicate tool not found: $errorMessage",
                )
            }
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testSpecialCharactersInArguments() {
        runBlocking {
            withContext(Dispatchers.IO) {
                client = HttpClient(CIO) {
                    install(SSE)
                }.mcpStreamableHttp(serverUrl)

                val specialChars = "!@#$%^&*()_+{}[]|\\:;\"'<>,.?/"
                val arguments = mapOf("name" to specialChars)

                val result = client.callTool("greet", arguments)
                assertNotNull(result, "Tool call result should not be null")

                val callResult = result as CallToolResult
                val textContent = callResult.content.firstOrNull { it is TextContent } as? TextContent
                assertNotNull(textContent, "Text content should be present in the result")

                val text = textContent.text ?: ""
                assertTrue(
                    text.contains(specialChars),
                    "Tool response should contain the special characters",
                )
            }
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testLargePayload() {
        runBlocking {
            withContext(Dispatchers.IO) {
                client = HttpClient(CIO) {
                    install(SSE)
                }.mcpStreamableHttp(serverUrl)

                val largeName = "A".repeat(10 * 1024)
                val arguments = mapOf("name" to largeName)

                val result = client.callTool("greet", arguments)
                assertNotNull(result, "Tool call result should not be null")

                val callResult = result as CallToolResult
                val textContent = callResult.content.firstOrNull { it is TextContent } as? TextContent
                assertNotNull(textContent, "Text content should be present in the result")

                val text = textContent.text ?: ""
                assertTrue(
                    text.contains("Hello,") && text.contains("A"),
                    "Tool response should contain the greeting with the large name",
                )
            }
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun testConcurrentRequests() {
        runBlocking {
            withContext(Dispatchers.IO) {
                client = HttpClient(CIO) {
                    install(SSE)
                }.mcpStreamableHttp(serverUrl)

                val concurrentCount = 5
                val results = mutableListOf<Deferred<String>>()

                for (i in 1..concurrentCount) {
                    val deferred = async {
                        val name = "ConcurrentClient$i"
                        val arguments = mapOf("name" to name)

                        val result = client.callTool("greet", arguments)
                        assertNotNull(result, "Tool call result should not be null for client $i")

                        val callResult = result as CallToolResult
                        val textContent = callResult.content.firstOrNull { it is TextContent } as? TextContent
                        assertNotNull(textContent, "Text content should be present for client $i")

                        textContent.text ?: ""
                    }
                    results.add(deferred)
                }

                val responses = results.awaitAll()

                for (i in 1..concurrentCount) {
                    val expectedName = "ConcurrentClient$i"
                    val matchingResponses = responses.filter { it.contains("Hello, $expectedName!") }
                    assertEquals(
                        1,
                        matchingResponses.size,
                        "Should have exactly one response for $expectedName",
                    )
                }
            }
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testInvalidArguments() {
        runBlocking {
            withContext(Dispatchers.IO) {
                client = HttpClient(CIO) {
                    install(SSE)
                }.mcpStreamableHttp(serverUrl)

                val invalidArguments = mapOf(
                    "name" to JsonObject(mapOf("nested" to JsonPrimitive("value"))),
                )

                val exception = assertThrows<IllegalStateException> {
                    client.callTool("greet", invalidArguments)
                }

                assertTrue(
                    exception.message?.contains("invalid") == true ||
                        exception.message?.contains("error") == true,
                    "Exception should indicate invalid arguments: ${exception.message}",
                )
            }
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testMultipleToolCalls() {
        runBlocking {
            withContext(Dispatchers.IO) {
                client = HttpClient(CIO) {
                    install(SSE)
                }.mcpStreamableHttp(serverUrl)

                repeat(10) { i ->
                    val name = "SequentialClient$i"
                    val arguments = mapOf("name" to name)

                    val result = client.callTool("greet", arguments)
                    assertNotNull(result, "Tool call result should not be null for call $i")

                    val callResult = result as CallToolResult
                    val textContent = callResult.content.firstOrNull { it is TextContent } as? TextContent
                    assertNotNull(textContent, "Text content should be present for call $i")

                    assertEquals(
                        "Hello, $name!",
                        textContent.text,
                        "Tool response should contain the greeting with the provided name",
                    )
                }
            }
        }
    }
}
