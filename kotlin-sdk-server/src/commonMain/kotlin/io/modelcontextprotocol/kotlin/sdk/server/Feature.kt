package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Prompt
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Resource
import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate
import io.modelcontextprotocol.kotlin.sdk.types.Tool

public abstract class FeatureKey<out T> {
    public abstract val key: String
    public abstract val value: T

    public open fun matches(input: String): Boolean = value == input
    public override fun equals(other: Any?): Boolean = other is FeatureKey<*> && key == other.key
    public override fun hashCode(): Int = key.hashCode()
}

public class StringFeatureKey(override val key: String) : FeatureKey<String>() {
    override val value: String = key
}

public class UriTemplateFeatureKey(override val key: String) : FeatureKey<Regex>() {
    override val value: Regex
    init {
        // Convert URI template to regex as follows:
        // - A simple variable `{variable}` is replaced with `(?<variable>[^/]+)`
        // - A wildcard variable `{variable*}` is replaced with `(?<variable>.+)`
        // - Query parameters are ignored
        val uriWithoutQueryParameters = this.key.replace(Regex("\\{\\?[^}]+}"), "")

        val newRegex = Regex("\\{(<groupName>[^}*]*)(<wildcard>[*]?)}")
            .replace(uriWithoutQueryParameters) { matchResult ->
                val groupName = matchResult.groups["groupName"]
                checkNotNull(groupName) { "Invalid URI template: $this" }
                if (matchResult.groups["wildcard"] != null) {
                    "(?<$groupName>.+)"
                } else {
                    "(?<$groupName>[^/]*)"
                }
            }
        this.value = Regex(newRegex)
    }

    override fun matches(input: String): Boolean = value.matches(input)
}

public typealias ToolFeatureKey = StringFeatureKey
public typealias PromptFeatureKey = StringFeatureKey
public typealias ResourceFeatureKey = StringFeatureKey
public typealias ResourceTemplateFeatureKey = UriTemplateFeatureKey

/**
 * Represents a feature with an associated unique key.
 */
public interface Feature<out T> {
    public val key: T
}

/**
 * A wrapper class representing a registered tool on the server.
 *
 * @property tool The tool definition.
 * @property handler A suspend function to handle the tool call requests.
 */
public data class RegisteredTool(val tool: Tool, val handler: suspend (CallToolRequest) -> CallToolResult) :
    Feature<ToolFeatureKey> {
    override val key: ToolFeatureKey = StringFeatureKey(tool.name)
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
) : Feature<PromptFeatureKey> {
    override val key: PromptFeatureKey = StringFeatureKey(prompt.name)
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
) : Feature<ResourceFeatureKey> {
    override val key: ResourceFeatureKey = StringFeatureKey(resource.uri)
}

/**
 * A wrapper class representing a registered resource on the server.
 *
 * @property resourceTemplate The resource template definition.
 * @property readHandler A suspend function to handle read requests for this resource.
 */
public data class RegisteredResourceTemplate(
    val resourceTemplate: ResourceTemplate,
    val readHandler: suspend (ReadResourceRequest) -> ReadResourceResult,
) : Feature<ResourceTemplateFeatureKey> {
    override val key: ResourceTemplateFeatureKey = UriTemplateFeatureKey(resourceTemplate.uriTemplate)
}
