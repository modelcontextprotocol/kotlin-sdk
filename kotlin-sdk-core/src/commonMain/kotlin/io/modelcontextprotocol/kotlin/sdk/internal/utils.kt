package io.modelcontextprotocol.kotlin.sdk.internal

import kotlinx.coroutines.CoroutineDispatcher

/** Platform-specific [CoroutineDispatcher] for I/O-bound operations. */
public expect val IODispatcher: CoroutineDispatcher
