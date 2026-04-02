package io.modelcontextprotocol.kotlin.sdk.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Platform-specific [CoroutineDispatcher] for I/O-bound operations. */
public actual val IODispatcher: CoroutineDispatcher
    get() = Dispatchers.IO
