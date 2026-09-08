package io.modelcontextprotocol.kotlin.sdk.server

import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.InternalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.CLIENT_CAPABILITIES_META_KEY
import io.modelcontextprotocol.kotlin.sdk.types.MCP_METHOD_HEADER
import io.modelcontextprotocol.kotlin.sdk.types.MCP_NAME_HEADER
import io.modelcontextprotocol.kotlin.sdk.types.MCP_PROTOCOL_VERSION_HEADER
import io.modelcontextprotocol.kotlin.sdk.types.MODERN_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.NAME_BEARING_METHODS
import io.modelcontextprotocol.kotlin.sdk.types.PROTOCOL_VERSION_META_KEY
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import io.modelcontextprotocol.kotlin.sdk.types.UnsupportedProtocolVersionData
import io.modelcontextprotocol.kotlin.sdk.types.decodeMcpHeaderValue
import io.modelcontextprotocol.kotlin.sdk.types.validateEnvelope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

/**
 * HTTP status for a JSON-RPC error code.
 *
 * A code absent from this map travels in-band on `200`. `-32602` is absent on purpose: a handler
 * called with bad arguments is not an HTTP failure.
 */
internal val HTTP_STATUS_BY_ERROR_CODE: Map<Int, HttpStatusCode> = mapOf(
    RPCError.ErrorCode.PARSE_ERROR to HttpStatusCode.BadRequest,
    RPCError.ErrorCode.INVALID_REQUEST to HttpStatusCode.BadRequest,
    RPCError.ErrorCode.METHOD_NOT_FOUND to HttpStatusCode.NotFound,
    RPCError.ErrorCode.HEADER_MISMATCH to HttpStatusCode.BadRequest,
    RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY to HttpStatusCode.BadRequest,
    RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION to HttpStatusCode.BadRequest,
)

/** What one inbound HTTP body turned out to be. */
internal sealed interface InboundOutcome {
    /** Handshake-era traffic, served exactly as a handshake-only transport would serve it. */
    data class Legacy(val reason: String) : InboundOutcome

    /** A request claiming the request-scoped lifecycle, with its envelope already validated. */
    data class Modern(val method: String, val id: RequestId, val protocolVersion: String) : InboundOutcome

    /** A request-scoped notification, which this wire acknowledges and drops. */
    data class ModernNotification(val method: String) : InboundOutcome

    /** The body failed a rung of the inbound ladder. */
    data class Reject(val error: RPCError, val status: HttpStatusCode, val id: RequestId?) : InboundOutcome
}

/**
 * Classifies one inbound HTTP body, body-primary, and runs the ladder rungs an HTTP entry owns.
 *
 * An id-bearing body **claims** the request-scoped lifecycle if and only if its `params._meta`
 * carries [PROTOCOL_VERSION_META_KEY]. Nothing else counts as a claim, and no connection state
 * participates, so one endpoint can serve both lifecycles interleaved. An id-less body — a
 * notification — carries no claim at this revision and is routed by the version header alone;
 * see [classifyNotification].
 *
 * The rungs, in precedence order, each answering the earliest failure:
 *
 * 1. **jsonrpc-shape** — a batch containing a claim, a body that is neither request nor
 *    notification, and an `id` that is not a string or an integer are refused
 *    [RPCError.ErrorCode.INVALID_REQUEST]. An all-handshake batch and a posted response stay
 *    handshake traffic.
 * 2. **headers** — on requests only, [MCP_PROTOCOL_VERSION_HEADER] and [MCP_METHOD_HEADER] must be
 *    present and agree with the body, and [MCP_NAME_HEADER] must agree for the methods that mirror
 *    a param, else [RPCError.ErrorCode.HEADER_MISMATCH]. Ahead of the version rung, so a client
 *    that disagrees with itself is told that rather than told its version is unsupported.
 * 3. **envelope** — a present claim whose envelope is not intact is
 *    [RPCError.ErrorCode.INVALID_PARAMS], naming every offending key at once.
 * 4. **version** — a claim naming a revision this server does not serve is
 *    [RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION], naming the ones it does.
 *
 * Method existence is not a rung here: dispatch owns it, so a custom-registered method still routes.
 */
@OptIn(ExperimentalMcpApi::class, InternalMcpApi::class)
internal fun classifyInboundRequest(body: JsonElement, headers: Headers): InboundOutcome {
    if (body is JsonArray) {
        // Element-wise: a batch is handshake traffic unless some element claims otherwise, and the
        // request-scoped lifecycle has no batching for such an element to belong to.
        return if (body.any { it is JsonObject && claimOf(it) != null }) {
            InboundOutcome.Reject(
                error = RPCError(
                    code = RPCError.ErrorCode.INVALID_REQUEST,
                    message = "A JSON-RPC batch cannot carry a request-scoped protocol envelope",
                ),
                status = HttpStatusCode.BadRequest,
                id = null,
            )
        } else {
            InboundOutcome.Legacy("batch")
        }
    }
    if (body !is JsonObject) {
        return InboundOutcome.Reject(
            error = RPCError(
                code = RPCError.ErrorCode.INVALID_REQUEST,
                message = "Body must be a single JSON-RPC request or notification object",
            ),
            status = HttpStatusCode.BadRequest,
            id = null,
        )
    }

    val idElement = body["id"]
    val id = idElement?.toRequestIdOrNull()
    if (idElement != null && id == null) return invalidIdReject()
    if (id == null) return classifyNotification(body, headers)

    val meta = claimOf(body) ?: return noClaim(id, body, headers)
    val method = body.stringOf("method")
        // A claim on something that is not a request or a notification: no method to route it by.
        ?: return InboundOutcome.Reject(
            error = RPCError(
                code = RPCError.ErrorCode.INVALID_REQUEST,
                message = "Body must be a single JSON-RPC request or notification object",
            ),
            status = HttpStatusCode.BadRequest,
            id = null,
        )

    headerMismatch(body, method, meta, headers)?.let {
        return InboundOutcome.Reject(it, HttpStatusCode.BadRequest, id)
    }

    val issues = validateEnvelope(meta)
    if (issues.isNotEmpty()) {
        return InboundOutcome.Reject(
            error = RPCError(
                code = RPCError.ErrorCode.INVALID_PARAMS,
                message = "Invalid request envelope: " + issues.joinToString(", ") { "${it.key} ${it.problem}" },
            ),
            status = HttpStatusCode.BadRequest,
            id = id,
        )
    }

    val claimed = checkNotNull(meta.claimedProtocolVersion) { "an intact envelope implies a string version" }
    if (claimed !in MODERN_PROTOCOL_VERSIONS) {
        return InboundOutcome.Reject(
            error = RPCError(
                code = RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION,
                message = "Unsupported protocol version: $claimed",
                data = McpJson.encodeToJsonElement(
                    UnsupportedProtocolVersionData(supported = MODERN_PROTOCOL_VERSIONS, requested = claimed),
                ),
            ),
            status = HttpStatusCode.BadRequest,
            id = id,
        )
    }

    return InboundOutcome.Modern(method = method, id = id, protocolVersion = claimed)
}

/**
 * An id-less body, routed by [MCP_PROTOCOL_VERSION_HEADER] alone: notifications carry no body
 * claim at this revision, so the header is determinative for them (as in the reference SDKs). A
 * served modern version routes the notification to the `202` acknowledgement; anything else stays
 * handshake traffic. Header presence is not required for notifications, but a present
 * [MCP_METHOD_HEADER] must still agree with the body.
 */
private fun classifyNotification(body: JsonObject, headers: Headers): InboundOutcome {
    val version = headers[MCP_PROTOCOL_VERSION_HEADER]
    if (version == null || version !in MODERN_PROTOCOL_VERSIONS) return InboundOutcome.Legacy("notification")
    val method = body.stringOf("method")
        ?: return InboundOutcome.Reject(
            error = RPCError(
                code = RPCError.ErrorCode.INVALID_REQUEST,
                message = "Body must be a single JSON-RPC request or notification object",
            ),
            status = HttpStatusCode.BadRequest,
            id = null,
        )
    val methodHeader = headers[MCP_METHOD_HEADER]
    if (methodHeader != null && methodHeader != method) {
        return InboundOutcome.Reject(
            error = mismatch("$MCP_METHOD_HEADER does not match the notification body's method"),
            status = HttpStatusCode.BadRequest,
            id = null,
        )
    }
    return InboundOutcome.ModernNotification(method)
}

/**
 * What an id-bearing body making no claim is.
 *
 * Handshake traffic, unless [MCP_PROTOCOL_VERSION_HEADER] names a request-scoped revision. The
 * header never upgrades a classification, so that case is a rejection naming the envelope keys the
 * body is missing, rather than a route into request-scoped serving.
 */
@OptIn(ExperimentalMcpApi::class, InternalMcpApi::class)
private fun noClaim(id: RequestId, body: JsonObject, headers: Headers): InboundOutcome {
    val version = headers[MCP_PROTOCOL_VERSION_HEADER]
    if (version == null || version !in MODERN_PROTOCOL_VERSIONS) return InboundOutcome.Legacy("no-claim")
    val present = (body["params"] as? JsonObject)?.get("_meta") as? JsonObject
    val missing = listOf(PROTOCOL_VERSION_META_KEY, CLIENT_CAPABILITIES_META_KEY)
        .filter { present?.containsKey(it) != true }
    return InboundOutcome.Reject(
        error = RPCError(
            code = RPCError.ErrorCode.INVALID_PARAMS,
            message = "Invalid request envelope: " + missing.joinToString(", ") { "$it missing" },
        ),
        status = HttpStatusCode.BadRequest,
        id = id,
    )
}

/**
 * [this] as a [RequestId], or `null` for any shape the JSON-RPC id grammar does not admit —
 * `null`, booleans, fractional numbers, objects, arrays. The classifier answers those `-32600`
 * rather than letting the serializer's exception escape the route as an HTTP 500.
 */
private fun JsonElement.toRequestIdOrNull(): RequestId? = try {
    McpJson.decodeFromJsonElement(RequestId.serializer(), this)
} catch (_: SerializationException) {
    null
}

private fun invalidIdReject(): InboundOutcome.Reject = InboundOutcome.Reject(
    error = RPCError(
        code = RPCError.ErrorCode.INVALID_REQUEST,
        message = "Invalid Request: id must be a string or an integer",
    ),
    status = HttpStatusCode.BadRequest,
    id = null,
)

/** The request metadata carrying a request-scoped claim, or `null` when this body makes none. */
@OptIn(ExperimentalMcpApi::class)
private fun claimOf(body: JsonObject): RequestMeta? {
    val meta = (body["params"] as? JsonObject)?.get("_meta") as? JsonObject ?: return null
    return RequestMeta(meta).takeIf { PROTOCOL_VERSION_META_KEY in meta }
}

/**
 * The first standard header that is absent or disagrees with [body], or `null` when they all agree.
 *
 * Presence is checked explicitly rather than by comparison: an absent header and an absent body
 * value would otherwise compare equal and mask each other.
 */
@OptIn(ExperimentalMcpApi::class, InternalMcpApi::class)
private fun headerMismatch(
    body: JsonObject,
    method: String,
    meta: RequestMeta,
    headers: Headers,
): RPCError? {
    val version = headers[MCP_PROTOCOL_VERSION_HEADER]
    if (version == null || version != meta.claimedProtocolVersion) {
        return mismatch("$MCP_PROTOCOL_VERSION_HEADER does not match the request envelope's protocol version")
    }
    if (headers[MCP_METHOD_HEADER] != method) {
        return mismatch("$MCP_METHOD_HEADER does not match the request body's method")
    }
    val nameKey = NAME_BEARING_METHODS[method] ?: return null
    val expected = (body["params"] as? JsonObject)?.stringOf(nameKey) ?: return null
    val raw = headers[MCP_NAME_HEADER]
        ?: return mismatch("$MCP_NAME_HEADER is missing but the request body carries a '$nameKey' parameter")
    // A malformed sentinel is itself a mismatch: comparing it literally would accept a value the
    // sender never wrote.
    if (decodeMcpHeaderValue(raw) != expected) {
        return mismatch("$MCP_NAME_HEADER does not match the request body's '$nameKey' parameter")
    }
    return null
}

private fun mismatch(message: String): RPCError = RPCError(code = RPCError.ErrorCode.HEADER_MISMATCH, message = message)

private fun JsonObject.stringOf(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
