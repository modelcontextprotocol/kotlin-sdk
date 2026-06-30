package io.modelcontextprotocol.kotlin.sdk.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseUrl
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError

/**
 * Default list of hostnames allowed for localhost DNS rebinding protection.
 * Matches the TypeScript SDK's `localhostAllowedHostnames()`.
 */
internal val LOCALHOST_ALLOWED_HOSTS: List<String> = listOf("localhost", "127.0.0.1", "[::1]")

/**
 * Default list of `Origin` values allowed for localhost DNS rebinding protection.
 *
 * Mirrors [LOCALHOST_ALLOWED_HOSTS] but carries a scheme so the values parse as URLs:
 * [extractOriginHost] rejects schemeless input. Comparison is hostname-only and
 * scheme-agnostic, so these entries also match `https://` origins on the same host.
 */
internal val LOCALHOST_ALLOWED_ORIGINS: List<String> =
    listOf("http://localhost", "http://127.0.0.1", "http://[::1]")

/**
 * Characters that are valid in a URL but must not appear in an HTTP `Host` header.
 * Rejecting them prevents the parser from accepting malformed values
 * (e.g. `evil.com@localhost`, `host/path`) that a generic URL parser would silently allow.
 */
private val FORBIDDEN_HOST_CHARS: CharArray = charArrayOf('/', '@', '?', '#')

/**
 * Extracts the hostname from a Host header value, stripping the port.
 *
 * Only accepts the strict `host [ ":" port ]` / `"[" ipv6 "]" [ ":" port ]`
 * format defined by RFC 7230. Values containing URL-only characters
 * (`/`, `@`, `?`, `#`) or whitespace are rejected.
 *
 * Examples:
 * - `"localhost:3000"` → `"localhost"`
 * - `"127.0.0.1:8080"` → `"127.0.0.1"`
 * - `"[::1]:3000"` → `"[::1]"`
 * - `"example.com"` → `"example.com"`
 * - `"evil.com@localhost"` → `null`
 *
 * @return the hostname, or `null` if the value is blank, malformed, or contains forbidden characters.
 */
internal fun extractHostname(hostHeader: String): String? = when {
    hostHeader.isBlank() -> null

    hostHeader.any { it in FORBIDDEN_HOST_CHARS || it.isWhitespace() } -> null

    hostHeader.startsWith("[") -> {
        val end = hostHeader.indexOf(']')
        when {
            end <= 1 -> null
            isValidPortSuffix(hostHeader.substring(end + 1)) -> hostHeader.substring(0, end + 1)
            else -> null
        }
    }

    else -> {
        val colon = hostHeader.indexOf(':')
        when {
            colon < 0 -> hostHeader
            colon == 0 -> null
            isValidPortSuffix(hostHeader.substring(colon)) -> hostHeader.substring(0, colon)
            else -> null
        }
    }
}

/**
 * Checks that [tail] is either empty (no port part) or `":"` followed by zero or more digits,
 * matching RFC 7230 §3.2.3 where `port = *DIGIT`. Non-empty non-digit characters are rejected.
 */
private fun isValidPortSuffix(tail: String): Boolean = when {
    tail.isEmpty() -> true
    tail[0] != ':' -> false
    else -> tail.substring(1).all { it.isDigit() }
}

/**
 * Extracts the hostname from an `Origin` header value.
 *
 * Uses Ktor's [parseUrl] to avoid reimplementing RFC 6454 parsing.
 * Scheme, port, userinfo, and path are discarded — only the host matters for
 * DNS-rebinding protection. The result is **not** normalized to lower-case;
 * callers should apply [String.lowercase] if they need case-insensitive comparison.
 *
 * @return the hostname, or `null` if [origin] cannot be parsed or has no host part.
 */
internal fun extractOriginHost(origin: String): String? = parseUrl(origin)?.host?.takeIf(String::isNotEmpty)

/**
 * Configuration for the [DnsRebindingProtection] Ktor route-scoped plugin.
 *
 * @property allowedHosts List of hostnames allowed in the `Host` header.
 *     Comparison is port-agnostic and case-insensitive.
 *     Defaults to `localhost`, `127.0.0.1`, `[::1]`.
 *     An empty list will reject **all** requests.
 * @property allowedOrigins Optional list of allowed `Origin` values. Entries are parsed as URLs
 *     (via [parseUrl]) and compared by **hostname only** — scheme and port are ignored.
 *     If `null`, origin validation is disabled.
 *     If configured, requests **with** an `Origin` header whose hostname is not in the list
 *     are rejected, but requests **without** an `Origin` header are allowed (non-browser clients).
 */
public class DnsRebindingProtectionConfig {
    public var allowedHosts: List<String> = LOCALHOST_ALLOWED_HOSTS
    public var allowedOrigins: List<String>? = null
}

/**
 * Ktor route-scoped plugin that validates `Host` and `Origin` headers
 * to protect against DNS rebinding attacks.
 *
 * Install on a route to intercept all requests **before** handlers:
 * ```kotlin
 * route("/mcp") {
 *     install(DnsRebindingProtection) {
 *         allowedHosts = listOf("myapp.com", "localhost")
 *     }
 *     // handlers...
 * }
 * ```
 */
public val DnsRebindingProtection: RouteScopedPlugin<DnsRebindingProtectionConfig> =
    createRouteScopedPlugin(
        "MCP-DnsRebindingProtection",
        ::DnsRebindingProtectionConfig,
    ) {
        val hosts: Set<String> = pluginConfig.allowedHosts.mapTo(mutableSetOf()) {
            extractHostname(it)?.lowercase()
                ?: error("Invalid host in DnsRebindingProtection.allowedHosts: '$it'")
        }
        val originHosts: Set<String>? = pluginConfig.allowedOrigins?.mapTo(mutableSetOf()) {
            extractOriginHost(it)?.lowercase()
                ?: error("Invalid origin in DnsRebindingProtection.allowedOrigins: '$it'")
        }

        onCall { call ->
            val hostHeader = call.request.header(HttpHeaders.Host)
            val hostname = hostHeader?.let { extractHostname(it) }?.lowercase()

            if (hostname == null) {
                call.rejectDnsValidation("Invalid Host header: (malformed or missing)")
                return@onCall
            }
            if (hostname !in hosts) {
                call.rejectDnsValidation("Invalid Host: $hostname")
                return@onCall
            }

            if (originHosts != null) {
                val originHeader = call.request.header(HttpHeaders.Origin)
                // Allow requests without Origin (non-browser clients cannot perform DNS rebinding)
                if (originHeader != null) {
                    val originHost = extractOriginHost(originHeader)?.lowercase()
                    if (originHost == null) {
                        call.rejectDnsValidation("Invalid Origin header: (unparseable)")
                        return@onCall
                    }
                    if (originHost !in originHosts) {
                        call.rejectDnsValidation("Invalid Origin host: $originHost")
                        return@onCall
                    }
                }
            }
        }
    }

/**
 * Responds with a 403 Forbidden JSON-RPC error without requiring ContentNegotiation.
 */
private suspend fun ApplicationCall.rejectDnsValidation(message: String) {
    val error = JSONRPCError(
        id = null,
        error = RPCError(
            code = -32_000,
            message = message,
        ),
    )
    respondText(
        McpJson.encodeToString(error),
        ContentType.Application.Json,
        HttpStatusCode.Forbidden,
    )
}
