import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SseServerIntegrationTest {

    private val client: Client = TestEnvironment.client

    @Test
    fun `should list tools`(): Unit = runBlocking {
        // when
        val listToolsResult = client.listTools()

        // then
        assertEquals(expected = EmptyJsonObject, actual = listToolsResult._meta)

        val tools = listToolsResult.tools
        assertEquals(actual = tools.size, expected = 1)
        assertEquals(expected = listOf("kotlin-sdk-tool"), actual = tools.map { it.name })
    }

    @Test
    fun `should list prompts`(): Unit = runBlocking {
        // when
        val listPromptsResult = client.listPrompts()

        // then
        assertEquals(expected = EmptyJsonObject, actual = listPromptsResult._meta)

        val prompts = listPromptsResult.prompts

        assertEquals(expected = listOf("Kotlin Developer"), actual = prompts.map { it.name })
    }

    @Test
    fun `should list resources`(): Unit = runBlocking {
        val listResourcesResult = client.listResources()

        // then
        assertEquals(expected = EmptyJsonObject, actual = listResourcesResult._meta)
        val resources = listResourcesResult.resources

        assertEquals(expected = listOf("Web Search"), actual = resources.map { it.name })
    }

    @Test
    fun `should get resource`(): Unit = runBlocking {
        val testResourceUri = "https://search.com/"
        val listResourcesResult = client.readResource(
            ReadResourceRequest(uri = testResourceUri),
        )

        // then
        assertEquals(expected = EmptyJsonObject, actual = listResourcesResult._meta)
        val contents = listResourcesResult.contents
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
        val toolResult = client.callTool("kotlin-sdk-tool", EmptyJsonObject)

        // then
        assertNotNull(toolResult)
        assertEquals(expected = EmptyJsonObject, actual = toolResult._meta)
        val content = toolResult.content.single()
        assertIs<TextContent>(content, "Tool result should be a text content")

        assertEquals(expected = "Hello, world!", actual = content.text)
        assertEquals(expected = "text", actual = "${content.type}".lowercase())
    }
}
