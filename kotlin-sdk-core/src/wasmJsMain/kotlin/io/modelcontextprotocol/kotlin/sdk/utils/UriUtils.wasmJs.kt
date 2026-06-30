package io.modelcontextprotocol.kotlin.sdk.utils

/** No platform-native URI normalizer is available on WASM targets; [uri] is returned unchanged. */
internal actual fun normalizeUri(uri: String): String = uri
