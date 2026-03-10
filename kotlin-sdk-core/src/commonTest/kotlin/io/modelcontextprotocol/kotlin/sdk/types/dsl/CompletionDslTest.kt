package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.CompleteRequest
import io.modelcontextprotocol.kotlin.sdk.types.PromptReference
import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplateReference
import io.modelcontextprotocol.kotlin.sdk.types.buildCompleteRequest
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class CompletionDslTest {
    @Test
    fun `buildCompleteRequest should build with prompt reference and context`() {
        val request = buildCompleteRequest {
            argument("query", "user input")
            ref(PromptReference("searchPrompt"))
            context {
                put("userId", "123")
            }
        }

        request.params.argument.name shouldBe "query"
        request.params.argument.value shouldBe "user input"
        (request.params.ref as PromptReference).name shouldBe "searchPrompt"
        request.params.context shouldNotBeNull {
            arguments?.get("userId") shouldBe "123"
        }
    }

    @Test
    fun `buildCompleteRequest should build with resource template reference and map context`() {
        val request = buildCompleteRequest {
            argument("path", "/users/123")
            ref(ResourceTemplateReference("file:///{path}"))
            context(mapOf("role" to "admin"))
        }

        request.params.argument.name shouldBe "path"
        request.params.argument.value shouldBe "/users/123"
        (request.params.ref as ResourceTemplateReference).uri shouldBe "file:///{path}"
        request.params.context shouldNotBeNull {
            arguments?.get("role") shouldBe "admin"
        }
    }

    @Test
    fun `buildCompleteRequest should throw if argument is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCompleteRequest {
                ref(PromptReference("name"))
            }
        }
    }

    @Test
    fun `buildCompleteRequest should throw if ref is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildCompleteRequest {
                argument("name", "value")
            }
        }
    }

    @Test
    fun `CompleteRequest should build with prompt reference and context`() {
        val request = CompleteRequest {
            argument("query", "user input")
            ref(PromptReference("searchPrompt"))
            context {
                put("userId", "123")
            }
        }

        request.params.argument.name shouldBe "query"
        request.params.argument.value shouldBe "user input"
        (request.params.ref as PromptReference).name shouldBe "searchPrompt"
        request.params.context shouldNotBeNull {
            arguments?.get("userId") shouldBe "123"
        }
    }

    @Test
    fun `CompleteRequest should build with resource template reference and map context`() {
        val request = CompleteRequest {
            argument("path", "/users/123")
            ref(ResourceTemplateReference("file:///{path}"))
            context(mapOf("role" to "admin"))
        }

        request.params.argument.name shouldBe "path"
        request.params.argument.value shouldBe "/users/123"
        (request.params.ref as ResourceTemplateReference).uri shouldBe "file:///{path}"
        request.params.context shouldNotBeNull {
            arguments?.get("role") shouldBe "admin"
        }
    }

    @Test
    fun `CompleteRequest should throw if argument is missing`() {
        shouldThrow<IllegalArgumentException> {
            CompleteRequest {
                ref(PromptReference("name"))
            }
        }
    }

    @Test
    fun `CompleteRequest should throw if ref is missing`() {
        shouldThrow<IllegalArgumentException> {
            CompleteRequest {
                argument("name", "value")
            }
        }
    }
}
