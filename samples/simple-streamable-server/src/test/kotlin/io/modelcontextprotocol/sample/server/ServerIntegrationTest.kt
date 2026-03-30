package io.modelcontextprotocol.sample.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServerIntegrationTest {

    private val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
        configureServer()
    }

    private val httpClient = HttpClient(CIO) {
        install(SSE)
    }

    private lateinit var mcpClient: Client

    @BeforeAll
    suspend fun beforeAll() {
        server.start(wait = false)
        val port = server.engine.resolvedConnectors().single().port

        mcpClient = httpClient.mcpStreamableHttp(
            url = "http://127.0.0.1:$port/mcp",
        )
    }

    @AfterAll
    suspend fun afterAll() {
        mcpClient.close()
        httpClient.close()
        server.stop(500, 1000)
    }

    @Test
    fun `should negotiate server capabilities`() {
        val capabilities = mcpClient.serverCapabilities
        assertNotNull(capabilities)
        assertNotNull(capabilities.tools)
        assertNotNull(capabilities.prompts)
        assertNotNull(capabilities.resources)
        assertNotNull(capabilities.logging)
    }

    @Test
    fun `should report server version`() {
        val version = mcpClient.serverVersion
        assertNotNull(version)
        assertEquals("simple-streamable-http-server", version.name)
        assertEquals("1.0.0", version.version)
    }

    @Test
    suspend fun `should list tools`() {
        val result = mcpClient.listTools()
        val toolNames = result.tools.map { it.name }.sorted()
        assertEquals(listOf("greet", "multi-greet"), toolNames)
    }

    @Test
    suspend fun `should call greet tool`() {
        val result = mcpClient.callTool(name = "greet", arguments = mapOf("name" to "Alice"))
        val content = result.content.single()
        assertIs<TextContent>(content)
        assertEquals("Hello, Alice!", content.text)
    }

    @Test
    suspend fun `should call greet tool with default name`() {
        val result = mcpClient.callTool(name = "greet", arguments = emptyMap())
        val content = result.content.single()
        assertIs<TextContent>(content)
        assertEquals("Hello, World!", content.text)
    }

    @Test
    suspend fun `should call multi-greet tool with logging notifications`() {
        val result = mcpClient.callTool(name = "multi-greet", arguments = mapOf("name" to "Bob"))
        val content = result.content.single()
        assertIs<TextContent>(content)
        assertEquals("Good morning, Bob!", content.text)
    }

    @Test
    suspend fun `should list prompts`() {
        val result = mcpClient.listPrompts()
        assertEquals(listOf("greeting-template"), result.prompts.map { it.name })
    }

    @Test
    suspend fun `should get greeting template prompt`() {
        val result = mcpClient.getPrompt(
            GetPromptRequest(
                GetPromptRequestParams(
                    name = "greeting-template",
                    arguments = mapOf("name" to "Charlie"),
                ),
            ),
        )
        val message = result.messages.single()
        assertEquals(Role.User, message.role)
        val content = message.content
        assertIs<TextContent>(content)
        assertEquals("Please greet Charlie in a friendly manner.", content.text)
    }

    @Test
    suspend fun `should list resources`() {
        val result = mcpClient.listResources()
        assertEquals(listOf("Default Greeting"), result.resources.map { it.name })
    }

    @Test
    suspend fun `should read default greeting resource`() {
        val result = mcpClient.readResource(
            ReadResourceRequest(
                ReadResourceRequestParams(uri = "https://example.com/greetings/default"),
            ),
        )
        val content = result.contents.single()
        assertIs<TextResourceContents>(content)
        assertEquals("Hello, world!", content.text)
        assertEquals("text/plain", content.mimeType)
    }
}

