package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.DiscoverResult
import io.modelcontextprotocol.kotlin.sdk.types.HANDSHAKE_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.UnsupportedProtocolVersionData
import io.modelcontextprotocol.kotlin.sdk.types.isModernProtocolVersion
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * How a client settles which protocol lifecycle it speaks with a server.
 *
 * The two lifecycles cannot interoperate, so a client settles this once per connection — unlike a
 * server, which decides it per request.
 */
@ExperimentalMcpApi
public sealed interface VersionNegotiationMode {
    /**
     * The plain `initialize` handshake, byte-identical to a client built without this option.
     *
     * The default, and the only lifecycle offering sampling, elicitation, roots, `ping`,
     * `logging/setLevel` and resource subscriptions; the request-scoped lifecycle has none of them.
     */
    public data object Legacy : VersionNegotiationMode

    /**
     * Probe `server/discover`, and fall back to the handshake unless the answer is positive
     * evidence of the request-scoped lifecycle.
     *
     * Anything not recognized as such evidence falls back, so a server that has never heard of
     * discovery still connects, at the cost of one extra round trip.
     */
    public data object Auto : VersionNegotiationMode

    /**
     * The request-scoped lifecycle at exactly [version], with no fallback.
     *
     * @throws IllegalArgumentException if [version] predates the request-scoped lifecycle, which
     * has no envelope to pin — such a version is reached through [Legacy] instead
     */
    public data class Pin(public val version: String) : VersionNegotiationMode {
        init {
            require(isModernProtocolVersion(version)) {
                "$version is not a request-scoped protocol revision, so it cannot be pinned; " +
                    "use VersionNegotiationMode.Legacy to reach it through the initialize handshake."
            }
        }
    }
}

/** What a `server/discover` probe came back as, normalized for [classifyProbeOutcome]. */
internal sealed interface ProbeOutcome {
    /** The server answered. [result] is `null` when the answer did not parse as a discovery result. */
    data class Answered(val result: DiscoverResult?) : ProbeOutcome

    /** The server answered with a JSON-RPC error. */
    data class Refused(val error: McpException) : ProbeOutcome

    /** Nothing arrived within the probe timeout, or the peer went away instead of answering. */
    data object Silent : ProbeOutcome
}

/** What a client does next, given a probe outcome. */
internal sealed interface ProbeVerdict {
    /** Positive evidence: adopt [discover] and speak [version] without a handshake. */
    data class Modern(val version: String, val discover: DiscoverResult) : ProbeVerdict

    /**
     * The server named a revision both ends speak: re-probe at [version]. This continuation is
     * part of negotiation, not a retry.
     */
    data class Corrective(val version: String) : ProbeVerdict

    /** No positive evidence: run the plain `initialize` handshake. */
    data object Legacy : ProbeVerdict

    /** A genuine incompatibility: report [cause] rather than pretending either lifecycle works. */
    data class Failed(val cause: McpException) : ProbeVerdict
}

/**
 * What [outcome] means for the lifecycle of this connection.
 *
 * Pure and total: retry state, loop guards and I/O all live in the caller. The verdict is
 * [ProbeVerdict.Modern] only on evidence that positively identifies the request-scoped lifecycle;
 * everything else falls back. A failure that is not the server answering — a network outage, a
 * cancellation — is not a verdict about which protocol a server speaks and never reaches here.
 *
 * @param clientModernVersions request-scoped revisions this client can speak, in preference order
 * @param fallbackAvailable whether the handshake is still open to this client; `false` under
 *   [VersionNegotiationMode.Pin], where an outcome that would have fallen back is a failure instead
 * @param overStdio whether the probe ran over stdio, where a server that stays silent or exits is a
 *   handshake-era signal rather than an outage — there is no status code to tell the two apart
 */
internal fun classifyProbeOutcome(
    outcome: ProbeOutcome,
    clientModernVersions: List<String>,
    fallbackAvailable: Boolean,
    overStdio: Boolean,
): ProbeVerdict = when (outcome) {
    is ProbeOutcome.Answered -> classifyAnswer(outcome.result, clientModernVersions, fallbackAvailable)

    is ProbeOutcome.Refused -> classifyRefusal(outcome.error, clientModernVersions, fallbackAvailable)

    ProbeOutcome.Silent -> if (overStdio) {
        fallBack(fallbackAvailable) {
            McpException(
                code = RPCError.ErrorCode.CONNECTION_CLOSED,
                message = "The server did not answer the server/discover probe, so it does not " +
                    "speak a pinned request-scoped revision.",
            )
        }
    } else {
        // A deployed HTTP server answers, so silence there is an outage, not an era.
        ProbeVerdict.Failed(
            McpException(
                code = RPCError.ErrorCode.REQUEST_TIMEOUT,
                message = "The server/discover probe timed out.",
            ),
        )
    }
}

private fun classifyAnswer(
    result: DiscoverResult?,
    clientModernVersions: List<String>,
    fallbackAvailable: Boolean,
): ProbeVerdict {
    // An answer this SDK cannot read is not evidence of anything, so it falls back like silence.
    val supported = result?.supportedVersions
        ?: return fallBack(fallbackAvailable) {
            McpException(
                code = RPCError.ErrorCode.INVALID_PARAMS,
                message = "The server/discover result did not parse, so no revision could be selected.",
            )
        }
    val mutual = clientModernVersions.firstOrNull { it in supported }
        // The server speaks discovery but advertises no revision this client has. That is an
        // advertisement, not an incompatibility, so a client that can still handshake does.
        ?: return fallBack(fallbackAvailable) { unsupported(supported, clientModernVersions) }
    return ProbeVerdict.Modern(version = mutual, discover = result)
}

private fun classifyRefusal(
    error: McpException,
    clientModernVersions: List<String>,
    fallbackAvailable: Boolean,
): ProbeVerdict {
    // Denylist, not allowlist: only the one code that carries version evidence is read as such.
    if (error.code != RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION) {
        return fallBack(fallbackAvailable) { error }
    }
    val supported = error.data
        ?.let {
            try {
                McpJson.decodeFromJsonElement<UnsupportedProtocolVersionData>(it).supported
            } catch (_: SerializationException) {
                null
            }
        }
        ?: return fallBack(fallbackAvailable) { error }

    clientModernVersions.firstOrNull { it in supported }?.let { return ProbeVerdict.Corrective(it) }
    // No mutual request-scoped revision. A server still naming a handshake revision is reachable
    // the old way; one naming only revisions this client does not have is genuinely incompatible,
    // and saying so beats a handshake that will fail less legibly.
    return if (supported.any { it in HANDSHAKE_PROTOCOL_VERSIONS }) {
        fallBack(fallbackAvailable) { error }
    } else {
        ProbeVerdict.Failed(error)
    }
}

/** The verdict for an outcome carrying no positive evidence: fall back, or fail where none is left. */
private inline fun fallBack(fallbackAvailable: Boolean, cause: () -> McpException): ProbeVerdict =
    if (fallbackAvailable) ProbeVerdict.Legacy else ProbeVerdict.Failed(cause())

private fun unsupported(supported: List<String>, requested: List<String>): McpException = McpException(
    code = RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION,
    message = "The server supports ${supported.joinToString()}, none of which this client speaks " +
        "(${requested.joinToString()}).",
)
