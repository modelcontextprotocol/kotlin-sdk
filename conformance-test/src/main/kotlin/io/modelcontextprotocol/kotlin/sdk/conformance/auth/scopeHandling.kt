package io.modelcontextprotocol.kotlin.sdk.conformance.auth

internal fun extractParam(wwwAuth: String, param: String): String? {
    val regex = Regex("""$param="([^"]+)"""")
    return regex.find(wwwAuth)?.groupValues?.get(1)
}

/**
 * Select scope per MCP spec priority:
 * 1. scope from WWW-Authenticate header
 * 2. scopes_supported from Protected Resource Metadata (space-joined)
 * 3. null (omit scope entirely)
 */
internal fun selectScope(wwwAuthScope: String?, scopesSupported: List<String>?): String? {
    if (wwwAuthScope != null) return wwwAuthScope
    if (!scopesSupported.isNullOrEmpty()) return scopesSupported.joinToString(" ")
    return null
}

/**
 * Detect 403 with error="insufficient_scope" and extract the new scope.
 * Returns the scope string if step-up is needed, null otherwise.
 */
internal fun parseStepUpScope(wwwAuth: String?): String? {
    if (wwwAuth == null) return null
    val error = extractParam(wwwAuth, "error")
    if (error != "insufficient_scope") return null
    return extractParam(wwwAuth, "scope")
}
