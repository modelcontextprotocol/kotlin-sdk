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

public interface UriTemplateArgumentExtractor {
    /**
     * Extracts the arguments from the given [input] string based on the URI template defined in this extractor.
     *
     * @param input The input string to extract arguments from.
     * @return A map of argument names to their corresponding values extracted from the input string.
     *         If the input does not match the URI template, an empty map is returned.
     */
    public fun extractArguments(input: String): Map<String, String>
}

/**
 * Represents a unique key for a feature and allows comparing inputs with the [key].
 */
public abstract class FeatureKey<out T> {
    /**
     * The unique key identifying the feature.
     */
    public abstract val key: String

    /**
     * The value associated with the feature key. This is used for matching inputs which do not have to match the [key].
     */
    public abstract val value: T

    /**
     * Checks if the given [input] matches the [value] of this feature key.
     *
     * @param input The input string to be matched against the feature key's value.
     * @return `true` if the input matches the value, `false` otherwise.
     */
    public open fun matches(input: String): Boolean = value == input
    public override fun equals(other: Any?): Boolean = other is FeatureKey<*> && key == other.key
    public override fun hashCode(): Int = key.hashCode()
}

/**
 * A [FeatureKey] implementation for string-based keys. The [value] is the same as the [key] and inputs are matched by equality with the [key].
 *
 * @property key The unique string key identifying the feature.
 */
public class StringFeatureKey(override val key: String) : FeatureKey<String>() {
    override val value: String = key
}

/**
 * A [FeatureKey] implementation for URI template-based keys. The [value] is a regex generated from the URI template in
 * the [key] based on RFC6570 (current support: Level 1 thereof). Inputs are matched against the generated regex.
 *
 * @property key The URI template string key identifying the feature.
 */
public class UriTemplateFeatureKey(override val key: String) :
    FeatureKey<Regex>(),
    UriTemplateArgumentExtractor {
    override val value: Regex

    /**
     * A list of variable names defined in the URI template. This is populated during initialization when the URI template is converted to a regex.
     */
    private val groups: MutableList<String> = mutableListOf()

    init {
        // Convert URI template to regex as follows:
        // - A simple variable `{variable}` is replaced with `(?<variable>[^/]+)`
        // - A wildcard variable `{variable*}` is replaced with `(?<variable>.+)`
        // - Query parameters are ignored
        val uriWithoutQueryParameters = this.key.replace(Regex("\\{\\?[^}]+}"), "")

        val newRegex = Regex("\\{(<groupName>[^}*]*)(<wildcard>[*]?)}")
            .replace(uriWithoutQueryParameters) { matchResult ->
                val groupName = matchResult.groups["groupName"]?.value
                checkNotNull(groupName) { "Invalid URI template: $this" }
                groups.add(groupName)
                if (matchResult.groups["wildcard"]?.value != null) {
                    "(?<$groupName>.+)"
                } else {
                    "(?<$groupName>[^/]*)"
                }
            }
        this.value = Regex(newRegex)
    }

    override fun matches(input: String): Boolean = value.matches(input)

    override fun extractArguments(input: String): TemplateValues {
        val matchGroups = value.matchEntire(input)?.groups
        return groups.mapNotNull { groupName ->
            val groupValue = matchGroups?.get(groupName)?.value
            if (groupValue != null) {
                groupName to groupValue
            } else {
                null
            }
        }.toMap()
    }
}

/**
 * A map of template variable names to their corresponding values extracted from an input string based on a URI template.
 */
public typealias TemplateValues = Map<String, String>

/**
 * The [FeatureKey] used for [Tool]s.
 */
public typealias ToolFeatureKey = StringFeatureKey

/**
 * The [FeatureKey] used for [Prompt]s.
 */
public typealias PromptFeatureKey = StringFeatureKey

/**
 * The [FeatureKey] used for [Resource]s.
 */
public typealias ResourceFeatureKey = StringFeatureKey

/**
 * The [FeatureKey] used for [ResourceTemplate]s.
 */
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
    val readHandler: suspend (ReadResourceRequest, TemplateValues) -> ReadResourceResult,
) : Feature<ResourceTemplateFeatureKey> {
    override val key: ResourceTemplateFeatureKey = UriTemplateFeatureKey(resourceTemplate.uriTemplate)
}
