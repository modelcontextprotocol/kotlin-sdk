package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException

/** `_meta` key carrying the protocol version a request is made under. */
@ExperimentalMcpApi
public const val PROTOCOL_VERSION_META_KEY: String = "io.modelcontextprotocol/protocolVersion"

/** `_meta` key carrying the name and version of the client software issuing a request. */
@ExperimentalMcpApi
public const val CLIENT_INFO_META_KEY: String = "io.modelcontextprotocol/clientInfo"

/** `_meta` key carrying the capabilities a client declares for a request. */
@ExperimentalMcpApi
public const val CLIENT_CAPABILITIES_META_KEY: String = "io.modelcontextprotocol/clientCapabilities"

/** `_meta` key carrying the minimum log severity a server may emit for a request. */
@ExperimentalMcpApi
public const val LOG_LEVEL_META_KEY: String = "io.modelcontextprotocol/logLevel"

/** `_meta` key carrying the name and version of the server software producing a result. */
@ExperimentalMcpApi
public const val SERVER_INFO_META_KEY: String = "io.modelcontextprotocol/serverInfo"

@OptIn(ExperimentalMcpApi::class)
private val ENVELOPE_META_KEYS: Set<String> = setOf(
    PROTOCOL_VERSION_META_KEY,
    CLIENT_INFO_META_KEY,
    CLIENT_CAPABILITIES_META_KEY,
    LOG_LEVEL_META_KEY,
)

/**
 * The protocol fields a client attaches to every request's `_meta`.
 *
 * These travel per request rather than per connection, so a server can serve a request without
 * consulting anything an earlier request established. Read one with [RequestMeta.toEnvelope],
 * write one with [toMeta].
 *
 * @property protocolVersion the protocol version this request is made under
 * @property clientCapabilities capabilities the client declares for this request. An empty value
 * declares no optional capabilities, and a receiver must not read capabilities from any other
 * request.
 * @property clientInfo name and version of the client software. Self-reported and unverified, so
 * receivers should confine it to display, logging and debugging rather than behavior or security
 * decisions.
 * @property logLevel minimum severity of log notifications the server may emit while serving this
 * request. Absent means the server emits none.
 */
@Serializable
@ExperimentalMcpApi
public data class RequestEnvelope(
    @SerialName(PROTOCOL_VERSION_META_KEY)
    val protocolVersion: String,
    @SerialName(CLIENT_CAPABILITIES_META_KEY)
    val clientCapabilities: ClientCapabilities,
    @SerialName(CLIENT_INFO_META_KEY)
    val clientInfo: Implementation? = null,
    @SerialName(LOG_LEVEL_META_KEY)
    val logLevel: LoggingLevel? = null,
)

/**
 * Renders this envelope as request metadata, preserving every non-protocol entry of [base].
 *
 * The envelope is authoritative: protocol entries already present in [base] are replaced, and one
 * whose envelope counterpart is `null` is dropped rather than carried over. Unrelated entries such
 * as `progressToken` survive untouched.
 */
@ExperimentalMcpApi
public fun RequestEnvelope.toMeta(base: RequestMeta? = null): RequestMeta = RequestMeta(
    JsonObject(
        base?.json.orEmpty().filterKeys { it !in ENVELOPE_META_KEYS } +
            McpJson.encodeToJsonElement(this).jsonObject,
    ),
)

/**
 * The protocol envelope carried by this metadata.
 *
 * Use [toEnvelopeOrNull] to ask whether an intact envelope is present without failing on one that
 * is not.
 *
 * @throws SerializationException if a required field is absent or any field is malformed; the
 * message names the offending `_meta` key
 */
@ExperimentalMcpApi
public fun RequestMeta.toEnvelope(): RequestEnvelope = McpJson.decodeFromJsonElement(json)

/**
 * The protocol envelope carried by this metadata, or `null` when it is absent or not intact.
 *
 * Answers "is a complete, well-formed envelope present": metadata carrying none — a request from a
 * peer that predates the envelope, say — yields `null` rather than failing, and so does one whose
 * envelope is incomplete in any field. Use [toEnvelope] where an unusable envelope has to be
 * reported rather than ignored.
 */
@ExperimentalMcpApi
public fun RequestMeta.toEnvelopeOrNull(): RequestEnvelope? = try {
    toEnvelope()
} catch (_: SerializationException) {
    null
}

/**
 * One self-identifying problem found while validating a `_meta` envelope.
 *
 * @property key the reserved `_meta` key the problem is about
 * @property problem what is wrong with it, phrased to be readable in an error message
 */
@ExperimentalMcpApi
public data class EnvelopeIssue(val key: String, val problem: String)

/** [EnvelopeIssue.problem] for a required key that is not there at all. */
private const val MISSING_PROBLEM: String = "missing"

/**
 * Everything that stops this metadata from carrying a usable [RequestEnvelope], or an empty list
 * when nothing does.
 *
 * Every problem is reported at once, so a peer is not corrected one round trip at a time. A server
 * answers a non-empty list with `-32602`.
 *
 * Checked: both required keys, for presence and shape, and [LOG_LEVEL_META_KEY] when present — a
 * level that names no known severity is rejected rather than guessed, since it decides what the
 * server may emit. Not checked: [CLIENT_INFO_META_KEY], whatever it contains. Identity is
 * self-reported and must not change behaviour, so a malformed one degrades to "not supplied".
 */
@ExperimentalMcpApi
public fun validateEnvelope(meta: RequestMeta): List<EnvelopeIssue> {
    val protocolVersion = meta[PROTOCOL_VERSION_META_KEY]
    val clientCapabilities = meta[CLIENT_CAPABILITIES_META_KEY]
    val logLevel = meta[LOG_LEVEL_META_KEY]

    val missing = listOfNotNull(
        PROTOCOL_VERSION_META_KEY.takeIf { protocolVersion == null },
        CLIENT_CAPABILITIES_META_KEY.takeIf { clientCapabilities == null },
    ).map { EnvelopeIssue(it, MISSING_PROBLEM) }

    val malformed = listOfNotNull(
        protocolVersion
            ?.takeIf { it !is JsonPrimitive || !it.isString }
            ?.let { EnvelopeIssue(PROTOCOL_VERSION_META_KEY, "must be a JSON string") },
        clientCapabilities
            ?.takeIf { !it.decodesAs<ClientCapabilities>() }
            ?.let { EnvelopeIssue(CLIENT_CAPABILITIES_META_KEY, "must be a client capabilities object") },
        logLevel
            ?.takeIf { !it.decodesAs<LoggingLevel>() }
            ?.let { EnvelopeIssue(LOG_LEVEL_META_KEY, "must be a known logging level") },
    )

    return missing + malformed
}

/**
 * The protocol envelope carried by this metadata, reading each field on its own terms.
 *
 * Admits exactly what [validateEnvelope] admits, and is the reader to use after that check has
 * passed: a malformed optional field reads as absent instead of discarding the whole envelope.
 * Returns `null` only when a required field is absent or malformed.
 *
 * Changing what [validateEnvelope] admits requires changing this in step, or a request can be
 * accepted and then served without the envelope it was accepted for.
 */
@ExperimentalMcpApi
internal fun RequestMeta.toEnvelopeLenient(): RequestEnvelope? {
    val protocolVersion = claimedProtocolVersion ?: return null
    val clientCapabilities = json[CLIENT_CAPABILITIES_META_KEY]?.decodeOrNull<ClientCapabilities>() ?: return null
    return RequestEnvelope(
        protocolVersion = protocolVersion,
        clientCapabilities = clientCapabilities,
        clientInfo = json[CLIENT_INFO_META_KEY]?.decodeOrNull<Implementation>(),
        logLevel = json[LOG_LEVEL_META_KEY]?.decodeOrNull<LoggingLevel>(),
    )
}

/**
 * This element decoded as [T], or `null` when it is not a valid [T].
 *
 * The catch is broad on purpose: kotlinx signals a structural mismatch with whichever exception the
 * generated deserializer happens to raise, and here they all mean the same thing.
 */
private inline fun <reified T> JsonElement.decodeOrNull(): T? = try {
    McpJson.decodeFromJsonElement<T>(this)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/** Whether this element is a valid [T]. */
private inline fun <reified T> JsonElement.decodesAs(): Boolean = decodeOrNull<T>() != null

/**
 * The server identity reported alongside this result, or `null` when absent.
 *
 * Self-reported and unverified, so clients should confine it to display, logging and debugging
 * rather than behavior or security decisions.
 *
 * @throws SerializationException if the field is present but is not a valid [Implementation]
 */
@ExperimentalMcpApi
public val ResultMeta.serverInfo: Implementation?
    get() = json[SERVER_INFO_META_KEY]?.let { McpJson.decodeFromJsonElement<Implementation>(it) }

/**
 * Renders [serverInfo] as result metadata, preserving every other entry of this metadata.
 *
 * Servers should identify themselves on every result unless configured otherwise, so this is the
 * write counterpart of [ResultMeta.serverInfo]. Any identity already present is replaced.
 */
@ExperimentalMcpApi
public fun ResultMeta?.withServerInfo(serverInfo: Implementation): ResultMeta = ResultMeta(
    JsonObject(
        this?.json.orEmpty() + (SERVER_INFO_META_KEY to McpJson.encodeToJsonElement(serverInfo)),
    ),
)

/**
 * Data carried by a [RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY] error.
 *
 * @property requiredCapabilities the capabilities the server needs in order to serve the request,
 * narrowed to those the client did not declare
 */
@Serializable
@ExperimentalMcpApi
public data class MissingRequiredClientCapabilityData(val requiredCapabilities: ClientCapabilities)

/**
 * The subset of these required capabilities that [declared] does not cover, or `null` when it
 * covers all of them.
 *
 * A server must not rely on a capability the client did not declare, and answers a request needing
 * one with [RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY]; the return value is what that
 * error reports as [MissingRequiredClientCapabilityData.requiredCapabilities]. A `null` [declared]
 * means nothing was declared, so every required capability comes back as missing.
 *
 * Capabilities are compared one level deep: a required capability whose declared counterpart is
 * absent is missing whole, and one that is present is narrowed to the sub-capabilities the
 * declaration omits. A bare [ClientCapabilities.Elicitation] naming no mode counts as declaring
 * form mode.
 */
@ExperimentalMcpApi
public fun ClientCapabilities.missingIn(declared: ClientCapabilities?): ClientCapabilities? {
    val declaredJson = declared?.let { McpJson.encodeToJsonElement(it).jsonObject }.orEmpty()
    val missing = McpJson.encodeToJsonElement(this).jsonObject.mapNotNull { (capability, required) ->
        val declaredValue = declaredJson[capability] ?: return@mapNotNull capability to required
        if (required !is JsonObject || declaredValue !is JsonObject) return@mapNotNull null
        required
            .filterKeys { it !in declaredValue && !impliesMember(capability, it, declaredValue) }
            .takeIf { it.isNotEmpty() }
            ?.let { capability to JsonObject(it) }
    }
    return missing.takeIf { it.isNotEmpty() }?.let { McpJson.decodeFromJsonElement(JsonObject(it.toMap())) }
}

/**
 * Whether a declaration implies a sub-capability it does not name: a bare `elicitation` naming no
 * mode means form mode. Naming any mode removes the implication.
 */
private fun impliesMember(capability: String, member: String, declared: JsonObject): Boolean =
    capability == "elicitation" && member == "form" && "form" !in declared && "url" !in declared
