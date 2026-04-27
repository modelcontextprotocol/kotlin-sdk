package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents an `elicitation/create` request from the server to the client.
 *
 * Supports two modes: form mode ([ElicitRequestFormParams]) for collecting structured data
 * in-band, and URL mode ([ElicitRequestURLParams]) for directing the user to an external URL.
 *
 * @property params The elicitation parameters — either form or URL mode.
 */
@Serializable
public data class ElicitRequest(override val params: ElicitRequestParams) : ServerRequest {
    @EncodeDefault
    public override val method: Method = Method.Defined.ElicitationCreate

    /**
     * The message to present to the user. This should clearly explain what information is being requested and why.
     */
    public val message: String
        get() = params.message

    /**
     * A restricted subset of JSON Schema defining the structure of the requested data.
     */
    @Deprecated(
        "Use (params as ElicitRequestFormParams).requestedSchema",
        ReplaceWith("(params as ElicitRequestFormParams).requestedSchema"),
        DeprecationLevel.WARNING,
    )
    public val requestedSchema: ElicitRequestParams.RequestedSchema?
        get() = (params as? ElicitRequestFormParams)?.requestedSchema

    /**
     * Metadata for this request. May include a progressToken for out-of-band progress notifications.
     */
    public val meta: RequestMeta?
        get() = params.meta
}

/**
 * Represents the parameters for an `elicitation/create` request.
 *
 * Implementations: [ElicitRequestFormParams], [ElicitRequestURLParams].
 */
@Serializable(with = ElicitRequestParamsSerializer::class)
public sealed interface ElicitRequestParams : RequestParams {
    /** The message to present to the user describing what information is being requested. */
    public val message: String

    /**
     * A restricted JSON Schema for elicitation requests.
     *
     * Only supports top-level primitive properties without nesting. Each property
     * represents a field in the form or dialog presented to the user.
     *
     * @property schema Optional URI to a JSON Schema definition.
     * @property properties A map of property names to their schema definitions.
     * Each property must be a primitive type (string, number, boolean).
     * @property required Optional list of property names that must be provided by the user.
     * If omitted, all fields are considered optional.
     */
    @Serializable
    public data class RequestedSchema(
        @SerialName($$"$schema")
        val schema: String? = null,
        val properties: Map<String, PrimitiveSchemaDefinition>,
        val required: List<String>? = null,
    ) {
        /** Always "object" for elicitation schemas. */
        @EncodeDefault
        val type: String = "object"
    }
}

/**
 * Creates an [ElicitRequestFormParams] for backwards compatibility.
 *
 * @param message The message to present to the user.
 * @param requestedSchema The JSON Schema for the requested data.
 * @param meta Optional request metadata.
 * @return A configured [ElicitRequestFormParams] instance.
 */
@Deprecated(
    "Use ElicitRequestFormParams instead",
    ReplaceWith("ElicitRequestFormParams(message, requestedSchema = requestedSchema, meta = meta)"),
    DeprecationLevel.WARNING,
)
@Suppress("FunctionName")
public fun ElicitRequestParams(
    message: String,
    requestedSchema: ElicitRequestParams.RequestedSchema,
    meta: RequestMeta? = null,
): ElicitRequestFormParams = ElicitRequestFormParams(
    message = message,
    requestedSchema = requestedSchema,
    meta = meta,
)

/**
 * Represents form mode parameters for an `elicitation/create` request.
 *
 * Collects non-sensitive structured data from the user via a form presented by the client.
 *
 * @property message The message to present to the user describing what information is being requested.
 * @property task If specified, the caller is requesting task-augmented execution. The request
 *   will return a [CreateTaskResult] immediately, and the actual result can be retrieved
 *   later via `tasks/result`.
 * @property requestedSchema A restricted subset of JSON Schema. Only top-level properties
 *   are allowed, without nesting.
 * @property meta Optional metadata. May include a progressToken for out-of-band progress notifications.
 */
@Serializable
public data class ElicitRequestFormParams(
    override val message: String,
    val task: TaskMetadata? = null,
    val requestedSchema: ElicitRequestParams.RequestedSchema,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : ElicitRequestParams {
    /** The elicitation mode discriminator, always `"form"`. */
    @EncodeDefault
    public val mode: String = "form"
}

/**
 * Represents URL mode parameters for an `elicitation/create` request.
 *
 * Directs the user to an external URL for out-of-band interactions (e.g., OAuth flows,
 * payment processing, or entering sensitive credentials) that must not pass through the MCP client.
 *
 * @property message The message explaining why the interaction is needed.
 * @property elicitationId A unique identifier for this elicitation. The client MUST treat
 *   this ID as an opaque value.
 * @property url The URL that the user should navigate to.
 * @property task If specified, the caller is requesting task-augmented execution. The request
 *   will return a [CreateTaskResult] immediately, and the actual result can be retrieved
 *   later via `tasks/result`.
 * @property meta Optional metadata. May include a progressToken for out-of-band progress notifications.
 */
@Serializable
public data class ElicitRequestURLParams(
    override val message: String,
    val elicitationId: String,
    val url: String,
    val task: TaskMetadata? = null,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : ElicitRequestParams {
    /** The elicitation mode discriminator, always `"url"`. */
    @EncodeDefault
    public val mode: String = "url"
}

/**
 * Represents the client's response to an [ElicitRequest].
 *
 * @property action The user action in response to the elicitation.
 * @property content The submitted form data, only present when [action] is [Action.Accept]
 *   and mode was form. Contains values matching the requested schema. Omitted for
 *   URL mode responses.
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class ElicitResult(
    val action: Action,
    val content: JsonObject? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ClientResult {

    init {
        require(action == Action.Accept || content == null) {
            "Content can only be provided when action is 'accept', got action=$action with content"
        }
    }

    /**
     * The user's response action to an elicitation request.
     */
    @Serializable
    public enum class Action {
        /** User submitted the form/confirmed the action */
        @SerialName("accept")
        Accept,

        /** User explicitly declined the action */
        @SerialName("decline")
        Decline,

        /** User dismissed without making an explicit choice */
        @SerialName("cancel")
        Cancel,
    }
}
