package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Creates a [CreateMessageRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [maxTokens][CreateMessageRequestBuilder.maxTokens] - Maximum number of tokens to generate
 * - [messages][CreateMessageRequestBuilder.messages] - List of conversation messages
 *
 * ## Optional
 * - [systemPrompt][CreateMessageRequestBuilder.systemPrompt] - System-level instructions
 * - [context][CreateMessageRequestBuilder.context] - Context inclusion settings
 * - [temperature][CreateMessageRequestBuilder.temperature] - Sampling temperature
 * - [stopSequences][CreateMessageRequestBuilder.stopSequences] - Sequences that stop generation
 * - [preferences][CreateMessageRequestBuilder.preferences] - Model selection preferences
 * - [metadata][CreateMessageRequestBuilder.metadata] - Additional metadata
 * - [meta][CreateMessageRequestBuilder.meta] - Request metadata
 *
 * Example:
 * ```kotlin
 * val request = CreateMessageRequest {
 *     maxTokens = 1000
 *     systemPrompt = "You are a helpful assistant"
 *     messages {
 *         user { "What is the capital of France?" }
 *         assistant { "The capital of France is Paris." }
 *         user { "What about Germany?" }
 *     }
 * }
 * ```
 *
 * Example with preferences:
 * ```kotlin
 * val request = CreateMessageRequest {
 *     maxTokens = 500
 *     temperature = 0.7
 *     preferences(
 *         hints = listOf("claude-3-sonnet"),
 *         intelligence = 0.8
 *     )
 *     messages {
 *         user { "Explain quantum computing" }
 *     }
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the create message request
 * @return A configured [CreateMessageRequest] instance
 * @see CreateMessageRequestBuilder
 * @see CreateMessageRequest
 */
@ExperimentalMcpApi
public inline operator fun CreateMessageRequest.Companion.invoke(
    block: CreateMessageRequestBuilder.() -> Unit,
): CreateMessageRequest = CreateMessageRequestBuilder().apply(block).build()

/**
 * Creates a [CreateMessageRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [maxTokens][CreateMessageRequestBuilder.maxTokens] - Maximum number of tokens to generate
 * - [messages][CreateMessageRequestBuilder.messages] - List of conversation messages
 *
 * ## Optional
 * - [systemPrompt][CreateMessageRequestBuilder.systemPrompt] - System-level instructions
 * - [context][CreateMessageRequestBuilder.context] - Context inclusion settings
 * - [temperature][CreateMessageRequestBuilder.temperature] - Sampling temperature
 * - [stopSequences][CreateMessageRequestBuilder.stopSequences] - Sequences that stop generation
 * - [preferences][CreateMessageRequestBuilder.preferences] - Model selection preferences
 * - [metadata][CreateMessageRequestBuilder.metadata] - Additional metadata
 * - [meta][CreateMessageRequestBuilder.meta] - Request metadata
 *
 * Example:
 * ```kotlin
 * val request = buildCreateMessageRequest {
 *     maxTokens = 1000
 *     systemPrompt = "You are a helpful assistant"
 *     messages {
 *         user { "What is the capital of France?" }
 *         assistant { "The capital of France is Paris." }
 *         user { "What about Germany?" }
 *     }
 * }
 * ```
 *
 * Example with preferences:
 * ```kotlin
 * val request = buildCreateMessageRequest {
 *     maxTokens = 500
 *     temperature = 0.7
 *     preferences(
 *         hints = listOf("claude-3-sonnet"),
 *         intelligence = 0.8
 *     )
 *     messages {
 *         user { "Explain quantum computing" }
 *     }
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the create message request
 * @return A configured [CreateMessageRequest] instance
 * @see CreateMessageRequestBuilder
 * @see CreateMessageRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
@Deprecated(
    message = "Use CreateMessageRequest { } instead",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("CreateMessageRequest{apply(block)}"),
)
public inline fun buildCreateMessageRequest(block: CreateMessageRequestBuilder.() -> Unit): CreateMessageRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return CreateMessageRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [CreateMessageRequest] instances.
 *
 * This builder creates LLM sampling requests with conversation history.
 *
 * ## Required
 * - [maxTokens] - Maximum number of tokens to generate
 * - [messages] - List of conversation messages
 *
 * ## Optional
 * - [systemPrompt] - System-level instructions
 * - [context] - Context inclusion settings
 * - [temperature] - Sampling temperature (0.0-1.0)
 * - [stopSequences] - Sequences that stop generation
 * - [preferences] - Model selection preferences
 * - [metadata] - Additional metadata
 * - [meta] - Request metadata
 *
 * @see CreateMessageRequest
 * @see CreateMessageRequest
 */
@McpDsl
public class CreateMessageRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    /** Maximum number of tokens to generate. This is a required field. */
    public var maxTokens: Int? = null

    /** Optional system-level instructions for the model. */
    public var systemPrompt: String? = null

    /** Optional context inclusion settings. */
    public var context: IncludeContext? = null

    /** Optional sampling temperature (0.0-1.0). Lower values are more deterministic. */
    public var temperature: Double? = null

    /** Optional sequences that will stop generation when encountered. */
    public var stopSequences: List<String>? = null

    private var messages: List<SamplingMessage>? = null
    private var preferences: ModelPreferences? = null
    private var metadata: JsonObject? = null

    /**
     * Sets messages directly from a list.
     *
     * Example:
     * ```kotlin
     * messages(listOf(
     *     SamplingMessage(Role.User, TextContent("Hello")),
     *     SamplingMessage(Role.Assistant, TextContent("Hi!"))
     * ))
     * ```
     */
    public fun messages(messages: List<SamplingMessage>) {
        this.messages = messages
    }

    /**
     * Sets messages using a DSL builder.
     *
     * This is the recommended way to define conversation messages.
     *
     * Example:
     * ```kotlin
     * messages {
     *     user { "What is 2+2?" }
     *     assistant { "2+2 equals 4." }
     *     user { "What about 3+3?" }
     * }
     * ```
     */
    public fun messages(block: SamplingMessageBuilder.() -> Unit) {
        this.messages = SamplingMessageBuilder().apply(block).build()
    }

    /**
     * Sets model preferences directly.
     */
    public fun preferences(value: ModelPreferences) {
        this.preferences = value
    }

    /**
     * Sets model selection preferences using individual parameters.
     *
     * Example:
     * ```kotlin
     * preferences(
     *     hints = listOf("claude-3-sonnet"),
     *     intelligence = 0.9,
     *     speed = 0.5
     * )
     * ```
     *
     * @param hints Model name hints for selection
     * @param cost Cost optimization priority (0.0-1.0)
     * @param speed Speed optimization priority (0.0-1.0)
     * @param intelligence Intelligence optimization priority (0.0-1.0)
     */
    public fun preferences(
        hints: List<String>? = null,
        cost: Double? = null,
        speed: Double? = null,
        intelligence: Double? = null,
    ) {
        this.preferences = ModelPreferences(
            hints = hints?.map { ModelHint(it) },
            costPriority = cost,
            speedPriority = speed,
            intelligencePriority = intelligence,
        )
    }

    /**
     * Sets additional metadata using a DSL builder.
     *
     * Example:
     * ```kotlin
     * metadata {
     *     put("userId", "123")
     *     put("sessionId", "abc")
     * }
     * ```
     */
    public fun metadata(block: JsonObjectBuilder.() -> Unit) {
        this.metadata = buildJsonObject(block)
    }

    @PublishedApi
    override fun build(): CreateMessageRequest {
        val maxTokens = requireNotNull(maxTokens) {
            "Missing required field 'maxTokens'. Example: maxTokens = 1000"
        }
        val messages = requireNotNull(messages) {
            "Missing required field 'messages'. Use messages { user { \"text\" } }"
        }

        val params = CreateMessageRequestParams(
            maxTokens = maxTokens,
            messages = messages,
            modelPreferences = preferences,
            systemPrompt = systemPrompt,
            includeContext = context,
            temperature = temperature,
            stopSequences = stopSequences,
            metadata = metadata,
            meta = meta,
        )
        return CreateMessageRequest(params)
    }
}

/**
 * DSL builder for constructing lists of [SamplingMessage] instances.
 *
 * This builder creates a conversation history for LLM sampling requests.
 *
 * Example:
 * ```kotlin
 * messages {
 *     user { "Hello!" }
 *     assistant { "Hi! How can I help?" }
 *     user { "What's 2+2?" }
 * }
 * ```
 *
 * @see SamplingMessage
 * @see CreateMessageRequestBuilder.messages
 */
@McpDsl
public class SamplingMessageBuilder @PublishedApi internal constructor() {
    private val messages = mutableListOf<SamplingMessage>()

    /**
     * Adds a user message with the specified content.
     *
     * Example:
     * ```kotlin
     * user(TextContent("Hello"))
     * ```
     */
    public fun user(content: MediaContent) {
        messages.add(SamplingMessage(Role.User, content))
    }

    /**
     * Adds an assistant message with the specified content.
     *
     * Example:
     * ```kotlin
     * assistant(TextContent("Hi there!"))
     * ```
     */
    public fun assistant(content: MediaContent) {
        messages.add(SamplingMessage(Role.Assistant, content))
    }

    @PublishedApi
    internal fun build(): List<SamplingMessage> = messages.toList() // Defensive copy
}

/**
 * Adds a user message with simple text content.
 *
 * Example:
 * ```kotlin
 * messages {
 *     user { "What is the weather today?" }
 * }
 * ```
 */
@ExperimentalMcpApi
public fun SamplingMessageBuilder.user(block: () -> String): Unit = this.user(TextContent(text = block()))

/**
 * Adds a user message with text content using a DSL builder.
 *
 * Example:
 * ```kotlin
 * messages {
 *     userText {
 *         text = "Important message"
 *         annotations(priority = 1.0)
 *     }
 * }
 * ```
 */
@ExperimentalMcpApi
public fun SamplingMessageBuilder.userText(block: TextContentBuilder.() -> Unit): Unit =
    this.user(TextContentBuilder().apply(block).build())

/**
 * Adds a user message with image content.
 *
 * Example:
 * ```kotlin
 * messages {
 *     userImage {
 *         data = base64ImageData
 *         mimeType = "image/png"
 *     }
 * }
 * ```
 */
@ExperimentalMcpApi
public fun SamplingMessageBuilder.userImage(block: ImageContentBuilder.() -> Unit): Unit =
    this.user(ImageContentBuilder().apply(block).build())

/**
 * Adds a user message with audio content.
 *
 * Example:
 * ```kotlin
 * messages {
 *     userAudio {
 *         data = base64AudioData
 *         mimeType = "audio/wav"
 *     }
 * }
 * ```
 */
@ExperimentalMcpApi
public fun SamplingMessageBuilder.userAudio(block: AudioContentBuilder.() -> Unit): Unit =
    this.user(AudioContentBuilder().apply(block).build())

/**
 * Adds an assistant message with simple text content.
 *
 * Example:
 * ```kotlin
 * messages {
 *     assistant { "The weather is sunny today." }
 * }
 * ```
 */
@ExperimentalMcpApi
public fun SamplingMessageBuilder.assistant(block: () -> String): Unit = this.assistant(TextContent(text = block()))

/**
 * Adds an assistant message with text content using a DSL builder.
 *
 * Example:
 * ```kotlin
 * messages {
 *     assistantText {
 *         text = "Here's the answer"
 *         annotations(priority = 0.8)
 *     }
 * }
 * ```
 */
@ExperimentalMcpApi
public fun SamplingMessageBuilder.assistantText(block: TextContentBuilder.() -> Unit): Unit =
    this.assistant(TextContentBuilder().apply(block).build())

/**
 * Adds an assistant message with image content.
 *
 * Example:
 * ```kotlin
 * messages {
 *     assistantImage {
 *         data = generatedImageData
 *         mimeType = "image/jpeg"
 *     }
 * }
 * ```
 */
@ExperimentalMcpApi
public fun SamplingMessageBuilder.assistantImage(block: ImageContentBuilder.() -> Unit): Unit =
    this.assistant(ImageContentBuilder().apply(block).build())

/**
 * Adds an assistant message with audio content.
 *
 * Example:
 * ```kotlin
 * messages {
 *     assistantAudio {
 *         data = generatedAudioData
 *         mimeType = "audio/mpeg"
 *     }
 * }
 * ```
 */
@ExperimentalMcpApi
public fun SamplingMessageBuilder.assistantAudio(block: AudioContentBuilder.() -> Unit): Unit =
    this.assistant(AudioContentBuilder().apply(block).build())
