package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.string.shouldStartWith
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Prompt
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Resource
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import org.junit.jupiter.api.Test

class ServerFeatureCapabilityTest {

    @Test
    fun `feature mutators should require the matching capability`() {
        val server = Server(Implementation(name = "test server", version = "1.0"), ServerOptions(ServerCapabilities()))
        val tool = RegisteredTool(Tool(name = "t", inputSchema = ToolSchema())) { CallToolResult(emptyList()) }
        val prompt = RegisteredPrompt(Prompt(name = "p")) { GetPromptResult(messages = emptyList()) }
        val resource = RegisteredResource(Resource(uri = "test://r", name = "r")) { ReadResourceResult(emptyList()) }
        val mutatorsByCapability = mapOf<String, Map<String, Server.() -> Unit>>(
            "tools" to mapOf(
                "addTool" to { addTool(tool.tool, tool.handler) },
                "addTools" to { addTools(listOf(tool)) },
                "removeTool" to { removeTool("t") },
                "removeTools" to { removeTools(listOf("t")) },
            ),
            "prompts" to mapOf(
                "addPrompt" to { addPrompt(prompt.prompt, prompt.messageProvider) },
                "addPrompts" to { addPrompts(listOf(prompt)) },
                "removePrompt" to { removePrompt("p") },
                "removePrompts" to { removePrompts(listOf("p")) },
            ),
            "resources" to mapOf(
                "addResource" to { addResource("test://r", "r", "r", readHandler = resource.readHandler) },
                "addResources" to { addResources(listOf(resource)) },
                "removeResource" to { removeResource("test://r") },
                "removeResources" to { removeResources(listOf("test://r")) },
                "addResourceTemplate" to {
                    addResourceTemplate("test://{id}", "t") { _, _ -> ReadResourceResult(emptyList()) }
                },
                "removeResourceTemplate" to { removeResourceTemplate("test://{id}") },
            ),
        )

        for ((capability, mutators) in mutatorsByCapability) {
            for ((name, mutate) in mutators) {
                withClue(name) {
                    shouldThrow<IllegalStateException> { server.mutate() }.message shouldStartWith
                        "Server does not support $capability capability."
                }
            }
        }
    }
}
