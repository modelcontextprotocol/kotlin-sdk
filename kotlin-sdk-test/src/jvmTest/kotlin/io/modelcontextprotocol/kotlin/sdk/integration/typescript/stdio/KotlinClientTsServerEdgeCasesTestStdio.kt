package io.modelcontextprotocol.kotlin.sdk.integration.typescript.stdio

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TransportKind
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TsTestBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KotlinClientTsServerEdgeCasesTestStdio : TsTestBase() {

    override val transportKind = TransportKind.STDIO

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testNonExistentToolOverStdio(): Unit = runBlocking(Dispatchers.IO) {
        withClientStdio { client: Client, _ ->
            val nonExistentToolName = "non-existent-tool"
            val arguments = mapOf("name" to "TestUser")

            val exception = assertThrows<IllegalStateException> {
                client.callTool(nonExistentToolName, arguments)
            }

            val expectedMessage =
                "JSONRPCError(code=InvalidParams, message=MCP error -32602: Tool non-existent-tool not found, data={})"
            assertEquals(
                expectedMessage,
                exception.message,
                "Unexpected error message for non-existent tool",
            )
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testSpecialCharactersInArgumentsOverStdio(): Unit = runBlocking(Dispatchers.IO) {
        withClientStdio { client: Client, _ ->
            val specialChars = "!@#$%^&*()_+{}[]|\\:;\"'<>.,?/"
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

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testLargePayloadOverStdio(): Unit = runBlocking(Dispatchers.IO) {
        withClientStdio { client: Client, _ ->
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

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun testConcurrentRequestsOverStdio(): Unit = runBlocking(Dispatchers.IO) {
        withClientStdio { client: Client, _ ->
            val concurrentCount = 5
            val responses = coroutineScope {
                val results = (1..concurrentCount).map { i ->
                    async {
                        val name = "ConcurrentClient$i"
                        val arguments = mapOf("name" to name)

                        val result = client.callTool("greet", arguments)
                        assertNotNull(result, "Tool call result should not be null for client $i")

                        val callResult = result as CallToolResult
                        val textContent = callResult.content.firstOrNull { it is TextContent } as? TextContent
                        assertNotNull(textContent, "Text content should be present for client $i")

                        textContent.text ?: ""
                    }
                }
                results.awaitAll()
            }

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

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testInvalidArgumentsOverStdio(): Unit = runBlocking(Dispatchers.IO) {
        withClientStdio { client: Client, _ ->
            val invalidArguments = mapOf(
                "name" to JsonObject(mapOf("nested" to JsonPrimitive("value"))),
            )

            val exception = assertThrows<IllegalStateException> {
                client.callTool("greet", invalidArguments)
            }

            val msg = exception.message ?: ""
            val expectedMessage = """
                        JSONRPCError(code=InvalidParams, message=MCP error -32602: Invalid arguments for tool greet: [
                          {
                            "code": "invalid_type",
                            "expected": "string",
                            "received": "object",
                            "path": [
                              "name"
                            ],
                            "message": "Expected string, received object"
                          }
                        ], data={})
            """.trimIndent()

            assertEquals(expectedMessage, msg, "Unexpected error message for invalid arguments")
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun testMultipleToolCallsOverStdio(): Unit = runBlocking(Dispatchers.IO) {
        withClientStdio { client: Client, _ ->
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
