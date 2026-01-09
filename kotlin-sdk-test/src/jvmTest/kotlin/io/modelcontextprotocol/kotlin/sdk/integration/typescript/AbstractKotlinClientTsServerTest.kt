package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.integration.utils.DisabledOnCI
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisabledOnCI
abstract class AbstractKotlinClientTsServerTest : TsTestBase() {
    protected abstract suspend fun <T> useClient(block: suspend (Client) -> T): T

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun connectsAndPings() = runBlocking(Dispatchers.IO) {
        useClient { client ->
            assertNotNull(client, "Client should be initialized")
            val ping = client.ping()
            assertNotNull(ping, "Ping result should not be null")
            val serverImpl = client.serverVersion
            assertNotNull(serverImpl, "Server implementation should not be null")
            println("Connected to TypeScript server: ${serverImpl.name} v${serverImpl.version}")
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun listsTools() = runBlocking(Dispatchers.IO) {
        useClient { client ->
            val result = client.listTools()
            assertNotNull(result, "Tools list should not be null")
            assertTrue(result.tools.isNotEmpty(), "Tools list should not be empty")
            val toolNames = result.tools.map { it.name }
            assertTrue("greet" in toolNames, "Greet tool should be available")
            assertTrue("multi-greet" in toolNames, "Multi-greet tool should be available")
            // Some tests also check collect-user-info; keep base minimal and non-breaking
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun callGreet() = runBlocking(Dispatchers.IO) {
        useClient { client ->
            val testName = "TestUser"
            val arguments = mapOf("name" to testName)
            val result = client.callTool("greet", arguments)
            assertNotNull(result, "Tool call result should not be null")
            val callResult = result
            val textContent = callResult.content.firstOrNull { it is TextContent } as? TextContent
            assertNotNull(textContent, "Text content should be present in the result")
            assertEquals("Hello, $testName!", textContent.text)
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun multipleClients() = runBlocking(Dispatchers.IO) {
        useClient { client1 ->
            useClient { client2 ->
                val tools1 = client1.listTools()
                val tools2 = client2.listTools()
                assertTrue(tools1.tools.isNotEmpty(), "Tools list for first client should not be empty")
                assertTrue(tools2.tools.isNotEmpty(), "Tools list for second client should not be empty")
                val toolNames1 = tools1.tools.map { it.name }
                val toolNames2 = tools2.tools.map { it.name }
                assertTrue("greet" in toolNames1, "Greet tool should be available to first client")
                assertTrue("multi-greet" in toolNames1, "Multi-greet tool should be available to first client")
                assertTrue("greet" in toolNames2, "Greet tool should be available to second client")
                assertTrue("multi-greet" in toolNames2, "Multi-greet tool should be available to second client")
            }
        }
    }
}
