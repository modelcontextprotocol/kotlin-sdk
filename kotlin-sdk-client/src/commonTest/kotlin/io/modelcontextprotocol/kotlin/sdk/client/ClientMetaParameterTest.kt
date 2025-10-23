package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for MCP Client meta parameter functionality
 *
 * Tests cover:
 * - Meta key validation according to MCP specification
 * - JSON type conversion for various data types
 * - Error handling for invalid meta keys
 * - Integration with callTool method
 */
class ClientMetaParameterTest {

    private lateinit var client: Client
    private lateinit var mockTransport: MockTransport
    private val clientInfo = Implementation("test-client", "1.0.0")

    @BeforeTest
    fun setup() = runTest {
        mockTransport = MockTransport()
        client = Client(clientInfo = clientInfo)
        mockTransport.setupInitializationResponse()
        client.connect(mockTransport)
    }

    @Test
    fun `should accept valid meta keys without throwing exception`() = runTest {
        val validMeta = buildMap {
            put("simple-key", "value1")
            put("api.example.com/version", "1.0")
            put("com.company.app/setting", "enabled")
            put("retry_count", 3)
            put("user.preference", true)
            put("valid123", "alphanumeric")
            put("multi.dot.name", "multiple-dots")
            put("under_score", "underscore")
            put("hyphen-dash", "hyphen")
            put("org.apache.kafka/consumer-config", "complex-valid-prefix")
        }

        val result = runCatching {
            client.callTool("test-tool", mapOf("arg" to "value"), validMeta)
        }

        assertTrue(result.isSuccess, "Valid meta keys should not cause exceptions")
    }

    @Test
    fun `should accept edge case valid prefixes and names`() = runTest {
        val edgeCaseValidMeta = buildMap {
            put("a/", "single-char-prefix-empty-name") // empty name is allowed
            put("a1-b2/test", "alphanumeric-hyphen-prefix")
            put("long.domain.name.here/config", "long-prefix")
            put("x/a", "minimal-valid-key")
            put("test123", "alphanumeric-name-only")
        }

        val result = runCatching {
            client.callTool("test-tool", emptyMap(), edgeCaseValidMeta)
        }

        assertTrue(result.isSuccess, "Edge case valid meta keys should be accepted")
    }

    @Test
    fun `should reject mcp reserved prefix`() = runTest {
        val invalidMeta = mapOf("mcp/internal" to "value")

        val exception = assertFailsWith<IllegalArgumentException> {
            client.callTool("test-tool", emptyMap(), invalidMeta)
        }

        assertContains(
            charSequence = exception.message ?: "",
            other = "Invalid _meta key",
        )
    }

    @Test
    fun `should reject modelcontextprotocol reserved prefix`() = runTest {
        val invalidMeta = mapOf("modelcontextprotocol/config" to "value")

        val exception = assertFailsWith<IllegalArgumentException> {
            client.callTool("test-tool", emptyMap(), invalidMeta)
        }

        assertContains(
            charSequence = exception.message ?: "",
            other = "Invalid _meta key",
        )
    }

    @Test
    fun `should reject nested reserved prefixes`() = runTest {
        val invalidKeys = listOf(
            "api.mcp.io/setting",
            "com.modelcontextprotocol.test/value",
            "example.mcp/data",
            "subdomain.mcp.com/config",
            "app.modelcontextprotocol.dev/setting",
            "test.mcp/value",
            "service.modelcontextprotocol/data",
        )

        invalidKeys.forEach { key ->
            val exception = assertFailsWith<IllegalArgumentException>(
                message = "Should reject nested reserved key: $key",
            ) {
                client.callTool("test-tool", emptyMap(), mapOf(key to "value"))
            }
            assertContains(
                charSequence = exception.message ?: "",
                other = "Invalid _meta key",
            )
        }
    }

    @Test
    fun `should reject case-insensitive reserved prefixes`() = runTest {
        val invalidKeys = listOf(
            "MCP/internal",
            "Mcp/config",
            "mCp/setting",
            "MODELCONTEXTPROTOCOL/data",
            "ModelContextProtocol/value",
            "modelContextProtocol/test",
        )

        invalidKeys.forEach { key ->
            val exception = assertFailsWith<IllegalArgumentException>(
                message = "Should reject case-insensitive reserved key: $key",
            ) {
                client.callTool("test-tool", emptyMap(), mapOf(key to "value"))
            }
            assertContains(
                charSequence = exception.message ?: "",
                other = "Invalid _meta key",
            )
        }
    }

    @Test
    fun `should reject invalid key formats`() = runTest {
        val invalidKeys = listOf(
            "", // empty key - not allowed at key level
            "/invalid", // starts with slash
            "-invalid", // starts with hyphen
            ".invalid", // starts with dot
            "in valid", // contains space
            "api../test", // consecutive dots
            "api./test", // label ends with dot
        )

        invalidKeys.forEach { key ->
            assertFailsWith<IllegalArgumentException>(
                message = "Should reject invalid key format: '$key'",
            ) {
                client.callTool("test-tool", emptyMap(), mapOf(key to "value"))
            }
        }
    }

    @Test
    fun `should convert various data types to JSON correctly`() = runTest {
        val complexMeta = createComplexMetaData()

        val result = runCatching {
            client.callTool(
                "test-tool",
                emptyMap(),
                complexMeta,
            )
        }

        assertTrue(result.isSuccess, "Complex data type conversion should not throw exceptions")

        mockTransport.lastJsonRpcRequest?.let { request ->
            assertEquals("tools/call", request.method)
            val params = request.params as JsonObject
            assertTrue(params.containsKey("_meta"), "Request should contain _meta field")
        }
    }

    @Test
    fun `should handle nested map structures correctly`() = runTest {
        val nestedMeta = buildNestedConfiguration()

        val result = runCatching {
            client.callTool("test-tool", emptyMap(), nestedMeta)
        }

        assertTrue(result.isSuccess)

        mockTransport.lastJsonRpcRequest?.let { request ->
            val params = request.params as JsonObject
            val metaField = params["_meta"] as JsonObject
            assertTrue(metaField.containsKey("config"))
        }
    }

    @Test
    fun `should include empty meta object when meta parameter not provided`() = runTest {
        client.callTool("test-tool", mapOf("arg" to "value"))

        mockTransport.lastJsonRpcRequest?.let { request ->
            val params = request.params as JsonObject
            val metaField = params["_meta"] as JsonObject
            assertTrue(metaField.isEmpty(), "Meta field should be empty when not provided")
        }
    }

    private fun createComplexMetaData(): Map<String, Any?> = buildMap {
        put("string", "text")
        put("number", 42)
        put("boolean", true)
        put("null_value", null)
        put("list", listOf(1, 2, 3))
        put("map", mapOf("nested" to "value"))
        put("enum", "STRING")
        put("int_array", intArrayOf(1, 2, 3))
    }

    private fun buildNestedConfiguration(): Map<String, Any> = buildMap {
        put(
            "config",
            buildMap {
                put(
                    "database",
                    buildMap {
                        put("host", "localhost")
                        put("port", 5432)
                    },
                )
                put("features", listOf("feature1", "feature2"))
            },
        )
    }
}

class MockTransport : Transport {
    private val _sentMessages = mutableListOf<JSONRPCMessage>()
    val sentMessages: List<JSONRPCMessage> = _sentMessages

    private var onMessageBlock: (suspend (JSONRPCMessage) -> Unit)? = null
    private var onCloseBlock: (() -> Unit)? = null
    private var onErrorBlock: ((Throwable) -> Unit)? = null

    override suspend fun start() = Unit

    override suspend fun send(message: JSONRPCMessage) {
        _sentMessages += message

        // Auto-respond to initialization and tool calls
        when (message) {
            is JSONRPCRequest -> {
                when (message.method) {
                    "initialize" -> {
                        val initResponse = JSONRPCResponse(
                            id = message.id,
                            result = InitializeResult(
                                protocolVersion = "2024-11-05",
                                capabilities = ServerCapabilities(
                                    tools = ServerCapabilities.Tools(listChanged = null),
                                ),
                                serverInfo = Implementation("mock-server", "1.0.0"),
                            ),
                        )
                        onMessageBlock?.invoke(initResponse)
                    }

                    "tools/call" -> {
                        val toolResponse = JSONRPCResponse(
                            id = message.id,
                            result = CallToolResult(
                                content = listOf(),
                                isError = false,
                            ),
                        )
                        onMessageBlock?.invoke(toolResponse)
                    }
                }
            }

            else -> {
                // Handle other message types if needed
            }
        }
    }

    override suspend fun close() {
        onCloseBlock?.invoke()
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        onMessageBlock = block
    }

    override fun onClose(block: () -> Unit) {
        onCloseBlock = block
    }

    override fun onError(block: (Throwable) -> Unit) {
        onErrorBlock = block
    }

    fun setupInitializationResponse() {
        // This method helps set up the mock for proper initialization
    }
}

val MockTransport.lastJsonRpcRequest: JSONRPCRequest?
    get() = sentMessages.lastOrNull() as? JSONRPCRequest
