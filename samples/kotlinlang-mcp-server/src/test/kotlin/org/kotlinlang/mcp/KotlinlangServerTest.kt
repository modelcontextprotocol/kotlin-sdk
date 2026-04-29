package org.kotlinlang.mcp

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.kotlinlang.mcp.config.ServerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalMcpApi::class)
class KotlinlangServerTest {

    private val testConfig = ServerConfig(
        algoliaAppId = "test-app-id",
        algoliaApiKey = "test-api-key",
        algoliaIndexName = "test-index",
    )

    @Test
    fun `server registers both tools with correct schema and annotations`() = runTest {
        val kotlinlangServer = KotlinlangServer(testConfig)
        kotlinlangServer.use {
            val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()

            val client = Client(
                clientInfo = Implementation(name = "test-client", version = "1.0"),
                options = ClientOptions(),
            )

            joinAll(
                launch { client.connect(clientTransport) },
                launch { kotlinlangServer.server.createSession(serverTransport) },
            )

            val tools = client.listTools().tools

            assertEquals(2, tools.size)

            val searchTool = tools.find { it.name == "search_kotlinlang" }
            val searchProps = searchTool?.inputSchema?.properties
            assertTrue(searchProps?.containsKey("query") ?: false)
            assertEquals(listOf("query"), searchTool.inputSchema.required)
            assertEquals(true, searchTool.annotations?.readOnlyHint)
            assertEquals(true, searchTool.annotations?.openWorldHint)

            val pageTool = tools.find { it.name == "get_kotlinlang_page" }
            val pageProps = pageTool?.inputSchema?.properties
            assertTrue(pageProps?.containsKey("path") ?: false)
            assertEquals(listOf("path"), pageTool.inputSchema.required)
            assertEquals(true, pageTool.annotations?.readOnlyHint)
            assertEquals(true, pageTool.annotations?.openWorldHint)

            client.close()
        }
    }
}
