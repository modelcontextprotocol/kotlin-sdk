import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.ContentTypes
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SseServerIntegrationTest {

    private val client: Client = TestEnvironment.client

    @Test
    fun `should get tools`(): Unit = runBlocking {
        val tools = client.listTools().tools

        assertEquals(expected = listOf("kotlin-sdk-tool"), actual = tools.map { it.name })
    }

    @Test
    fun `should get prompts`(): Unit = runBlocking {
        val prompts = client.listPrompts().prompts

        assertEquals(expected = listOf("Kotlin Developer"), actual = prompts.map { it.name })
    }

    @Test
    fun `should get resources`(): Unit = runBlocking {
        val resources = client.listResources().resources

        assertEquals(expected = listOf("Web Search"), actual = resources.map { it.name })
    }

    @Test
    fun `should call tool`(): Unit = runBlocking {
        val toolResult = client.callTool("kotlin-sdk-tool", EmptyJsonObject)
        val content = toolResult?.content?.single()
        assertIs<TextContent>(content, "Tool result should be a text content")

        assertEquals(expected = "Hello, world!", actual = content.text)
        assertEquals(expected = ContentTypes.TEXT, actual = content.type)
    }
}
