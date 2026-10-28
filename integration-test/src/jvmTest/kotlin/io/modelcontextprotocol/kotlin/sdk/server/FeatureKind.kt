package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Prompt
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Resource
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema

/**
 * Registration API of one [Server] feature type, keyed by name (tool or prompt name, resource URI, URI template),
 * so a test can cover every feature type. Resource templates have no bulk API: their bulk operations
 * fall back to single calls.
 */
enum class FeatureKind(
    val listChangedMethod: Method.Defined,
    val capabilities: (listChanged: Boolean) -> ServerCapabilities,
    val add: Server.(name: String) -> Unit,
    val addAll: Server.(names: List<String>) -> Unit,
    val remove: Server.(name: String) -> Boolean,
    val removeAll: Server.(names: List<String>) -> Int,
    val list: suspend Client.() -> List<String>,
) {
    TOOL(
        listChangedMethod = Method.Defined.NotificationsToolsListChanged,
        capabilities = { ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = it)) },
        add = { addTool(it, "Tool $it") { CallToolResult(emptyList()) } },
        addAll = { names -> addTools(names.map(::registeredTool)) },
        remove = { removeTool(it) },
        removeAll = { removeTools(it) },
        list = { listTools().tools.map { it.name } },
    ),
    PROMPT(
        listChangedMethod = Method.Defined.NotificationsPromptsListChanged,
        capabilities = { ServerCapabilities(prompts = ServerCapabilities.Prompts(listChanged = it)) },
        add = { addPrompt(it) { GetPromptResult(messages = emptyList()) } },
        addAll = { names -> addPrompts(names.map(::registeredPrompt)) },
        remove = { removePrompt(it) },
        removeAll = { removePrompts(it) },
        list = { listPrompts().prompts.map { it.name } },
    ),
    RESOURCE(
        listChangedMethod = Method.Defined.NotificationsResourcesListChanged,
        capabilities = { ServerCapabilities(resources = ServerCapabilities.Resources(listChanged = it)) },
        add = { addResource(uri = it, name = it, description = "Resource $it") { ReadResourceResult(emptyList()) } },
        addAll = { names -> addResources(names.map(::registeredResource)) },
        remove = { removeResource(it) },
        removeAll = { removeResources(it) },
        list = { listResources().resources.map { it.uri } },
    ),
    RESOURCE_TEMPLATE(
        listChangedMethod = Method.Defined.NotificationsResourcesListChanged,
        capabilities = { ServerCapabilities(resources = ServerCapabilities.Resources(listChanged = it)) },
        add = { addResourceTemplate(uriTemplate = it, name = it) { _, _ -> ReadResourceResult(emptyList()) } },
        addAll = { names ->
            names.forEach { name ->
                addResourceTemplate(uriTemplate = name, name = name) { _, _ -> ReadResourceResult(emptyList()) }
            }
        },
        remove = { removeResourceTemplate(it) },
        removeAll = { names -> names.count { removeResourceTemplate(it) } },
        list = { listResourceTemplates(ListResourceTemplatesRequest()).resourceTemplates.map { it.uriTemplate } },
    ),
}

private fun registeredTool(name: String) =
    RegisteredTool(Tool(name = name, inputSchema = ToolSchema())) { CallToolResult(emptyList()) }

private fun registeredPrompt(name: String) =
    RegisteredPrompt(Prompt(name = name)) { GetPromptResult(messages = emptyList()) }

private fun registeredResource(name: String) =
    RegisteredResource(Resource(uri = name, name = name)) { ReadResourceResult(emptyList()) }
