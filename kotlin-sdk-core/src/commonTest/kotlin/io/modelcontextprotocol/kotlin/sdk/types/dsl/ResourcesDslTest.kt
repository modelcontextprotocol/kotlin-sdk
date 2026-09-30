package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.buildReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildSubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildUnsubscribeRequest
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class ResourcesDslTest {
    @Test
    fun `uri builders should set uri and require it`() {
        val builders = mapOf<String, (String?) -> String>(
            "read" to { value -> buildReadResourceRequest { uri = value }.params.uri },
            "subscribe" to { value -> buildSubscribeRequest { uri = value }.params.uri },
            "unsubscribe" to { value -> buildUnsubscribeRequest { uri = value }.params.uri },
        )

        builders.forEach { (name, build) ->
            withClue(name) {
                build("test://resource") shouldBe "test://resource"
                shouldThrow<IllegalArgumentException> { build(null) }.message shouldContain "'uri'"
            }
        }
    }
}
