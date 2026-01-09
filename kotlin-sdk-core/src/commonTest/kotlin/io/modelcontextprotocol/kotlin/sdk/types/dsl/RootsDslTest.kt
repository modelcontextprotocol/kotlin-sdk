package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.matchers.nulls.shouldNotBeNull
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.buildListRootsRequest
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class RootsDslTest {
    @Test
    fun `buildListRootsRequest should create request with meta`() {
        val request = buildListRootsRequest {
            meta {
                put("test", "value")
            }
        }
        request.params.shouldNotBeNull()
    }
}
