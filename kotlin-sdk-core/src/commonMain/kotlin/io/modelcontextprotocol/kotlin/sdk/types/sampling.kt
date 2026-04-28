package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmInline

/**
 * Hints to use for model selection.
 */
@Serializable
public data class ModelHint(
    /**
     * A hint for a model name.
     */
    val name: String?,
)

/**
 * The server's preferences for model selection, requested of the client during sampling.
 *
 * Because LLMs can vary along multiple dimensions, choosing the "best" model is rarely straightforward.
 * Different models excel in different areas—some are faster but less capable, others are more capable
 * but more expensive, and so on. This interface allows servers to express their priorities across
 * multiple dimensions to help clients make an appropriate selection for their use case.
 *
 * **Important:** These preferences are always advisory. The client MAY ignore them completely.
 * It is also up to the client to decide how to interpret these preferences and how to balance
 * them against other considerations.
 *
 * @property hints Optional hints to use for model selection.
 * If multiple hints are specified, the client MUST evaluate them in order
 * (such that the first match is taken).
 * The client SHOULD prioritize these hints over the numeric priorities,
 * but MAY still use the priorities to select from ambiguous matches.
 * @property costPriority How much to prioritize cost when selecting a model.
 * A value of 0.0 means cost is not important, while a value of 1.0 means
 * cost is the most important factor.
 * Should be between 0.0 and 1.0.
 * @property speedPriority How much to prioritize sampling speed (latency) when selecting a model.
 * A value of 0.0 means speed is not important, while a value of 1.0 means
 * speed is the most important factor.
 * Should be between 0.0 and 1.0.
 * @property intelligencePriority How much to prioritize intelligence and capabilities when selecting a model.
 * A value of 0.0 means intelligence is not important, while a value of 1.0
 * means intelligence is the most important factor.
 * Should be between 0.0 and 1.0.
 */
@Serializable
public data class ModelPreferences(
    val hints: List<ModelHint>? = null,
    val costPriority: Double? = null,
    val speedPriority: Double? = null,
    val intelligencePriority: Double? = null,
) {
    init {
        require(costPriority == null || costPriority in 0.0..1.0) {
            "costPriority must be in 0.0 <= x <= 1.0 value range"
        }

        require(speedPriority == null || speedPriority in 0.0..1.0) {
            "costPriority must be in 0.0 <= x <= 1.0 value range"
        }

        require(intelligencePriority == null || intelligencePriority in 0.0..1.0) {
            "intelligencePriority must be in 0.0 <= x <= 1.0 value range"
        }
    }
}

/**
 * Describes a message issued to or received from an LLM API.
 *
 * Used in sampling requests to provide conversation context and history to the LLM.
 *
 * Under SEP-1577, [content] is a list of [SamplingMessageContent] blocks rather than a
 * single block. On the wire, a list of size 1 is serialised as a single object (wire-compatible
 * with pre-SEP single-block content); a list of size 2+ is serialised as a JSON array.
 *
 * @property role The role of the message sender (user, assistant, or system).
 * @property content The content blocks of the message; MUST contain at least one block.
 * @property meta Optional metadata reserved by MCP for clients and servers.
 */
@Serializable
public data class SamplingMessage(
    val role: Role,
    @Serializable(with = SamplingContentSerializer::class)
    val content: List<SamplingMessageContent>,
    @SerialName("_meta")
    val meta: JsonObject? = null,
) {
    /**
     * Convenience constructor for a single-block message. Wraps [content] in a
     * singleton list so call sites can write `SamplingMessage(Role.User, TextContent("hi"))`
     * without the explicit `listOf(...)`.
     */
    public constructor(
        role: Role,
        content: SamplingMessageContent,
        meta: JsonObject? = null,
    ) : this(role, listOf(content), meta)
}

// ============================================================================
// sampling/createMessage
// ============================================================================

/**
 * A request from the server to sample an LLM via the client.
 *
 * The client has full discretion over which model to select. The client should also
 * inform the user before beginning sampling, to allow them to inspect the request
 * (human in the loop) and decide whether to approve it.
 *
 * **Important:** This is a request from **server to client**, not client to server.
 * The server is asking the client to use its LLM to generate a completion.
 *
 * **Human-in-the-loop:** Clients should:
 * 1. Show the sampling request to the user before executing it
 * 2. Allow the user to approve or reject the request
 * 3. Show the generated response to the user before sending it back to the server
 *
 * @property params The parameters for the sampling request, including messages and model preferences.
 */
@Serializable
public data class CreateMessageRequest(override val params: CreateMessageRequestParams) : ServerRequest {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val method: Method = Method.Defined.SamplingCreateMessage

    /**
     * The requested maximum number of tokens to sample (to prevent runaway completions).
     */
    public val maxTokens: Int
        get() = params.maxTokens

    /**
     * The messages to use as context for sampling.
     */
    public val messages: List<SamplingMessage>
        get() = params.messages

    /**
     * The server's preferences for which model to select.
     */
    public val modelPreferences: ModelPreferences?
        get() = params.modelPreferences

    /**
     * An optional system prompt the server wants to use for sampling.
     */
    public val systemPrompt: String?
        get() = params.systemPrompt

    /**
     * A request to include context from one or more MCP servers (including the caller), to be attached to the prompt.
     */
    public val includeContext: IncludeContext?
        get() = params.includeContext

    /**
     * Temperature parameter for sampling (typically 0.0-2.0).
     */
    public val temperature: Double?
        get() = params.temperature

    /**
     * List of sequences that will stop generation if encountered.
     */
    public val stopSequences: List<String>?
        get() = params.stopSequences

    /**
     * Metadata to pass through to the LLM provider. The format of this metadata is provider-specific.
     */
    public val metadata: JsonObject?
        get() = params.metadata

    /**
     * Metadata for this request. May include a progressToken for out-of-band progress notifications.
     */
    public val meta: RequestMeta?
        get() = params.meta
}

/**
 * Parameters for a sampling/createMessage request.
 *
 * @property maxTokens The requested maximum number of tokens to sample (to prevent runaway completions).
 * The client MAY choose to sample fewer tokens than the requested maximum.
 * @property messages The messages to use as context for sampling. Typically includes conversation history
 * and the current user message.
 * @property modelPreferences The server's preferences for which model to select.
 * The client MAY ignore these preferences and choose any model.
 * @property systemPrompt An optional system prompt the server wants to use for sampling.
 * The client MAY modify or omit this prompt.
 * @property includeContext A request to include context from one or more MCP servers (including the caller),
 * to be attached to the prompt. The client MAY ignore this request.
 * - `none`: Don't include any server context
 * - `thisServer`: Include context only from the requesting server
 * - `allServers`: Include context from all connected MCP servers
 * @property temperature Optional temperature parameter for sampling (typically 0.0-2.0).
 * Higher values make output more random, lower values make it more deterministic.
 * @property stopSequences Optional list of sequences that will stop generation if encountered.
 * @property metadata Optional metadata to pass through to the LLM provider.
 * The format of this metadata is provider-specific.
 * @property tools Optional list of tools the model may use during generation.
 * The client MUST return an error if this field is present but the client did not advertise
 * [ClientCapabilities.Sampling.tools].
 * @property toolChoice Optional policy controlling how the model uses the provided [tools].
 * The client MUST return an error if this field is present but the client did not advertise
 * [ClientCapabilities.Sampling.tools].
 * @property meta Optional metadata for this request. May include a progressToken for
 * out-of-band progress notifications.
 */
@Serializable
public data class CreateMessageRequestParams(
    val maxTokens: Int,
    val messages: List<SamplingMessage>,
    val modelPreferences: ModelPreferences? = null,
    val systemPrompt: String? = null,
    val includeContext: IncludeContext? = null,
    val temperature: Double? = null,
    val stopSequences: List<String>? = null,
    val metadata: JsonObject? = null,
    val tools: List<Tool>? = null,
    val toolChoice: ToolChoice? = null,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams

/**
 * Specifies which MCP server context to include in the sampling request.
 */
@Serializable
public enum class IncludeContext {
    /** Don't include any server context */
    @SerialName("none")
    None,

    /** Include context only from the requesting server */
    @SerialName("thisServer")
    ThisServer,

    /** Include context from all connected MCP servers */
    @SerialName("allServers")
    AllServers,
}

/**
 * The client's response to a [CreateMessageRequest] from the server.
 *
 * The client should inform the user before returning the sampled message, to allow them
 * to inspect the response (human in the loop) and decide whether to allow the server to see it.
 *
 * @property role The role of the message sender. Typically [Role.Assistant] for LLM-generated responses.
 * @property content The generated content blocks; at least one block is required.
 * @property model The name of the model that generated the message (e.g., "claude-3-opus-20240229").
 * @property stopReason The reason why sampling stopped, if known.
 * Common values: [StopReason.EndTurn], [StopReason.StopSequence], [StopReason.MaxTokens],
 * [StopReason.ToolUse].
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class CreateMessageResult(
    val role: Role,
    @Serializable(with = SamplingContentSerializer::class)
    val content: List<SamplingMessageContent>,
    val model: String,
    val stopReason: StopReason? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ClientResult {
    /**
     * Convenience constructor for a single-block response. Wraps [content] in a
     * singleton list so call sites can write
     * `CreateMessageResult(Role.Assistant, TextContent("ok"), "model-name")`
     * without the explicit `listOf(...)`.
     */
    public constructor(
        role: Role,
        content: SamplingMessageContent,
        model: String,
        stopReason: StopReason? = null,
        meta: JsonObject? = null,
    ) : this(role, listOf(content), model, stopReason, meta)
}

/**
 * The reason why the LLM stopped generating tokens.
 *
 * @property value the string representation of this stop reason
 */
@JvmInline
@Serializable
public value class StopReason(public val value: String) {
    /**
     * @property EndTurn generation ended naturally
     * @property StopSequence a stop sequence was encountered
     * @property MaxTokens the maximum token limit was reached
     * @property ToolUse the assistant issued a tool call (see SEP-1577)
     */
    public companion object {
        public val EndTurn: StopReason = StopReason("endTurn")
        public val StopSequence: StopReason = StopReason("stopSequence")
        public val MaxTokens: StopReason = StopReason("maxTokens")
        public val ToolUse: StopReason = StopReason("toolUse")
    }
}

/**
 * Controls tool-selection behaviour for [CreateMessageRequest] under SEP-1577.
 *
 * @property mode Tool-use policy:
 *  - [Mode.Auto]: the model decides whether to use tools (default when [mode] is absent)
 *  - [Mode.Required]: the model MUST use at least one tool before completing
 *  - [Mode.None]: the model MUST NOT use any tool
 */
@Serializable
public data class ToolChoice(public val mode: Mode? = null) {
    /**
     * Allowed values of [ToolChoice.mode] per SEP-1577.
     */
    @Serializable
    public enum class Mode {
        @SerialName("auto")
        Auto,

        @SerialName("required")
        Required,

        @SerialName("none")
        None,
    }
}
