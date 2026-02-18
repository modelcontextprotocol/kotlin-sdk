package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.UnsubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class ResourcesDslTest {
    @Test
    fun `buildListResourcesRequest should create request with cursor`() {
        val request = ListResourcesRequest {
            cursor = "next"
        }
        request.params shouldNotBeNull {
            cursor shouldBe "next"
        }
    }

    @Test
    fun `buildReadResourceRequest should create request with uri`() {
        val request = ReadResourceRequest {
            uri = "test://resource"
        }
        request.params.uri shouldBe "test://resource"
    }

    @Test
    fun `buildSubscribeRequest should create request with uri`() {
        val request = SubscribeRequest {
            uri = "test://resource"
        }
        request.params.uri shouldBe "test://resource"
    }

    @Test
    fun `buildUnsubscribeRequest should create request with uri`() {
        val request = UnsubscribeRequest {
            uri = "test://resource"
        }
        request.params.uri shouldBe "test://resource"
    }

    @Test
    fun `buildListResourceTemplatesRequest should create request with cursor`() {
        val request = ListResourceTemplatesRequest {
            cursor = "template-cursor"
        }
        request.params shouldNotBeNull {
            cursor shouldBe "template-cursor"
        }
    }

    @Test
    fun `buildReadResourceRequest should throw if uri is missing`() {
        shouldThrow<IllegalArgumentException> {
            ReadResourceRequest { }
        }
    }

    @Test
    fun `buildSubscribeRequest should throw if uri is missing`() {
        shouldThrow<IllegalArgumentException> {
            SubscribeRequest { }
        }
    }

    @Test
    fun `buildUnsubscribeRequest should throw if uri is missing`() {
        shouldThrow<IllegalArgumentException> {
            UnsubscribeRequest { }
        }
    }

    @Test
    fun `buildListResourcesRequest should create request without params if empty`() {
        val request = ListResourcesRequest { }
        request.params shouldBe null
    }

    @Test
    fun `buildListResourceTemplatesRequest should create request without params if empty`() {
        val request = ListResourceTemplatesRequest { }
        request.params shouldBe null
    }
}
