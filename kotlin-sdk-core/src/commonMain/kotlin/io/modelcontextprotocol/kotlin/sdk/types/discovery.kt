package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parameters for a `server/discover` request.
 *
 * Discovery belongs to the request-scoped lifecycle, so [meta] carries the [RequestEnvelope] rather
 * than being optional as it is on requests that predate it. Whether the envelope is intact is
 * checked where the request is served, not here, so a malformed one can be answered on the wire
 * instead of failing construction.
 *
 * @property meta request-scoped protocol and client metadata
 */
@Serializable
@ExperimentalMcpApi
public data class DiscoverRequestParams(
    @SerialName("_meta")
    override val meta: RequestMeta,
) : RequestParams

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
}

/**
 * Result returned by `server/discover`.
 *
 * @property supportedVersions protocol versions supported by the server
 * @property capabilities capabilities advertised by the server
 * @property instructions optional guidance for clients using the server
 * @property resultType Discriminator for the result representation.
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
    val resultType: String = COMPLETE_RESULT_TYPE,
    override val ttlMs: Long = 0,
    override val cacheScope: CacheScope = CacheScope.Private,
    @SerialName("_meta")
    override val meta: ResultMeta? = null,
) : ServerResult,
    CacheableResult {
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
