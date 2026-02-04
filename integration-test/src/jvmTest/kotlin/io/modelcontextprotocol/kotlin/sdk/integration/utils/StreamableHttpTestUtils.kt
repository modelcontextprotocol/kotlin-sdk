package io.modelcontextprotocol.kotlin.sdk.integration.utils

import io.ktor.http.Headers
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Utility helpers for Streamable HTTP transport integration tests. */
@OptIn(ExperimentalUuidApi::class)
object StreamableHttpTestUtils {

    private const val MCP_SESSION_ID_HEADER = "mcp-session-id"
    private const val MCP_PROTOCOL_VERSION_HEADER = "mcp-protocol-version"
    private const val MCP_RESUMPTION_TOKEN_HEADER = "Last-Event-ID"

    fun validateSessionId(sessionId: String): Boolean = try {
        Uuid.parse(sessionId)
        true
    } catch (e: IllegalArgumentException) {
        false
    }

    fun extractSessionIdFromHeaders(headers: Headers): String? = headers[MCP_SESSION_ID_HEADER]

    fun extractProtocolVersionFromHeaders(headers: Headers): String? = headers[MCP_PROTOCOL_VERSION_HEADER]

    fun extractLastEventIdFromHeaders(headers: Headers): String? = headers[MCP_RESUMPTION_TOKEN_HEADER]

    fun generateSessionId(): String = Uuid.random().toString()

    fun isWithinMessageSizeLimit(messageSize: Int): Boolean = messageSize <= MAXIMUM_MESSAGE_SIZE

    fun getMaximumMessageSize(): Int = MAXIMUM_MESSAGE_SIZE

    private const val MAXIMUM_MESSAGE_SIZE = 4 * 1024 * 1024 // 4 MB
}
