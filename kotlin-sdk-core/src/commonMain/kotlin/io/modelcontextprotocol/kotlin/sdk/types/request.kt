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

    public operator fun get(key: String): JsonElement? = json[key]
}

@Serializable
public sealed interface RequestParams {
    @SerialName("_meta")
    public val meta: RequestMeta?
}

@Serializable
public data class BaseRequestParams(override val meta: RequestMeta? = null) : RequestParams

/**
 * Represents a request in the protocol.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public sealed interface Request {
    public val method: Method
    public val params: RequestParams?
}

/**
 * A custom request with a specified method.
 */
@Serializable
public open class CustomRequest(override val method: Method, override val params: RequestParams?) : Request

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
}

/**
 * Represents the result of a request, including additional metadata.
 */
@Serializable // TODO: custom serializer
public sealed interface RequestResult : WithMeta

@Serializable
public sealed interface ClientResult : RequestResult

@Serializable
public sealed interface ServerResult : RequestResult

/**
 * An empty result for a request containing optional metadata.
 *
 * @param meta Additional metadata for the response. Defaults to an empty JSON object.
 */
@Serializable
public data class EmptyResult(override val meta: JsonObject? = null) :
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
public data class PaginatedRequestParams(val cursor: String? = null, override val meta: RequestMeta? = null) :
    RequestParams
