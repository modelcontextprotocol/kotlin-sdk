package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Represents a ping request used to check if the connection is alive.
 *
 * @property params optional request parameters carrying metadata
 */
@Serializable
public data class PingRequest(override val params: BaseRequestParams? = null) :
    ClientRequest,
    ServerRequest {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val method: Method = Method.Defined.Ping

    /** Convenience accessor for [params]'s metadata. */
    public val meta: RequestMeta?
        get() = params?.meta
}
