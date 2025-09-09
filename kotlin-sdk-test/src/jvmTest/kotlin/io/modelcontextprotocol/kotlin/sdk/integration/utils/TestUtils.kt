package io.modelcontextprotocol.kotlin.sdk.integration.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object TestUtils {
    fun <T> runTest(block: suspend () -> T): T = runBlocking {
        withContext(Dispatchers.IO) {
            block()
        }
    }
}
