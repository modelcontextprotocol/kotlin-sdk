package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Prompt
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Resource
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ServerBulkFeaturesTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(listChanged = null),
        prompts = ServerCapabilities.Prompts(listChanged = null),
        resources = ServerCapabilities.Resources(listChanged = null, subscribe = null),
    )

    // ── addTools ───────────────────────────────────────────────────────────────

    @Test
    fun `addTools should register all provided tools`() = runTest {
        server.addTools(
            listOf(
                RegisteredTool(Tool("bulk-a", ToolSchema(), "Tool A")) { CallToolResult(emptyList()) },
                RegisteredTool(Tool("bulk-b", ToolSchema(), "Tool B")) { CallToolResult(emptyList()) },
                RegisteredTool(Tool("bulk-c", ToolSchema(), "Tool C")) { CallToolResult(emptyList()) },
            ),
        )

        val tools = client.listTools().tools

        tools shouldHaveSize 3
        tools.map { it.name } shouldContainExactlyInAnyOrder listOf("bulk-a", "bulk-b", "bulk-c")
    }

    @Test
    fun `addTools should append to existing tools`() = runTest {
        server.addTool("existing", "Existing") { CallToolResult(emptyList()) }
        server.addTools(
            listOf(
                RegisteredTool(Tool("new-a", ToolSchema())) { CallToolResult(emptyList()) },
                RegisteredTool(Tool("new-b", ToolSchema())) { CallToolResult(emptyList()) },
            ),
        )

        val tools = client.listTools().tools

        tools shouldHaveSize 3
        tools.map { it.name } shouldContainExactlyInAnyOrder listOf("existing", "new-a", "new-b")
    }

    // ── addPrompts ─────────────────────────────────────────────────────────────

    @Test
    fun `addPrompts should register all provided prompts`() = runTest {
        server.addPrompts(
            listOf(
                RegisteredPrompt(Prompt("bulk-p1", "Prompt 1")) { GetPromptResult(messages = emptyList()) },
                RegisteredPrompt(Prompt("bulk-p2", "Prompt 2")) { GetPromptResult(messages = emptyList()) },
            ),
        )

        val prompts = client.listPrompts().prompts

        prompts shouldHaveSize 2
        prompts.map { it.name } shouldContainExactlyInAnyOrder listOf("bulk-p1", "bulk-p2")
    }

    @Test
    fun `addPrompts should append to existing prompts`() = runTest {
        server.addPrompt("existing-prompt", "Existing") { GetPromptResult(messages = emptyList()) }
        server.addPrompts(
            listOf(
                RegisteredPrompt(Prompt("new-p")) { GetPromptResult(messages = emptyList()) },
            ),
        )

        val prompts = client.listPrompts().prompts

        prompts shouldHaveSize 2
        prompts.map { it.name } shouldContainExactlyInAnyOrder listOf("existing-prompt", "new-p")
    }

    // ── addResources ───────────────────────────────────────────────────────────

    @Test
    fun `addResources should register all provided resources`() = runTest {
        server.addResources(
            listOf(
                RegisteredResource(Resource("test://bulk-r1", "Resource 1")) { ReadResourceResult(emptyList()) },
                RegisteredResource(Resource("test://bulk-r2", "Resource 2")) { ReadResourceResult(emptyList()) },
            ),
        )

        val resources = client.listResources().resources

        resources shouldHaveSize 2
        resources.map { it.uri } shouldContainExactlyInAnyOrder listOf("test://bulk-r1", "test://bulk-r2")
    }

    @Test
    fun `addResources should append to existing resources`() = runTest {
        server.addResource("test://existing-res", "Existing", "Existing resource") {
            ReadResourceResult(emptyList())
        }
        server.addResources(
            listOf(
                RegisteredResource(Resource("test://new-res", "New")) { ReadResourceResult(emptyList()) },
            ),
        )

        val resources = client.listResources().resources

        resources shouldHaveSize 2
        resources.map { it.uri } shouldContainExactlyInAnyOrder listOf("test://existing-res", "test://new-res")
    }

    // ── Partial-batch remove ───────────────────────────────────────────────────

    @Test
    fun `removeTools should return count of actually removed tools`() = runTest {
        server.addTool("present", "Present") { CallToolResult(emptyList()) }
        client.listTools().tools shouldHaveSize 1

        val count = server.removeTools(listOf("present", "absent-1", "absent-2"))

        count shouldBe 1
        client.listTools().tools.shouldBeEmpty()
    }

    @Test
    fun `removeTools on empty list should return zero`() = runTest {
        val count = server.removeTools(emptyList())

        count shouldBe 0
    }

    @Test
    fun `removePrompts should return count of actually removed prompts`() = runTest {
        server.addPrompt("present-p", "Present") { GetPromptResult(messages = emptyList()) }
        client.listPrompts().prompts shouldHaveSize 1

        val count = server.removePrompts(listOf("present-p", "absent-p"))

        count shouldBe 1
        client.listPrompts().prompts.shouldBeEmpty()
    }

    @Test
    fun `removePrompts on empty list should return zero`() = runTest {
        val count = server.removePrompts(emptyList())

        count shouldBe 0
    }

    @Test
    fun `removeResources should return count of actually removed resources`() = runTest {
        server.addResource("test://present-r", "Present", "Present resource") {
            ReadResourceResult(emptyList())
        }
        client.listResources().resources shouldHaveSize 1

        val count = server.removeResources(listOf("test://present-r", "test://absent-r"))

        count shouldBe 1
        client.listResources().resources.shouldBeEmpty()
    }

    @Test
    fun `removeResources on empty list should return zero`() = runTest {
        val count = server.removeResources(emptyList())

        count shouldBe 0
    }
}
