@file:OptIn(ExperimentalSerializationApi::class)

package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Describes an argument that a prompt can accept.
 *
 * Arguments allow prompts to be templated with user-provided values.
 *
 * @property name The programmatic identifier for this argument.
 * Intended for logical use and API identification. If [title] is not provided,
 * this should be used as a fallback display name.
 * @property description A human-readable description of the argument, explaining what it's for
 * and what values are expected.
 * @property required Whether this argument must be provided when using the prompt.
 * If true, the client must supply a value for this argument.
 * @property title Optional human-readable display name for this argument.
 * Intended for UI and end-user contexts, optimized to be easily understood
 * even by those unfamiliar with domain-specific terminology.
 * If not provided, [name] should be used for display purposes.
 */
@Serializable
public data class PromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean? = null,
    val title: String? = null,
)

/**
 * A prompt or prompt template that the server offers.
 *
 * Prompts can be static messages or templates that accept arguments for dynamic content generation.
 *
 * @property name The programmatic identifier for this prompt.
 * Intended for logical use and API identification. If [title] is not provided,
 * this should be used as a fallback display name.
 * @property description An optional description of what this prompt provides and when to use it.
 * @property arguments Optional list of arguments to use for templating the prompt.
 * If present, the prompt is a template that requires these arguments to be filled in.
 * @property title Optional human-readable display name for this prompt.
 * Intended for UI and end-user contexts, optimized to be easily understood
 * even by those unfamiliar with domain-specific terminology.
 * If not provided, [name] should be used for display purposes.
 * @property icons Optional set of sized icons that clients can display in their user interface.
 * Clients MUST support at least PNG and JPEG formats.
 * Clients SHOULD also support SVG and WebP formats.
 * @property meta Optional metadata for this prompt.
 */
@Serializable
public data class Prompt(
    val name: String,
    val description: String? = null,
    val arguments: List<PromptArgument>? = null,
    val title: String? = null,
    val icons: List<Icon>? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : WithMeta

/**
 * Describes a message returned as part of a prompt.
 *
 * This is similar to SamplingMessage, but also supports the embedding of resources from the MCP server.
 *
 * @property role The role of the message sender (e.g., user, assistant, or system).
 * @property content The content of the message. Can include text, images, resources, and other content types.
 */
@Serializable
public data class PromptMessage(val role: Role, val content: ContentBlock)

/**
 * Identifies a prompt by reference.
 *
 * Used in completion requests and other contexts where a prompt needs to be referenced
 * without including its full definition.
 *
 * @property name The programmatic identifier of the prompt being referenced.
 * Intended for logical use and API identification. If [title] is not provided,
 * this should be used as a fallback display name.
 * @property title Optional human-readable display name for the referenced prompt.
 * Intended for UI and end-user contexts, optimized to be easily understood
 * even by those unfamiliar with domain-specific terminology.
 * If not provided, [name] should be used for display purposes.
 */
@Serializable
public data class PromptReference(val name: String, val title: String? = null) : Reference {
    @EncodeDefault
    public override val type: ReferenceType = ReferenceType.Prompt
}

// ============================================================================
// prompts/get
// ============================================================================

/**
 * Used by the client to get a prompt provided by the server.
 *
 * Prompts can be static text or templates that accept arguments. When arguments
 * are provided, the server will substitute them into the prompt template before
 * returning the result.
 *
 * @property params The parameters specifying which prompt to retrieve and any template arguments.
 */
@Serializable
public data class GetPromptRequest(override val params: GetPromptRequestParams) : ClientRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.PromptsGet

    @Deprecated(
        message = "Use constructor with GetPromptRequestParams instead",
        replaceWith = ReplaceWith("GetPromptRequest(GetPromptRequestParams(name, arguments, meta))"),
        level = DeprecationLevel.ERROR,
    )
    public constructor(
        name: String,
        arguments: Map<String, String>? = null,
        meta: RequestMeta? = null,
    ) : this(
        GetPromptRequestParams(
            name = name,
            arguments = arguments,
            meta = meta,
        ),
    )

    /**
     * The name of the prompt or prompt template to retrieve.
     */
    public val name: String
        get() = params.name

    /**
     * Arguments to use for templating the prompt.
     * Keys are argument names, values are the argument values to substitute.
     */
    public val arguments: Map<String, String>?
        get() = params.arguments

    /**
     * Metadata for this request. May include a progressToken for out-of-band progress notifications.
     */
    public val meta: RequestMeta?
        get() = params.meta
}

/**
 * Parameters for a prompts/get request.
 *
 * @property name The name of the prompt or prompt template to retrieve.
 * @property arguments Optional arguments to use for templating the prompt.
 *                    Keys are argument names, values are the argument values to substitute.
 * @property meta Optional metadata for this request. May include a progressToken for
 *                out-of-band progress notifications.
 */
@Serializable
public data class GetPromptRequestParams(
    val name: String,
    val arguments: Map<String, String>? = null,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams

/**
 * The server's response to a [GetPromptRequest] from the client.
 *
 * Contains the prompt's messages and optional description. If the prompt was a template,
 * the messages will have arguments already substituted.
 *
 * @property messages The list of messages that make up this prompt.
 *                   Each message has a role (e.g., "user", "assistant") and content.
 * @property description An optional description for the prompt, explaining its purpose or usage.
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class GetPromptResult(
    val messages: List<PromptMessage>,
    val description: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult

// ============================================================================
// prompts/list
// ============================================================================

/**
 * Sent from the client to request a list of prompts and prompt templates the server has.
 *
 * This request supports pagination through the cursor parameter. If the server has many
 * prompts, it may return a subset along with a cursor to fetch the next page.
 *
 * @property params Optional pagination parameters to control which page of results to return.
 */
@Serializable
public data class ListPromptsRequest(override val params: PaginatedRequestParams? = null) :
    ClientRequest,
    PaginatedRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.PromptsList

    /**
     * Secondary constructor for creating a [ListPromptsRequest] instance
     * using optional cursor and metadata parameters.
     *
     * This constructor simplifies the creation of the [ListPromptsRequest] by allowing a cursor
     * and metadata to be provided.
     *
     * @param cursor Optional cursor string to specify the starting point of the paginated request.
     * @param meta Optional metadata associated with the request.
     */
    @Deprecated(
        message = "Use constructor with ListPromptsParams instead",
        replaceWith = ReplaceWith("ListPromptsRequest(ListPromptsParams(name, arguments, meta))"),
        level = DeprecationLevel.ERROR,
    )
    public constructor(
        cursor: String?,
        meta: RequestMeta? = null,
    ) : this(paginatedRequestParams(cursor, meta))
}

/**
 * The server's response to a [ListPromptsRequest] from the client.
 *
 * Returns the available prompts and prompt templates, along with pagination information
 * if there are more results available.
 *
 * @property prompts The list of available prompts. Each prompt includes its name, optional description,
 *                  and information about any arguments it accepts.
 * @property nextCursor An opaque token representing the pagination position after the last returned result.
 *                     If present, there may be more results available. The client can pass this token
 *                     in a subsequent request to fetch the next page.
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class ListPromptsResult(
    val prompts: List<Prompt>,
    override val nextCursor: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult,
    PaginatedResult
