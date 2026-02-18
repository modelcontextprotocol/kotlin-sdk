package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.matchers.nulls.shouldNotBeNull
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class RootsDslTest {
    @Test
    fun `buildListRootsRequest should create request with meta`() {
        val request = ListRootsRequest {
            meta {
                put("test", "value")
            }
        }
        request.params.shouldNotBeNull()
    }
}
