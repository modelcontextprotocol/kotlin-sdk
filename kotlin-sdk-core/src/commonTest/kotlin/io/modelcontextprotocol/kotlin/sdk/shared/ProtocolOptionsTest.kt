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
}
