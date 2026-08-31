package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.InternalMcpApi
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** HTTP header restating the protocol version a request is made under. */
@InternalMcpApi
public const val MCP_PROTOCOL_VERSION_HEADER: String = "MCP-Protocol-Version"

/** HTTP header restating a request's JSON-RPC method. */
@InternalMcpApi
public const val MCP_METHOD_HEADER: String = "Mcp-Method"

/** HTTP header restating the tool, prompt or resource a request names. */
@InternalMcpApi
public const val MCP_NAME_HEADER: String = "Mcp-Name"

private const val MCP_BASE64_PREFIX = "=?base64?"
private const val MCP_BASE64_SUFFIX = "?="

/**
 * The params key whose value [MCP_NAME_HEADER] restates, per method.
 *
 * Selected by method rather than by whichever of `name` or `uri` happens to be present, so that
 * both ends compare the same field. A method absent from this map sends and expects no
 * [MCP_NAME_HEADER].
 */
@InternalMcpApi
public val NAME_BEARING_METHODS: Map<String, String> = mapOf(
    Method.Defined.ToolsCall.value to "name",
    Method.Defined.PromptsGet.value to "name",
    Method.Defined.ResourcesRead.value to "uri",
)

/**
 * Renders a value for an MCP HTTP header, escaping it when it cannot travel literally.
 *
 * HTTP field values admit only visible ASCII and tabs, and edge whitespace is not preserved across
 * intermediaries, so anything else is wrapped in the `=?base64?…?=` sentinel. A value that already
 * looks like the sentinel is escaped too, so a receiver can always tell the two apart.
 */
@OptIn(ExperimentalEncodingApi::class)
@InternalMcpApi
public fun String.encodeMcpHeaderValue(): String {
    val containsUnsafeCharacters = any { it != '\t' && it.code !in 0x20..0x7e }
    val hasEdgeWhitespace = firstOrNull()?.isWhitespace() == true || lastOrNull()?.isWhitespace() == true
    val matchesBase64Sentinel = startsWith(MCP_BASE64_PREFIX) && endsWith(MCP_BASE64_SUFFIX)

    if (!containsUnsafeCharacters && !hasEdgeWhitespace && !matchesBase64Sentinel) return this

    return "$MCP_BASE64_PREFIX${Base64.encode(encodeToByteArray())}$MCP_BASE64_SUFFIX"
}

/**
 * Reads a header value written by [encodeMcpHeaderValue], or `null` when the sentinel is malformed.
 *
 * Surrounding optional whitespace is stripped first, per RFC 9110 §5.5. A malformed sentinel is
 * reported rather than compared literally, so a receiver never accepts a value the sender did not
 * write.
 */
@OptIn(ExperimentalEncodingApi::class)
@InternalMcpApi
public fun decodeMcpHeaderValue(raw: String): String? {
    val trimmed = raw.trim(' ', '\t')
    if (!trimmed.startsWith(MCP_BASE64_PREFIX) || !trimmed.endsWith(MCP_BASE64_SUFFIX)) return trimmed
    if (trimmed.length < MCP_BASE64_PREFIX.length + MCP_BASE64_SUFFIX.length) return null
    val payload = trimmed.substring(MCP_BASE64_PREFIX.length, trimmed.length - MCP_BASE64_SUFFIX.length)
    return try {
        Base64.decode(payload).decodeToString()
    } catch (_: IllegalArgumentException) {
        null
    }
}
