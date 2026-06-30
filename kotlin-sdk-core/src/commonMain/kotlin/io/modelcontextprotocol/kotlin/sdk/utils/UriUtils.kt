package io.modelcontextprotocol.kotlin.sdk.utils

/**
 * Normalizes [uri] by resolving dot-segments (`/a/b/../c` → `/a/c`) using the
 * platform-native URI parser.
 *
 * On JVM uses [java.net.URI.normalize]. On JS uses the browser/Node.js `URL` API.
 * On native and WASM targets [uri] is returned unchanged — no platform normalizer
 * is available, so callers on those targets do not receive dot-segment resolution.
 *
 * Returns [uri] unchanged if it cannot be parsed or normalized.
 *
 * **Security note**: call this before splitting a URI into segments to prevent
 * dot-segment traversal attacks (e.g. `public/../private/secret`).
 */
internal expect fun normalizeUri(uri: String): String
