package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.SetLevelRequest
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class LoggingDslTest {
    @Test
    fun `buildSetLevelRequest should create request with given level`() {
        val request = SetLevelRequest {
            loggingLevel = LoggingLevel.Info
        }

        request.params.level shouldBe LoggingLevel.Info
    }

    @Test
    fun `buildSetLevelRequest should throw if loggingLevel is missing`() {
        shouldThrow<IllegalArgumentException> {
            SetLevelRequest { }
        }
    }
}
