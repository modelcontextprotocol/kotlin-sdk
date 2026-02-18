package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject

/**
 * Creates a [GetPromptRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [name][GetPromptRequestBuilder.name] - The name of the prompt to retrieve
 *
 * ## Optional
 * - [arguments][GetPromptRequestBuilder.arguments] - Arguments to pass to the prompt
 * - [meta][GetPromptRequestBuilder.meta] - Metadata for the request
 *
 * Example without arguments:
 * ```kotlin
 * val request = GetPromptRequest {
 *     name = "greeting"
 * }
 * ```
 *
 * Example with arguments:
 * ```kotlin
 * val request = GetPromptRequest {
 *     name = "userProfile"
 *     arguments = mapOf(
 *         "userId" to "123",
 *         "includeDetails" to "true"
 *     )
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the get prompt request
 * @return A configured [GetPromptRequest] instance
 * @see GetPromptRequestBuilder
 * @see GetPromptRequest
 */
@ExperimentalMcpApi
public inline operator fun GetPromptRequest.Companion.invoke(
    block: GetPromptRequestBuilder.() -> Unit,
): GetPromptRequest = GetPromptRequestBuilder().apply(block).build()

/**
 * DSL builder for constructing [GetPromptRequest] instances.
 *
 * This builder retrieves a specific prompt by name, optionally with arguments.
 *
 * ## Required
 * - [name] - The name of the prompt to retrieve
 *
 * ## Optional
 * - [arguments] - Arguments to pass to the prompt
 * - [meta] - Metadata for the request
 *
 * @see GetPromptRequest
 */
@McpDsl
public class GetPromptRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    /**
     * The name of the prompt to retrieve. This is a required field.
     *
     * Example: `name = "greeting"`
     */
    public var name: String? = null

    /**
     * Optional arguments to pass to the prompt.
     *
     * Example:
     * ```kotlin
     * arguments = mapOf("userId" to "123", "lang" to "en")
     * ```
     */
    public var arguments: Map<String, String>? = null

    @PublishedApi
    override fun build(): GetPromptRequest {
        val name = requireNotNull(name) {
            "Missing required field 'name'. Example: name = \"promptName\""
        }

        val params = GetPromptRequestParams(name = name, arguments = arguments, meta = meta)
        return GetPromptRequest(params)
    }
}

/**
 * Creates a [ListPromptsRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [cursor][ListPromptsRequestBuilder.cursor] - Pagination cursor for fetching next page
 * - [meta][ListPromptsRequestBuilder.meta] - Metadata for the request
 *
 * Example without pagination:
 * ```kotlin
 * val request = ListPromptsRequest { }
 * ```
 *
 * Example with pagination:
 * ```kotlin
 * val request = ListPromptsRequest {
 *     cursor = "eyJwYWdlIjogMn0="
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list prompts request
 * @return A configured [ListPromptsRequest] instance
 * @see ListPromptsRequestBuilder
 * @see ListPromptsRequest
 */
@ExperimentalMcpApi
public inline operator fun ListPromptsRequest.Companion.invoke(
    block: ListPromptsRequestBuilder.() -> Unit,
): ListPromptsRequest = ListPromptsRequestBuilder().apply(block).build()

/**
 * DSL builder for constructing [ListPromptsRequest] instances.
 *
 * This builder retrieves a list of available prompts, with optional pagination support.
 * All fields are optional.
 *
 * ## Optional
 * - [cursor] - Pagination cursor (inherited from [PaginatedRequestBuilder])
 * - [meta] - Metadata for the request (inherited from [RequestBuilder])
 *
 * @see ListPromptsRequest
 * @see PaginatedRequestBuilder
 */
@McpDsl
public class ListPromptsRequestBuilder @PublishedApi internal constructor() : PaginatedRequestBuilder() {
    @PublishedApi
    override fun build(): ListPromptsRequest {
        val params = paginatedRequestParams(cursor = cursor, meta = meta)
        return ListPromptsRequest(params)
    }
}

// ============================================================================
// Result Builders (Server-side)
// ============================================================================

/**
 * Creates a [GetPromptResult] using a type-safe DSL builder.
 *
 * ## Required
 * - [messages][GetPromptResultBuilder.messagesList] - List of prompt messages (at least one)
 *
 * ## Optional
 * - [description][GetPromptResultBuilder.description] - Description of the prompt
 * - [meta][GetPromptResultBuilder.meta] - Metadata for the response
 *
 * Example:
 * ```kotlin
 * val result = GetPromptResult {
 *     description = "A greeting prompt"
 *     message {
 *         role = Role.User
 *         content = TextContent("Hello, how can I help you today?")
 *     }
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the get prompt result
 * @return A configured [GetPromptResult] instance
 * @see GetPromptResultBuilder
 * @see GetPromptResult
 */
@ExperimentalMcpApi
public inline operator fun GetPromptResult.Companion.invoke(block: GetPromptResultBuilder.() -> Unit): GetPromptResult =
    GetPromptResultBuilder().apply(block).build()

/**
 * DSL builder for constructing [GetPromptResult] instances.
 *
 * This builder creates a response containing prompt messages.
 *
 * ## Required
 * - At least one message (via [message] method)
 *
 * ## Optional
 * - [description] - Description of the prompt
 * - [meta] - Metadata for the response
 *
 * @see GetPromptResult
 */
@McpDsl
public class GetPromptResultBuilder @PublishedApi internal constructor() : ResultBuilder() {
    private val messagesList: MutableList<PromptMessage> = mutableListOf()

    /**
     * Optional description for the prompt.
     *
     * Example: `description = "A personalized greeting prompt for users"`
     */
    public var description: String? = null

    /**
     * Adds a pre-built message to the result.
     *
     * Example:
     * ```kotlin
     * message(PromptMessage(
     *     role = Role.User,
     *     content = TextContent("What is your name?")
     * ))
     * ```
     *
     * @param message The prompt message to add
     */
    public fun message(message: PromptMessage) {
        messagesList.add(message)
    }

    /**
     * Adds a message using role and content block.
     *
     * Example:
     * ```kotlin
     * message(Role.User, TextContent("What is your name?"))
     * ```
     *
     * @param role The role of the message sender
     * @param content The content block
     */
    public fun message(role: Role, content: ContentBlock) {
        messagesList.add(PromptMessage(role = role, content = content))
    }

    @PublishedApi
    override fun build(): GetPromptResult {
        require(messagesList.isNotEmpty()) {
            "At least one message is required. Use message() to add messages."
        }

        return GetPromptResult(
            messages = messagesList.toList(),
            description = description,
            meta = meta,
        )
    }
}

/**
 * Creates a [ListPromptsResult] using a type-safe DSL builder.
 *
 * ## Required
 * - [prompts][ListPromptsResultBuilder.promptsList] - List of available prompts (at least one)
 *
 * ## Optional
 * - [nextCursor][ListPromptsResultBuilder.nextCursor] - Pagination cursor for next page
 * - [meta][ListPromptsResultBuilder.meta] - Metadata for the response
 *
 * Example:
 * ```kotlin
 * val result = ListPromptsResult {
 *     prompt {
 *         name = "greeting"
 *         description = "A friendly greeting prompt"
 *     }
 *     prompt {
 *         name = "farewell"
 *         description = "A polite farewell prompt"
 *     }
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list prompts result
 * @return A configured [ListPromptsResult] instance
 * @see ListPromptsResultBuilder
 * @see ListPromptsResult
 */
@ExperimentalMcpApi
public inline operator fun ListPromptsResult.Companion.invoke(
    block: ListPromptsResultBuilder.() -> Unit,
): ListPromptsResult = ListPromptsResultBuilder().apply(block).build()

/**
 * DSL builder for constructing [ListPromptsResult] instances.
 *
 * This builder creates a response containing a list of prompts available on the server,
 * with optional pagination support.
 *
 * ## Required
 * - At least one prompt (via [prompt] method)
 *
 * ## Optional
 * - [nextCursor] - Pagination cursor (inherited from [PaginatedResultBuilder])
 * - [meta] - Metadata for the response (inherited from [ResultBuilder])
 *
 * @see ListPromptsResult
 * @see PaginatedResultBuilder
 */
@McpDsl
public class ListPromptsResultBuilder @PublishedApi internal constructor() : PaginatedResultBuilder() {
    private val promptsList: MutableList<Prompt> = mutableListOf()

    /**
     * Adds a pre-built prompt to the result.
     *
     * Example:
     * ```kotlin
     * val myPrompt = Prompt(
     *     name = "greeting",
     *     description = "A friendly greeting"
     * )
     * prompt(myPrompt)
     * ```
     *
     * @param prompt The prompt to add
     */
    public fun prompt(prompt: Prompt) {
        promptsList.add(prompt)
    }

    /**
     * Adds a prompt using a DSL builder.
     *
     * Example:
     * ```kotlin
     * prompt {
     *     name = "greeting"
     *     description = "A friendly greeting"
     *     arguments = listOf(
     *         PromptArgument(name = "userName", required = true)
     *     )
     * }
     * ```
     *
     * @param block Lambda for building the Prompt
     */
    public fun prompt(block: PromptBuilder.() -> Unit) {
        promptsList.add(PromptBuilder().apply(block).build())
    }

    @PublishedApi
    override fun build(): ListPromptsResult {
        require(promptsList.isNotEmpty()) {
            "At least one prompt is required. Use prompt() or prompt { } to add prompts."
        }

        return ListPromptsResult(
            prompts = promptsList.toList(),
            nextCursor = nextCursor,
            meta = meta,
        )
    }
}

/**
 * DSL builder for constructing [Prompt] instances.
 *
 * Used within [ListPromptsResultBuilder] to define individual prompts.
 *
 * ## Required
 * - [name] - The programmatic identifier for the prompt
 *
 * ## Optional
 * - [description] - Human-readable description
 * - [arguments] - List of arguments the prompt accepts
 * - [title] - Display name for the prompt
 * - [icons] - Icon representations for UIs
 * - [meta] - Metadata for the prompt
 *
 * @see Prompt
 * @see ListPromptsResultBuilder
 */
@McpDsl
public class PromptBuilder @PublishedApi internal constructor() {
    /**
     * The programmatic identifier for this prompt. Required.
     *
     * Example: `name = "greeting"`
     */
    public var name: String? = null

    /**
     * Human-readable description of what the prompt does.
     *
     * Example: `description = "A friendly greeting prompt"`
     */
    public var description: String? = null

    /**
     * Optional list of arguments the prompt accepts.
     *
     * Example:
     * ```kotlin
     * arguments = listOf(
     *     PromptArgument(name = "userName", required = true, description = "The user's name"),
     *     PromptArgument(name = "language", required = false, description = "Preferred language")
     * )
     * ```
     */
    public var arguments: List<PromptArgument>? = null

    /**
     * Optional display name for the prompt.
     *
     * Example: `title = "Friendly Greeting"`
     */
    public var title: String? = null

    /**
     * Optional list of icons for the prompt.
     *
     * Example:
     * ```kotlin
     * icons = listOf(
     *     Icon(url = "https://example.com/icon.png", size = "32x32", mimeType = "image/png")
     * )
     * ```
     */
    public var icons: List<Icon>? = null

    private var metaValue: JsonObject? = null

    /**
     * Sets metadata directly from a JsonObject.
     *
     * Example:
     * ```kotlin
     * meta(buildJsonObject {
     *     put("category", "greetings")
     * })
     * ```
     *
     * @param meta The metadata as a JsonObject
     */
    public fun meta(meta: JsonObject) {
        this.metaValue = meta
    }

    /**
     * Sets metadata using a DSL builder.
     *
     * Example:
     * ```kotlin
     * meta {
     *     put("category", "greetings")
     *     put("version", "1.0")
     * }
     * ```
     *
     * @param block Lambda for building the metadata JsonObject
     */
    public fun meta(block: JsonObjectBuilder.() -> Unit) {
        meta(buildJsonObject(block))
    }

    @PublishedApi
    internal fun build(): Prompt {
        val name = requireNotNull(name) {
            "Missing required field 'name'. Example: name = \"promptName\""
        }

        return Prompt(
            name = name,
            description = description,
            arguments = arguments,
            title = title,
            icons = icons,
            meta = metaValue,
        )
    }
}
