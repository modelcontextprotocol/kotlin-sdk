package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MethodTest {

    @Test
    fun `from should return Defined entry for known method strings`() {
        Method.from("tools/call") shouldBe Method.Defined.ToolsCall
        Method.from("initialize") shouldBe Method.Defined.Initialize
        Method.from("notifications/cancelled") shouldBe Method.Defined.NotificationsCancelled
    }

    @Test
    fun `from should return Custom for unknown method strings`() {
        Method.from("my/custom") shouldBe Method.Custom("my/custom")
    }
}
