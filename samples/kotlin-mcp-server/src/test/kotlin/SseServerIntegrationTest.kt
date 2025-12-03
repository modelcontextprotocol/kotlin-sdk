import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SseServerIntegrationTest {

    private val client: Client = TestEnvironment.client

    @Test
    fun `should list tools`(): Unit = runBlocking {
        // when
        val listToolsResult = client.listTools()

        // then
        assertNull(listToolsResult.meta)

        val tools = listToolsResult.tools
        assertEquals(actual = tools.size, expected = 1)
        assertEquals(expected = listOf("kotlin-sdk-tool"), actual = tools.map { it.name })
    }

    @Test
    fun `should list prompts`(): Unit = runBlocking {
        // when
        val listPromptsResult = client.listPrompts()

        // then
        assertNull(listPromptsResult.meta)

        val prompts = listPromptsResult.prompts

        assertEquals(expected = listOf("Kotlin Developer"), actual = prompts.map { it.name })
    }

    @Test
    fun `should list resources`(): Unit = runBlocking {
        val listResourcesResult = client.listResources()

        // then
        assertNull(listResourcesResult.meta)
        val resources = listResourcesResult.resources

        assertEquals(expected = listOf("Web Search"), actual = resources.map { it.name })
    }

    @Test
    fun `should get resource`(): Unit = runBlocking {
        val testResourceUri = "https://search.com/"
        val getResourcesResult = client.readResource(
            ReadResourceRequest(ReadResourceRequestParams(uri = testResourceUri)),
        )

        // then
        assertEquals(expected = null, actual = getResourcesResult.meta)
        val contents = getResourcesResult.contents
        assertEquals(expected = 1, actual = contents.size)
        assertTrue {
            contents.contains(
                TextResourceContents("Placeholder content for $testResourceUri", testResourceUri, "text/html"),
            )
        }
    }

    @Test
    fun `should call tool`(): Unit = runBlocking {
        // when
        val toolResult = client.callTool(
            name = "kotlin-sdk-tool",
            arguments = emptyMap(),
        )

        // then
        assertNotNull(toolResult)
        assertNull(toolResult.meta)
        val content = toolResult.content.single()
        assertIs<TextContent>(content, "Tool result should be a text content")

        assertEquals(expected = "Hello, world!", actual = content.text)
        assertEquals(expected = "text", actual = "${content.type}".lowercase())
    }
}
