package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlin.jvm.JvmInline

/**
 * Metadata attached to a request's `_meta` field.
 *
 * @property json the raw JSON object containing the metadata
 */
@JvmInline
@Serializable
public value class RequestMeta(public val json: JsonObject) {
    /** Optional progress token for tracking request progress. */
    public val progressToken: ProgressToken?
        get() = json["progressToken"]?.let { element ->
            when {
                element is JsonPrimitive && element.isString -> ProgressToken(element.content)
                element is JsonPrimitive && element.longOrNull != null -> ProgressToken(element.long)
                else -> null
            }
        }

    /**
     * The related task metadata, if this request is associated with a task.
     *
     * @see RelatedTaskMetadata
     * @see RELATED_TASK_META_KEY
     */
    public val relatedTask: RelatedTaskMetadata?
        get() = json[RELATED_TASK_META_KEY]?.let { element ->
            McpJson.decodeFromJsonElement(element)
        }

    /**
     * Retrieves the value associated with the specified key from the JSON object.
     *
     * @param key the key whose corresponding value is to be returned
     * @return the JsonElement associated with the specified key, or null if the key does not exist
     */
    public operator fun get(key: String): JsonElement? = json[key]
}

/**
 * Base interface for parameters attached to a [Request].
 */
@Serializable
public sealed interface RequestParams {
    /**
     * The `_meta` property/parameter is reserved by MCP
     * to allow clients and servers to attach additional metadata to their interactions.
     *
     * @see <a href="https://modelcontextprotocol.io/specification/2025-06-18/basic/index#meta">MCP specification</a>
     */
    @SerialName("_meta")
    public val meta: RequestMeta?
}

/**
 * Default [RequestParams] implementation carrying only optional metadata.
 */
@Serializable
public data class BaseRequestParams(@SerialName("_meta") override val meta: RequestMeta? = null) : RequestParams

/**
 * Represents a request in the protocol.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = RequestPolymorphicSerializer::class)
public sealed interface Request {
    /** The request method identifier. */
    public val method: Method

    /** Optional request parameters. */
    public val params: RequestParams?
}

/**
 * A custom request with a specified method.
 */
@Serializable
public open class CustomRequest(override val method: Method, override val params: BaseRequestParams?) : Request

/**
 * Represents a request sent by the client.
 */
@Serializable
public sealed interface ClientRequest : Request

/**
 * Represents a request sent by the server.
 */
@Serializable
public sealed interface ServerRequest : Request

/**
 * Represents a request supporting pagination.
 */
@Serializable
public sealed interface PaginatedRequest : Request {
    public override val params: PaginatedRequestParams?

    /**
     * An opaque token representing the current pagination position.
     */
    public val cursor: String?
        get() = params?.cursor

    /**
     * Metadata for this request. May include a progressToken for out-of-band progress notifications.
     */
    public val meta: RequestMeta?
        get() = params?.meta
}

/**
 * Represents the result of a request, including additional metadata.
 */
@Serializable(with = RequestResultPolymorphicSerializer::class)
public sealed interface RequestResult : WithMeta

/**
 * Represents a result returned by the server in response to a [ClientRequest].
 */
@Serializable(with = ClientResultPolymorphicSerializer::class)
public sealed interface ClientResult : RequestResult

/**
 * Represents a result returned by the client in response to a [ServerRequest].
 */
@Serializable(with = ServerResultPolymorphicSerializer::class)
public sealed interface ServerResult : RequestResult

/**
 * An empty result for a request containing optional metadata.
 *
 * @property meta Additional metadata for the response. Defaults to an empty JSON object.
 */
@Serializable
public data class EmptyResult(@SerialName("_meta") override val meta: JsonObject? = null) :
    ClientResult,
    ServerResult

/**
 * Represents a paginated result.
 */
@Serializable
public sealed interface PaginatedResult : RequestResult {
    /** Opaque token for retrieving the next page of results, or `null` if no more results. */
    public val nextCursor: String?
}

/**
 * Common parameters for paginated requests.
 *
 * @property cursor An opaque token representing the current pagination position.
 * If provided, the server should return results starting after this cursor.
 * @property meta Optional metadata for this request. May include a progressToken for
 * out-of-band progress notifications.
 */
@Serializable
public data class PaginatedRequestParams(
    val cursor: String? = null,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams

internal fun paginatedRequestParams(cursor: String?, meta: RequestMeta?): PaginatedRequestParams? =
    if (cursor == null && meta == null) {
        null
    } else {
        PaginatedRequestParams(cursor = cursor, meta = meta)
    }
