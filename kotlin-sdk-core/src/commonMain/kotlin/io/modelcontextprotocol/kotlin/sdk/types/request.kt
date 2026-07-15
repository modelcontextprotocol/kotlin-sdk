package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlin.jvm.JvmInline

internal object RequestMetaKeys {
    const val PROTOCOL_VERSION: String = "io.modelcontextprotocol/protocolVersion"
    const val CLIENT_INFO: String = "io.modelcontextprotocol/clientInfo"
    const val CLIENT_CAPABILITIES: String = "io.modelcontextprotocol/clientCapabilities"
    const val LOG_LEVEL: String = "io.modelcontextprotocol/logLevel"
}

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
     * The MCP protocol version selected for this request, or `null` when absent.
     *
     * This field is required by the request-scoped lifecycle introduced in protocol version
     * `2026-07-28`, but remains optional here so older requests retain their wire shape.
     *
     * @throws SerializationException if the field is present but is not a string
     */
    @ExperimentalMcpApi
    public val protocolVersion: String?
        get() = json[RequestMetaKeys.PROTOCOL_VERSION]?.let { element ->
            if (element !is JsonPrimitive || !element.isString) {
                throw SerializationException("${RequestMetaKeys.PROTOCOL_VERSION} must be a JSON string")
            }
            element.content
        }

    /**
     * Information about the client making this request, or `null` when absent.
     *
     * @throws SerializationException if the field is present but is not a valid [Implementation]
     */
    @ExperimentalMcpApi
    public val clientInfo: Implementation?
        get() = json[RequestMetaKeys.CLIENT_INFO]?.let { element ->
            try {
                McpJson.decodeFromJsonElement<Implementation>(element)
            } catch (cause: SerializationException) {
                throw SerializationException(
                    "${RequestMetaKeys.CLIENT_INFO} must be a valid client implementation",
                    cause,
                )
            }
        }

    /**
     * Capabilities declared by the client for this request, or `null` when absent.
     *
     * Servers using the request-scoped lifecycle must not infer capabilities from an earlier
     * request. An empty object means the client supports no optional capabilities.
     *
     * @throws SerializationException if the field is present but is not valid [ClientCapabilities]
     */
    @ExperimentalMcpApi
    public val clientCapabilities: ClientCapabilities?
        get() = json[RequestMetaKeys.CLIENT_CAPABILITIES]?.let { element ->
            try {
                McpJson.decodeFromJsonElement<ClientCapabilities>(element)
            } catch (cause: SerializationException) {
                throw SerializationException(
                    "${RequestMetaKeys.CLIENT_CAPABILITIES} must be a valid client capabilities object",
                    cause,
                )
            }
        }

    /**
     * Minimum severity of request-associated log notifications, or `null` when absent.
     *
     * In the request-scoped lifecycle, absence means that the server must not emit log
     * notifications for this request.
     *
     * @throws SerializationException if the field is present but is not a valid [LoggingLevel]
     */
    @Deprecated("Per-request log levels are deprecated as of MCP protocol version 2026-07-28 (SEP-2577).")
    @ExperimentalMcpApi
    public val logLevel: LoggingLevel?
        get() = json[RequestMetaKeys.LOG_LEVEL]?.let { element ->
            try {
                McpJson.decodeFromJsonElement<LoggingLevel>(element)
            } catch (cause: SerializationException) {
                throw SerializationException(
                    "${RequestMetaKeys.LOG_LEVEL} must be a valid logging level",
                    cause,
                )
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
