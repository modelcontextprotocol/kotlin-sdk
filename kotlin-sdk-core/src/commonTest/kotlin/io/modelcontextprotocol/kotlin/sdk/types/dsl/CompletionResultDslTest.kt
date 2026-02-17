package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.buildCompleteResult
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

/**
 * Tests for CompleteResult DSL builder.
 *
 * Verifies CompleteResult can be constructed via DSL,
 * covering minimal (required only), full (all fields), and edge cases.
 */
@OptIn(ExperimentalMcpApi::class)
class CompletionResultDslTest {

    @Test
    fun `CompleteResult should build minimal with values only`() {
        val result = buildCompleteResult {
            values("user1", "user2", "user3")
        }

        result.completion.values shouldHaveSize 3
        result.completion.values shouldBe listOf("user1", "user2", "user3")
        result.completion.total.shouldBeNull()
        result.completion.hasMore.shouldBeNull()
        result.meta.shouldBeNull()
    }

    @Test
    fun `CompleteResult should build full with all fields`() {
        val result = buildCompleteResult {
            values(listOf("admin", "moderator", "user", "guest"))
            total = 42
            hasMore = true

            meta {
                put("cached", true)
                put("queryTime", 50)
            }
        }

        result.completion.values shouldHaveSize 4
        result.completion.total shouldBe 42
        result.completion.hasMore shouldBe true

        result.meta shouldNotBeNull {
            get("cached")?.jsonPrimitive?.boolean shouldBe true
            get("queryTime")?.jsonPrimitive?.int shouldBe 50
        }
    }

    @Test
    fun `CompleteResult should support values via vararg`() {
        val result = buildCompleteResult {
            values("a", "b", "c", "d", "e")
        }

        result.completion.values shouldHaveSize 5
    }

    @Test
    fun `CompleteResult should support values via list`() {
        val completions = listOf("option1", "option2", "option3")

        val result = buildCompleteResult {
            values(completions)
        }

        result.completion.values shouldBe completions
    }

    @Test
    fun `CompleteResult should throw if no values provided`() {
        shouldThrow<IllegalArgumentException> {
            buildCompleteResult { }
        }
    }

    @Test
    fun `CompleteResult should throw if values exceed 100 items`() {
        val tooManyValues = (1..101).map { "value$it" }

        shouldThrow<IllegalArgumentException> {
            buildCompleteResult {
                values(tooManyValues)
            }
        }
    }

    @Test
    fun `CompleteResult should support exactly 100 values`() {
        val maxValues = (1..100).map { "value$it" }

        val result = buildCompleteResult {
            values(maxValues)
        }

        result.completion.values shouldHaveSize 100
    }

    @Test
    fun `CompleteResult should support empty strings in values`() {
        val result = buildCompleteResult {
            values("", "non-empty", "")
        }

        result.completion.values shouldBe listOf("", "non-empty", "")
    }

    @Test
    fun `CompleteResult should support unicode in values`() {
        val result = buildCompleteResult {
            values("Hello 🌍", "Ça va?", "北京", "مرحبا")
        }

        result.completion.values shouldHaveSize 4
        result.completion.values[0] shouldBe "Hello 🌍"
    }

    @Test
    fun `CompleteResult should support total without hasMore`() {
        val result = buildCompleteResult {
            values("a", "b", "c")
            total = 10
        }

        result.completion.total shouldBe 10
        result.completion.hasMore.shouldBeNull()
    }

    @Test
    fun `CompleteResult should support hasMore without total`() {
        val result = buildCompleteResult {
            values("a", "b", "c")
            hasMore = true
        }

        result.completion.hasMore shouldBe true
        result.completion.total.shouldBeNull()
    }
}
