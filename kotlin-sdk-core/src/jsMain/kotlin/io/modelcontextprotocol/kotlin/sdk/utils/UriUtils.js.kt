package io.modelcontextprotocol.kotlin.sdk.utils

private external class URL(url: String) {
    val href: String
}

/**
 * Normalizes [uri] via the browser/Node.js `URL` API, resolving dot segments and
 * normalizing the scheme, host, and path. Returns [uri] unchanged if it cannot be
 * parsed (e.g. relative URIs or schemes unsupported by the runtime).
 */
internal actual fun normalizeUri(uri: String): String = try {
    URL(uri).href
} catch (_: Throwable) {
    uri
}
