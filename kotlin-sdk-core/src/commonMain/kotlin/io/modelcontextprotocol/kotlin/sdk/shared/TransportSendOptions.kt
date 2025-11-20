package io.modelcontextprotocol.kotlin.sdk.shared

import io.modelcontextprotocol.kotlin.sdk.types.RequestId

/**
 * Options for sending a JSON-RPC message through transport.
 *
 * @property relatedRequestId if present,
 * `relatedRequestId` is used to indicate to the transport which incoming request to associate this outgoing message with.
 * @property resumptionToken the resumption token used to continue long-running requests that were interrupted.
 * This allows clients to reconnect and continue from where they left off, if supported by the transport.
 * @property onResumptionToken a callback that is invoked when the resumption token changes, if supported by the transport.
 * This allows clients to persist the latest token for potential reconnection.
 */
public open class TransportSendOptions(
    public val relatedRequestId: RequestId? = null,
    public val resumptionToken: String? = null,
    public val onResumptionToken: ((String) -> Unit)? = null,
) {
    public operator fun component1(): RequestId? = relatedRequestId
    public operator fun component2(): String? = resumptionToken
    public operator fun component3(): ((String) -> Unit)? = onResumptionToken

    public open fun copy(
        relatedRequestId: RequestId? = this.relatedRequestId,
        resumptionToken: String? = this.resumptionToken,
        onResumptionToken: ((String) -> Unit)? = this.onResumptionToken,
    ): TransportSendOptions = TransportSendOptions(relatedRequestId, resumptionToken, onResumptionToken)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TransportSendOptions

        return when {
            relatedRequestId != other.relatedRequestId -> false
            resumptionToken != other.resumptionToken -> false
            onResumptionToken != other.onResumptionToken -> false
            else -> true
        }
    }

    override fun hashCode(): Int {
        var result = relatedRequestId?.hashCode() ?: 0
        result = 31 * result + (resumptionToken?.hashCode() ?: 0)
        result = 31 * result + (onResumptionToken?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "TransportSendOptions(relatedRequestId=$relatedRequestId, resumptionToken=$resumptionToken, onResumptionToken=$onResumptionToken)"
}
