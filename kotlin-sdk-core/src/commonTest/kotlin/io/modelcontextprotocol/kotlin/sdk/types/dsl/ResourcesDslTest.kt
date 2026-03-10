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
import io.modelcontextprotocol.kotlin.sdk.types.buildListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildSubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.buildUnsubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class ResourcesDslTest {

    @Test
    fun `buildListResourcesRequest should create request with cursor`() {
        val request = buildListResourcesRequest {
            cursor = "next"
        }
        request.params shouldNotBeNull {
            cursor shouldBe "next"
        }
    }

    @Test
    fun `buildReadResourceRequest should create request with uri`() {
        val request = buildReadResourceRequest {
            uri = "test://resource"
        }
        request.params.uri shouldBe "test://resource"
    }

    @Test
    fun `buildSubscribeRequest should create request with uri`() {
        val request = buildSubscribeRequest {
            uri = "test://resource"
        }
        request.params.uri shouldBe "test://resource"
    }

    @Test
    fun `buildUnsubscribeRequest should create request with uri`() {
        val request = buildUnsubscribeRequest {
            uri = "test://resource"
        }
        request.params.uri shouldBe "test://resource"
    }

    @Test
    fun `buildListResourceTemplatesRequest should create request with cursor`() {
        val request = buildListResourceTemplatesRequest {
            cursor = "template-cursor"
        }
        request.params shouldNotBeNull {
            cursor shouldBe "template-cursor"
        }
    }

    @Test
    fun `buildReadResourceRequest should throw if uri is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildReadResourceRequest { }
        }
    }

    @Test
    fun `buildSubscribeRequest should throw if uri is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildSubscribeRequest { }
        }
    }

    @Test
    fun `buildUnsubscribeRequest should throw if uri is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildUnsubscribeRequest { }
        }
    }

    @Test
    fun `buildListResourcesRequest should create request without params if empty`() {
        val request = buildListResourcesRequest { }
        request.params shouldBe null
    }

    @Test
    fun `buildListResourceTemplatesRequest should create request without params if empty`() {
        val request = buildListResourceTemplatesRequest { }
        request.params shouldBe null
    }

    @Test
    fun `ListResourcesRequest should create request with cursor`() {
        val request = ListResourcesRequest {
            cursor = "next"
        }
        request.params shouldNotBeNull {
            cursor shouldBe "next"
        }
    }

    @Test
    fun `ReadResourceRequest should create request with uri`() {
        val request = ReadResourceRequest {
            uri = "test://resource"
        }
        request.params.uri shouldBe "test://resource"
    }

    @Test
    fun `SubscribeRequest should create request with uri`() {
        val request = SubscribeRequest {
            uri = "test://resource"
        }
        request.params.uri shouldBe "test://resource"
    }

    @Test
    fun `UnsubscribeRequest should create request with uri`() {
        val request = UnsubscribeRequest {
            uri = "test://resource"
        }
        request.params.uri shouldBe "test://resource"
    }

    @Test
    fun `ListResourceTemplatesRequest should create request with cursor`() {
        val request = ListResourceTemplatesRequest {
            cursor = "template-cursor"
        }
        request.params shouldNotBeNull {
            cursor shouldBe "template-cursor"
        }
    }

    @Test
    fun `ReadResourceRequest should throw if uri is missing`() {
        shouldThrow<IllegalArgumentException> {
            ReadResourceRequest { }
        }
    }

    @Test
    fun `SubscribeRequest should throw if uri is missing`() {
        shouldThrow<IllegalArgumentException> {
            SubscribeRequest { }
        }
    }

    @Test
    fun `UnsubscribeRequest should throw if uri is missing`() {
        shouldThrow<IllegalArgumentException> {
            UnsubscribeRequest { }
        }
    }

    @Test
    fun `ListResourcesRequest should create request without params if empty`() {
        val request = ListResourcesRequest { }
        request.params shouldBe null
    }

    @Test
    fun `ListResourceTemplatesRequest should create request without params if empty`() {
        val request = ListResourceTemplatesRequest { }
        request.params shouldBe null
    }
}
