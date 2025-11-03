package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.Prompt
import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.Resource
import io.modelcontextprotocol.kotlin.sdk.Tool

internal typealias FeatureKey = String

/**
 * Represents a feature with an associated unique key.
 */
internal interface Feature {
    val key: FeatureKey
}

/**
 * A wrapper class representing a registered tool on the server.
 *
 * @property tool The tool definition.
 * @property handler A suspend function to handle the tool call requests.
 */
public data class RegisteredTool(val tool: Tool, val handler: suspend (CallToolRequest) -> CallToolResult) : Feature {
    override val key: String = tool.name
}

/**
 * A wrapper class representing a registered prompt on the server.
 *
 * @property prompt The prompt definition.
 * @property messageProvider A suspend function that returns the prompt content when requested by the client.
 */
public data class RegisteredPrompt(
    val prompt: Prompt,
    val messageProvider: suspend (GetPromptRequest) -> GetPromptResult,
) : Feature {
    override val key: String = prompt.name
}

/**
 * A wrapper class representing a registered resource on the server.
 *
 * @property resource The resource definition.
 * @property readHandler A suspend function to handle read requests for this resource.
 */
public data class RegisteredResource(
    val resource: Resource,
    val readHandler: suspend (ReadResourceRequest) -> ReadResourceResult,
) : Feature {
    override val key: String = resource.uri
}
