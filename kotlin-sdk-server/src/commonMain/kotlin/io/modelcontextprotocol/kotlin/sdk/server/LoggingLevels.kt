package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.LOG_LEVEL_META_KEY
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import io.modelcontextprotocol.kotlin.sdk.types.isModernProtocolVersion
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * The `notifications/message` severities a server may emit while serving one request.
 *
 * The two lifecycles read opposite defaults, which is why this is computed per request:
 *
 * - Connection-scoped: every level is allowed here, and narrowing them stays the application's
 *   `logging/setLevel` concern.
 * - Request-scoped: a request opts in by carrying [LOG_LEVEL_META_KEY] and gets that level and
 *   everything more severe. A request that does not carry it gets nothing, and a level some earlier
 *   request set never leaks into it.
 */
@OptIn(ExperimentalMcpApi::class)
internal fun allowedLogLevels(protocolVersion: String, meta: RequestMeta?): Set<LoggingLevel> {
    if (!isModernProtocolVersion(protocolVersion)) return LoggingLevel.entries.toSet()
    val requested = meta?.get(LOG_LEVEL_META_KEY)?.let { level ->
        try {
            McpJson.decodeFromJsonElement<LoggingLevel>(level)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            // kotlinx reports some structural mismatches this way; either shape is unusable.
            null
        }
    } ?: return emptySet()
    return LoggingLevel.entries.filterTo(mutableSetOf()) { it >= requested }
}
