package io.modelcontextprotocol.kotlin.sdk.utils

import java.net.URI
import java.net.URISyntaxException

/**
 * Normalizes [uri] by resolving dot segments (e.g. `/a/b/../c` → `/a/c`) via [URI.normalize].
 * Returns [uri] unchanged if it is not a valid URI.
 */
internal actual fun normalizeUri(uri: String): String = try {
    URI(uri).normalize().toString()
} catch (_: URISyntaxException) {
    uri
}
