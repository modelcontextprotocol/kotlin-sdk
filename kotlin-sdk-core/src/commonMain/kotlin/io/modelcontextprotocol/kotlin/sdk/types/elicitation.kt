package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A request from the server to elicit additional information from the user via the client.
 *
 * This request type allows servers to prompt users for structured input through forms
 * or dialogs presented by the client. The server defines a schema for the requested data,
 * and the client presents an appropriate UI to collect this information.
 *
 * @property params The elicitation parameters including the message and requested schema.
 */
@Serializable
@SerialName("elicitation/create")
public data class ElicitRequest(override val params: ElicitRequestParams) : ServerRequest

/**
 * Parameters for an elicitation/create request.
 *
 * @property message The message to present to the user. This should clearly explain
 * what information is being requested and why.
 * @property requestedSchema A restricted subset of JSON Schema defining the structure
 * of the requested data. Only top-level properties are allowed,
 * without nesting.
 * @property meta Optional metadata for this request. May include a progressToken for
 * out-of-band progress notifications.
 */
@Serializable
public data class ElicitRequestParams(
    val message: String,
    val requestedSchema: RequestedSchema,
    override val meta: RequestMeta? = null,
) : RequestParams {

    /**
     * A restricted JSON Schema for elicitation requests.
     *
     * Only supports top-level primitive properties without nesting. Each property
     * represents a field in the form or dialog presented to the user.
     *
     * @property properties A map of property names to their schema definitions.
     * Each property must be a primitive type (string, number, boolean).
     * @property required Optional list of property names that must be provided by the user.
     * If omitted, all fields are considered optional.
     * @property type Always "object" for elicitation schemas.
     */
    @Serializable
    public data class RequestedSchema(
        val properties: JsonObject, // TODO: PrimitiveSchema???
        val required: List<String>? = null,
    ) {
        @OptIn(ExperimentalSerializationApi::class)
        @EncodeDefault
        val type: String = "object"
    }
}

/**
 * The client's response to an [ElicitRequest].
 *
 * Represents the user's action and, if accepted, the submitted form data.
 *
 * @property action The user action in response to the elicitation prompt.
 * @property content The submitted form data, only present when [action] is [Action.Accept].
 * Contains values matching the requested schema, where keys correspond
 * to property names and values are primitives (string, number, or boolean).
 * @property meta Optional metadata for this response.
 * @throws IllegalArgumentException if content is provided with a non-accept action.
 */
@Serializable
public data class ElicitResult(
    val action: Action,
    val content: JsonObject? = null,
    override val meta: JsonObject? = null,
) : ClientResult {

    init {
        require(action == Action.Accept || content == null) {
            "Content can only be provided when action is 'accept', got action=$action with content"
        }
    }

    /**
     * The user's response action to an elicitation request.
     *
     * @property Accept User submitted the form/confirmed the action. Content will be provided.
     * @property Decline User explicitly declined the action. No content provided.
     * @property Cancel User dismissed the dialog without making an explicit choice. No content provided.
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
