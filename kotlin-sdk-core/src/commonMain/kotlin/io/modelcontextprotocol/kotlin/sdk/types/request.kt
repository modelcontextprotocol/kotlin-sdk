package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
public value class RequestMeta(public val json: JsonObject) {
    public val progressToken: ProgressToken?
        get() = json["progressToken"]?.let { element ->
            when (element) {
                is JsonPrimitive if (element.isString) -> ProgressToken(element.content)
                is JsonPrimitive if (element.longOrNull != null) -> ProgressToken(element.long)
                else -> null
            }
        }

    /**
     * Retrieves the value associated with the specified key from the JSON object.
     *
     * @param key the key whose corresponding value is to be returned
     * @return the JsonElement associated with the specified key, or null if the key does not exist
     */
    public operator fun get(key: String): JsonElement? = json[key]
}

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

@Serializable
public data class BaseRequestParams(@SerialName("_meta") override val meta: RequestMeta? = null) : RequestParams

/**
 * Represents a request in the protocol.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = RequestPolymorphicSerializer::class)
public sealed interface Request {
    public val method: Method
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

@Serializable(with = ClientResultPolymorphicSerializer::class)
public sealed interface ClientResult : RequestResult

@Serializable(with = ServerResultPolymorphicSerializer::class)
public sealed interface ServerResult : RequestResult

/**
 * An empty result for a request containing optional metadata.
 *
 * @param meta Additional metadata for the response. Defaults to an empty JSON object.
 */
@Serializable
public data class EmptyResult(@SerialName("_meta") override val meta: JsonObject? = null) :
    ClientResult,
    ServerResult

/**
 * Represents a request supporting pagination.
 */
@Serializable
public sealed interface PaginatedResult : RequestResult {
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
