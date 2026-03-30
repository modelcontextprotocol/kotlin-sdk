package io.modelcontextprotocol.kotlin.sdk.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
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
 * Extracts the hostname from a Host header value, stripping port and normalizing IPv6.
 *
 * Examples:
 * - `"localhost:3000"` → `"localhost"`
 * - `"127.0.0.1:8080"` → `"127.0.0.1"`
 * - `"[::1]:3000"` → `"[::1]"`
 * - `"example.com"` → `"example.com"`
 *
 * @return the hostname, or `null` if parsing fails.
 */
internal fun extractHostname(hostHeader: String): String? {
    if (hostHeader.isBlank()) return null
    return try {
        URLBuilder("http://$hostHeader").build().host.ifEmpty { null }
    } catch (_: Exception) {
        null
    }
}

/**
 * Configuration for the [DnsRebindingProtection] Ktor route-scoped plugin.
 *
 * @property allowedHosts List of hostnames allowed in the `Host` header.
 *     Comparison is port-agnostic and case-insensitive.
 *     Defaults to [LOCALHOST_ALLOWED_HOSTS].
 *     An empty list will reject **all** requests.
 * @property allowedOrigins Optional list of allowed `Origin` header values.
 *     If `null`, origin validation is disabled.
 *     If configured, requests **with** an `Origin` header not in the list are rejected,
 *     but requests **without** an `Origin` header are allowed (non-browser clients).
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
            extractHostname(it)?.lowercase() ?: it.lowercase()
        }
        val origins: Set<String>? = pluginConfig.allowedOrigins?.mapTo(mutableSetOf()) { it.lowercase() }

        onCall { call ->
            val hostHeader = call.request.header(HttpHeaders.Host)
            val hostname = hostHeader?.let { extractHostname(it) }?.lowercase()

            if (hostname == null || hostname !in hosts) {
                call.rejectDnsValidation("Invalid Host header: $hostHeader")
                return@onCall
            }

            if (origins != null) {
                val origin = call.request.header(HttpHeaders.Origin)?.lowercase()
                // Allow requests without Origin (non-browser clients cannot perform DNS rebinding)
                if (origin != null && origin !in origins) {
                    call.rejectDnsValidation("Invalid Origin header: $origin")
                    return@onCall
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
            code = RPCError.ErrorCode.CONNECTION_CLOSED,
            message = message,
        ),
    )
    respondText(
        McpJson.encodeToString(error),
        ContentType.Application.Json,
        HttpStatusCode.Forbidden,
    )
}
