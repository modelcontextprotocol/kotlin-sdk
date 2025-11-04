import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpSseTransport
import io.modelcontextprotocol.sample.server.runSseMcpServerUsingKtorPlugin
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SseServerIntegrationTest {

    companion object {
        private const val PORT = 3002
    }

    private lateinit var client: Client

    private fun initClient(port: Int) {
        client = Client(
            Implementation(name = "test-client", version = "0.1.0"),
        )

        val httpClient = HttpClient(CIO) {
            install(SSE)
        }

        // Create a transport wrapper that captures the session ID and received messages
        val transport = httpClient.mcpSseTransport {
            url {
                this.host = "127.0.0.1"
                this.port = port
            }
        }
        runBlocking {
            client.connect(transport)
        }
    }

    @BeforeAll
    fun setUp() {
        runSseMcpServerUsingKtorPlugin(PORT, wait = false)
        initClient(PORT)
    }

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
        assertEquals(expected = "text", actual = content.type)
    }
}
