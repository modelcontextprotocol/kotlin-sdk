package io.modelcontextprotocol.kotlin.sdk.utils
/** No platform-native URI normalizer is available on native targets; [uri] is returned unchanged. */
internal actual fun normalizeUri(uri: String): String = uri
