package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject

/**
 * Parameters for a `server/discover` request.
 *
 * Discovery belongs to the request-scoped lifecycle and therefore requires a protocol version and
 * client capabilities in [meta]. Client information is recommended by the protocol but optional.
 * Unknown metadata entries are preserved by [RequestMeta].
 *
 * @property meta request-scoped protocol and client metadata
 * @throws IllegalArgumentException if required request-scoped metadata is absent
 * @throws SerializationException if required metadata is malformed
 */
@Serializable
@ExperimentalMcpApi
public data class DiscoverRequestParams(
    @SerialName("_meta")
    override val meta: RequestMeta,
) : RequestParams {
    init {
        requireNotNull(meta.protocolVersion) { "${RequestMetaKeys.PROTOCOL_VERSION} is required" }
        requireNotNull(meta.clientCapabilities) { "${RequestMetaKeys.CLIENT_CAPABILITIES} is required" }
    }
}

/**
 * Requests the protocol versions, capabilities, and metadata advertised by a server.
 *
 * @property params request metadata required by the request-scoped lifecycle
 */
@Serializable
@ExperimentalMcpApi
public data class DiscoverRequest(override val params: DiscoverRequestParams) : ClientRequest {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val method: Method = Method.Defined.ServerDiscover

    /** Metadata for this request. */
    public val meta: RequestMeta
        get() = params.meta
}

/** Defines where a cached discovery result may be reused. */
@Serializable
@ExperimentalMcpApi
public enum class CacheScope {
    /** The result may only be reused within the same authorization context. */
    @SerialName("private")
    Private,

    /** The result may be shared across authorization contexts. */
    @SerialName("public")
    Public,
}

/**
 * Result returned by `server/discover`.
 *
 * @property supportedVersions protocol versions supported by the server
 * @property capabilities capabilities advertised by the server
 * @property instructions optional guidance for clients using the server
 * @property resultType discriminator for the result representation
 * @property ttlMs number of milliseconds clients may treat this result as fresh
 * @property cacheScope authorization boundary within which the result may be reused
 * @property meta optional response metadata, including optional server information
 * @throws IllegalArgumentException if [ttlMs] is negative
 */
@Serializable
@ExperimentalMcpApi
public data class DiscoverResult(
    val supportedVersions: List<String>,
    val capabilities: ServerCapabilities,
    val instructions: String? = null,
    @EncodeDefault
    val resultType: String = COMPLETE_RESULT_TYPE,
    @Required
    val ttlMs: Long = 0,
    @Required
    val cacheScope: CacheScope = CacheScope.Private,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult {
    init {
        require(ttlMs >= 0) { "ttlMs must be non-negative, but was $ttlMs" }
    }
}

/**
 * Data returned when a request selects an unsupported protocol version.
 *
 * @property supported protocol versions supported by the receiver
 * @property requested protocol version selected by the request
 */
@Serializable
@ExperimentalMcpApi
public data class UnsupportedProtocolVersionData(val supported: List<String>, val requested: String)

internal const val COMPLETE_RESULT_TYPE: String = "complete"
