package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Resource
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class ServerFeaturesListTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(listChanged = null),
        prompts = ServerCapabilities.Prompts(listChanged = null),
        resources = ServerCapabilities.Resources(listChanged = null, subscribe = null),
    )

    // ── listTools ──────────────────────────────────────────────────────────────

    @Test
    fun `listTools should return empty list when no tools registered`() = runTest {
        client.listTools().tools.shouldBeEmpty()
    }

    @Test
    fun `listTools should return all registered tools`() = runTest {
        server.addTool("alpha", "First tool") { CallToolResult(emptyList()) }
        server.addTool("beta", "Second tool") { CallToolResult(emptyList()) }

        val tools = client.listTools().tools

        tools shouldHaveSize 2
        tools.map { it.name } shouldContainExactlyInAnyOrder listOf("alpha", "beta")
    }

    @Test
    fun `listTools should expose tool name and description`() = runTest {
        server.addTool("my-tool", "Detailed description") { CallToolResult(emptyList()) }

        val tool = client.listTools().tools.single()

        assertSoftly(tool) {
            name shouldBe "my-tool"
            description shouldBe "Detailed description"
        }
    }

    // ── listPrompts ────────────────────────────────────────────────────────────

    @Test
    fun `listPrompts should return empty list when no prompts registered`() = runTest {
        client.listPrompts().prompts.shouldBeEmpty()
    }

    @Test
    fun `listPrompts should return all registered prompts`() = runTest {
        server.addPrompt("prompt-a", "First prompt") { GetPromptResult(messages = emptyList()) }
        server.addPrompt("prompt-b", "Second prompt") { GetPromptResult(messages = emptyList()) }

        val prompts = client.listPrompts().prompts

        prompts shouldHaveSize 2
        prompts.map { it.name } shouldContainExactlyInAnyOrder listOf("prompt-a", "prompt-b")
    }

    @Test
    fun `listPrompts should expose prompt description`() = runTest {
        server.addPrompt("my-prompt", "Helpful context prompt") { GetPromptResult(messages = emptyList()) }

        val prompt = client.listPrompts().prompts.single()

        assertSoftly(prompt) {
            name shouldBe "my-prompt"
            description shouldBe "Helpful context prompt"
        }
    }

    @Test
    fun `listPrompts should include prompt arguments`() = runTest {
        val args = listOf(
            PromptArgument(name = "topic", description = "The topic to discuss", required = true),
            PromptArgument(name = "tone", description = "Tone of voice", required = false),
        )
        server.addPrompt("templated", "A templated prompt", args) { GetPromptResult(messages = emptyList()) }

        val prompt = client.listPrompts().prompts.single()

        val promptArgs = prompt.arguments.shouldNotBeNull()
        promptArgs shouldHaveSize 2
        promptArgs.map { it.name } shouldContainExactlyInAnyOrder listOf("topic", "tone")
        promptArgs.first { it.name == "topic" }.required shouldBe true
        promptArgs.first { it.name == "tone" }.required shouldBe false
    }

    @Test
    fun `listPrompts should expose null description`() = runTest {
        server.addPrompt("no-desc", null) { GetPromptResult(messages = emptyList()) }

        client.listPrompts().prompts.single().description shouldBe null
    }

    // ── listResources ──────────────────────────────────────────────────────────

    @Test
    fun `listResources should return empty list when no resources registered`() = runTest {
        client.listResources().resources.shouldBeEmpty()
    }

    @Test
    fun `listResources should return all registered resources`() = runTest {
        server.addResource("test://res-1", "Resource 1", "First") { ReadResourceResult(emptyList()) }
        server.addResource("test://res-2", "Resource 2", "Second") { ReadResourceResult(emptyList()) }

        val resources = client.listResources().resources

        resources shouldHaveSize 2
        resources.map { it.uri } shouldContainExactlyInAnyOrder listOf("test://res-1", "test://res-2")
    }

    @Test
    fun `listResources should expose resource metadata`() = runTest {
        server.addResource("test://my-res", "My Resource", "A detailed resource", "application/json") {
            ReadResourceResult(emptyList())
        }

        val resource = client.listResources().resources.single()

        assertSoftly(resource) {
            uri shouldBe "test://my-res"
            name shouldBe "My Resource"
            description shouldBe "A detailed resource"
            mimeType shouldBe "application/json"
        }
    }

    @Test
    fun `listResources should expose resource without mimeType`() = runTest {
        server.addResources(
            listOf(
                RegisteredResource(Resource("test://no-mime", "No Mime")) {
                    ReadResourceResult(emptyList())
                },
            ),
        )

        client.listResources().resources.single().mimeType shouldBe null
    }

    // ── listResourceTemplates ──────────────────────────────────────────────────

    @Test
    fun `listResourceTemplates should return empty list`() = runTest {
        client.listResourceTemplates(ListResourceTemplatesRequest()).resourceTemplates.shouldBeEmpty()
    }

    // ── add / remove round trip ────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(FeatureKind::class)
    fun `list should reflect added and removed features`(kind: FeatureKind) = runTest {
        kind.add(server, "a")
        kind.addAll(server, listOf("b", "c"))
        kind.list(client) shouldContainExactlyInAnyOrder listOf("a", "b", "c")

        kind.remove(server, "a") shouldBe true
        kind.remove(server, "a") shouldBe false
        kind.removeAll(server, listOf("b", "c", "absent")) shouldBe 2
        kind.list(client).shouldBeEmpty()
    }
}
