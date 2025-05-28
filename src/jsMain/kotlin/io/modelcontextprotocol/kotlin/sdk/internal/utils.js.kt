package io.modelcontextprotocol.kotlin.sdk.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val IODispatcher: CoroutineDispatcher
    get() = Dispatchers.Default