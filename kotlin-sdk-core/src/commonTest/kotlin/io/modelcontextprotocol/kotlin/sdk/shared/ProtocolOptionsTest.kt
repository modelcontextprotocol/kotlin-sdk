package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test

class ProtocolOptionsTest {

    @Test
    fun `defaults expose concurrency knobs`() {
        val options = ProtocolOptions()
        options.handlerCoroutineContext shouldBe Dispatchers.Default
        options.maxConcurrentHandlers shouldBe 64
        options.maxInFlightHandlers shouldBe 256
    }

    @Test
    fun `knobs are settable via constructor`() {
        val options = ProtocolOptions(
            handlerCoroutineContext = Dispatchers.Unconfined,
            maxConcurrentHandlers = 2,
            maxInFlightHandlers = 4,
        )
        options.handlerCoroutineContext shouldBe Dispatchers.Unconfined
        options.maxConcurrentHandlers shouldBe 2
        options.maxInFlightHandlers shouldBe 4
    }
}
