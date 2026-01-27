package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.security

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.KotlinTestBase
import io.modelcontextprotocol.kotlin.sdk.integration.utils.AuthorizationRules
import io.modelcontextprotocol.kotlin.sdk.integration.utils.MockAuthorizationWrapper
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class AbstractToolSecurityIntegrationTest : KotlinTestBase() {

    private val publicToolName = "public-tool"
    private val secretToolName = "secret-tool"
    private val restrictedToolName = "restricted-tool"

    override fun configureServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(
            listChanged = true,
        ),
    )

    override fun configureServer() {
        configureServerWithAuthorization(
            allowedTools = setOf(publicToolName, restrictedToolName),
        )
    }

    protected fun configureServerWithAuthorization(
        allowedTools: Set<String>? = null,
        deniedTools: Set<String>? = null,
    ) {
        val authWrapper = MockAuthorizationWrapper(
            AuthorizationRules(
                allowedTools = allowedTools,
                deniedTools = deniedTools,
            ),
        )

        server.addTool(
            name = publicToolName,
            description = "A public tool that authorized users can access",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put(
                        "text",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Input text")
                        },
                    )
                },
                required = listOf("text"),
            ),
        ) { request ->
            if (!authWrapper.isAllowed("tools", "call", mapOf("name" to publicToolName))) {
                throw authWrapper.createDeniedError("Access denied to tool: $publicToolName")
            }

            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Public tool result",
                    ),
                ),
            )
        }

        server.addTool(
            name = secretToolName,
            description = "A secret tool that requires special permissions",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put(
                        "text",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Input text")
                        },
                    )
                },
                required = listOf("text"),
            ),
        ) { request ->
            if (!authWrapper.isAllowed("tools", "call", mapOf("name" to secretToolName))) {
                throw authWrapper.createDeniedError("Access denied to tool: $secretToolName")
            }

            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Secret tool result",
                    ),
                ),
            )
        }

        server.addTool(
            name = restrictedToolName,
            description = "A restricted tool",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put(
                        "text",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Input text")
                        },
                    )
                },
                required = listOf("text"),
            ),
        ) { request ->
            if (!authWrapper.isAllowed("tools", "call", mapOf("name" to restrictedToolName))) {
                throw authWrapper.createDeniedError("Access denied to tool: $restrictedToolName")
            }

            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Restricted tool result",
                    ),
                ),
            )
        }
    }

    @Test
    fun testListToolsAllowed() = runBlocking {
        val result = client.listTools()

        assertNotNull(result, "List tools result should not be null")
        assertTrue(result.tools.isNotEmpty(), "Tools list should not be empty")

        val publicTool = result.tools.find { it.name == publicToolName }
        assertNotNull(publicTool, "Public tool should be in the list")
        assertEquals("A public tool that authorized users can access", publicTool.description)
    }

    @Test
    fun testListToolsDenied() {
        runBlocking {
            val result = client.listTools()
            assertNotNull(result, "List should still work in default configuration")
        }
    }

    @Test
    fun testCallToolAllowed() = runBlocking {
        val result = client.callTool(publicToolName, mapOf("text" to "test"))

        assertNotNull(result, "Call tool result should not be null")
        assertTrue(result.content.isNotEmpty(), "Contents should not be empty")

        val content = result.content.first() as TextContent
        assertEquals("Public tool result", content.text)
    }

    @Test
    fun testCallToolDenied() = runBlocking {
        val result = client.callTool(secretToolName, mapOf("text" to "test"))

        assertNotNull(result, "Call tool result should not be null")
        withClue("Tool call should have isError=true for denied access") {
            result.isError shouldBe true
        }

        val textContent = result.content.firstOrNull { it is TextContent } as? TextContent
        assertNotNull(textContent, "Error content should be present in the result")
        withClue("Error message should mention access denied") {
            textContent.text.lowercase().contains("access denied") shouldBe true
        }
    }

    @Test
    fun testCallToolPartialAccess() = runBlocking {
        val publicResult = client.callTool(publicToolName, mapOf("text" to "test"))
        assertNotNull(publicResult, "Public tool should be accessible")
        withClue("Public tool should succeed without error") {
            (publicResult.isError ?: false) shouldBe false
        }

        val secretResult = client.callTool(secretToolName, mapOf("text" to "test"))
        assertNotNull(secretResult, "Secret tool call should return a result")
        withClue("Secret tool should have isError=true for denied access") {
            secretResult.isError shouldBe true
        }
        val secretTextContent = secretResult.content.firstOrNull { it is TextContent } as? TextContent
        assertNotNull(secretTextContent, "Error content should be present")
        withClue("Should be denied access to secret tool") {
            secretTextContent.text.lowercase().contains("access denied") shouldBe true
        }

        val restrictedResult = client.callTool(restrictedToolName, mapOf("text" to "test"))
        assertNotNull(restrictedResult, "Restricted tool should be accessible with proper permissions")
        withClue("Restricted tool should succeed without error") {
            (restrictedResult.isError ?: false) shouldBe false
        }
    }

    @Test
    fun testUnauthorizedAfterInitialization() = runBlocking {
        val result = client.callTool(secretToolName, mapOf("text" to "test"))

        assertNotNull(result, "Call tool result should not be null")
        withClue("Tool call should have isError=true for unauthorized access") {
            result.isError shouldBe true
        }

        val textContent = result.content.firstOrNull { it is TextContent } as? TextContent
        assertNotNull(textContent, "Error content should be present")
        withClue("Unauthorized operations should fail after successful initialization") {
            textContent.text.lowercase().contains("access denied") shouldBe true
        }
    }
}
