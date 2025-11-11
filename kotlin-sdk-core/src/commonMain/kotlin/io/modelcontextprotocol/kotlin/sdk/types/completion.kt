package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A request from the client to the server to ask for completion options.
 *
 * @property params The request parameters containing the argument to complete and its context.
 */
@Serializable
public data class CompleteRequest(override val params: CompleteRequestParams) : ClientRequest {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    public override val method: Method = Method.Defined.CompletionComplete

    /**
     * The argument's information for which completion options are requested.
     */
    public val argument: CompleteRequestParams.Argument
        get() = params.argument

    /**
     * A reference to either a prompt or resource template to complete within.
     */
    public val ref: Reference
        get() = params.ref

    /**
     * Additional, context for generating completions.
     */
    public val context: CompleteRequestParams.Context?
        get() = params.context

    public val meta: RequestMeta?
        get() = params.meta
}

/**
 * Parameters for the completion request.
 *
 * @property argument The argument's information for which completion options are requested.
 * @property ref A reference to either a prompt or resource template to complete within.
 * @property context Additional, optional context for generating completions.
 */
@Serializable
public data class CompleteRequestParams(
    val argument: Argument,
    val ref: Reference,
    val context: Context? = null,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams {
    /**
     * The argument for which completion is being requested.
     *
     * @property name The name of the argument being completed.
     * @property value The partial value of the argument to use for completion matching.
     *               This is typically the text the user has typed so far.
     */
    @Serializable
    public data class Argument(val name: String, val value: String)

    /**
     * Additional context to help generate more relevant completions.
     *
     * @property arguments Previously-resolved variables in a URI template or prompt.
     * These can be used to provide context-aware completions.
     * For example, if completing a file path, this might contain the repository or directory context.
     */
    @Serializable
    public data class Context(val arguments: Map<String, String>? = null)
}

/**
 * The server's response to a [CompleteRequest].
 *
 * Provides completion options for prompt or resource template arguments,
 * along with pagination information if there are many possible completions.
 *
 * @property completion The completion options and metadata about available results.
 * @property meta Optional metadata for this response. See MCP specification for details on _meta usage.
 */
@Serializable
public data class CompleteResult(
    public val completion: Completion,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult {

    /**
     * Completion options and pagination information.
     *
     * @property values An array of completion values. Must not exceed 100 items.
     * Each value represents a possible completion for the requested argument.
     * @property total The total number of completion options available.
     * This can exceed the number of values actually sent in the response,
     * indicating that pagination or filtering may be needed.
     * @property hasMore Indicates whether there are additional completion options beyond
     * those provided in the current response, even if the exact total is unknown.
     * Use this when the complete set of completions is too large to calculate upfront.
     */
    @Serializable
    public data class Completion(val values: List<String>, val total: Int? = null, val hasMore: Boolean? = null) {
        init {
            require(values.size <= 100) {
                "Completion 'values' must not exceed 100 items, got ${values.size}"
            }
        }
    }
}
