package io.modelcontextprotocol.kotlin.sdk.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

public actual val IODispatcher: CoroutineDispatcher
    get() = Dispatchers.IO
