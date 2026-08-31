@file:OptIn(ExperimentalMcpApi::class)

package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.InternalMcpApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.jvm.JvmInline

/**
 * A result already rendered to the shape it will occupy on the wire.
 *
 * Lets a [RequestResult] carry JSON that has already been rendered, so that a message rewritten on
 * its way out still travels as a typed result. It serializes as exactly the object it wraps.
 *
 * @property json the result exactly as it will be sent
 */
@JvmInline
@Serializable
@InternalMcpApi
public value class WireResult(public val json: JsonObject) :
    ClientResult,
    ServerResult {
    override val meta: ResultMeta?
        get() = (json["_meta"] as? JsonObject)?.let(::ResultMeta)
}

/** Result fields introduced by the request-scoped lifecycle, which no earlier peer understands. */
private val REQUEST_SCOPED_RESULT_KEYS: Set<String> = setOf("resultType", "ttlMs", "cacheScope")

/**
 * Renders an encoded result for the lifecycle it is being sent under.
 *
 * On [ProtocolEra.Legacy] the request-scoped fields are removed. On [ProtocolEra.Modern] the server
 * identifies itself in `_meta`, unless [serverInfo] is `null` or the result already names one — an
 * identity a handler wrote wins over the automatic one.
 */
internal fun encodeOutboundResult(era: ProtocolEra, json: JsonObject, serverInfo: Implementation?): JsonObject =
    when (era) {
        ProtocolEra.Legacy -> JsonObject(json.filterKeys { it !in REQUEST_SCOPED_RESULT_KEYS })
        ProtocolEra.Modern -> json.withServerInfoStamp(serverInfo)
    }

private fun JsonObject.withServerInfoStamp(serverInfo: Implementation?): JsonObject {
    if (serverInfo == null) return this
    val meta = (this["_meta"] as? JsonObject).orEmpty()
    if (SERVER_INFO_META_KEY in meta) return this
    val stamped = JsonObject(meta + (SERVER_INFO_META_KEY to McpJson.encodeToJsonElement(serverInfo)))
    return JsonObject(this + ("_meta" to stamped))
}

/**
 * The error code to put on the wire for a locally raised [code].
 *
 * Retired codes are never emitted: `-32002` (resource not found) travels as invalid params under
 * either lifecycle, and `-32042` survives only where URL elicitation does.
 */
@Suppress("DEPRECATION")
internal fun outboundErrorCode(era: ProtocolEra, code: Int): Int = when {
    code == RPCError.ErrorCode.RESOURCE_NOT_FOUND -> RPCError.ErrorCode.INVALID_PARAMS

    era == ProtocolEra.Modern && code == RPCError.ErrorCode.URL_ELICITATION_REQUIRED ->
        RPCError.ErrorCode.INTERNAL_ERROR

    else -> code
}

/**
 * The `resultType` this result declares, or `null` when it declares none.
 *
 * `resultType` is declared per concrete result rather than on [RequestResult], because
 * [EmptyResult] has to be able to omit it. Results that wrap opaque JSON report whatever that JSON
 * says, if anything.
 */
@OptIn(InternalMcpApi::class)
internal val RequestResult.resultTypeOrNull: String?
    get() = when (this) {
        is EmptyResult -> resultType
        is InitializeResult -> resultType
        is DiscoverResult -> resultType
        is CompleteResult -> resultType
        is ListToolsResult -> resultType
        is CallToolResult -> resultType
        is ListPromptsResult -> resultType
        is GetPromptResult -> resultType
        is ListResourcesResult -> resultType
        is ReadResourceResult -> resultType
        is ListResourceTemplatesResult -> resultType
        is ListRootsResult -> resultType
        is CreateMessageResult -> resultType
        is ElicitResult -> resultType
        is CreateTaskResult -> resultType
        is GetTaskResult -> resultType
        is ListTasksResult -> resultType
        is GetTaskPayloadResult -> json.resultTypeString()
        is WireResult -> json.resultTypeString()
    }

private fun JsonObject.resultTypeString(): String? = (this["resultType"] as? JsonPrimitive)
    ?.takeIf { it.isString }
    ?.content

/**
 * Rejects a result whose `resultType` this SDK does not recognize.
 *
 * Checked on [ProtocolEra.Modern] only, where an unknown value means the peer is describing a
 * result shape this SDK cannot interpret; reading it as [COMPLETE_RESULT_TYPE] would hand the caller
 * a partial answer as though it were the whole one. An absent `resultType` always means
 * [COMPLETE_RESULT_TYPE].
 *
 * @throws McpException if [era] is [ProtocolEra.Modern] and [result] declares an unknown `resultType`
 */
internal fun checkInboundResult(era: ProtocolEra, result: RequestResult) {
    if (era != ProtocolEra.Modern) return
    val declared = result.resultTypeOrNull ?: return
    if (declared != COMPLETE_RESULT_TYPE) {
        throw McpException(
            code = RPCError.ErrorCode.INVALID_PARAMS,
            message = "Unrecognized resultType \"$declared\"",
        )
    }
}
