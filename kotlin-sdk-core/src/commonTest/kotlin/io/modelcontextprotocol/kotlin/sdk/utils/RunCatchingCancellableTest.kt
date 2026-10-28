package io.modelcontextprotocol.kotlin.sdk.utils

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RunCatchingCancellableTest {

    @Test
    fun `should capture a regular throwable as failure`() {
        val result = runCatchingCancellable { throw IllegalStateException("boom") }

        result.exceptionOrNull().shouldBeInstanceOf<IllegalStateException>().message shouldBe "boom"
    }

    @Test
    fun `should not let a cancelled coroutine continue past the call`() = runTest {
        val entered = CompletableDeferred<Unit>()
        var reachedAfterCall = false

        val job = launch {
            runCatchingCancellable {
                entered.complete(Unit)
                awaitCancellation()
            }
            reachedAfterCall = true
        }
        entered.await()
        job.cancelAndJoin()

        reachedAfterCall shouldBe false
    }
}
