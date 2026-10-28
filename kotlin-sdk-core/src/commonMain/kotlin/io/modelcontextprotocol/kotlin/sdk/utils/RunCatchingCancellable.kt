package io.modelcontextprotocol.kotlin.sdk.utils

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but re-throws [CancellationException] instead of capturing it into a failed
 * [Result]: swallowing cancellation would let a cancelled coroutine keep running.
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
