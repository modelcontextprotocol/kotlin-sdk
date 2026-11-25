package io.modelcontextprotocol.kotlin.sdk.utils

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate
import kotlin.test.Test

class RepeatedVariableTemplateMatcherTest {
    @Test
    fun `should reject different values for a repeated name`() {
        matcher("test://items/{id}/{id}").match("test://items/42/43") shouldBe null
    }

    @Test
    fun `should retain the value and score for equal repeated captures`() {
        matcher("test://items/{id}/{id}").match("test://items/42/42") shouldBe
            MatchResult(mapOf("id" to "42"), score = 8)
    }

    @Test
    fun `should compare decoded values for repeated names`() {
        matcher("test://items/{id}/{id}").match("test://items/42/%34%32")?.variables shouldBe
            mapOf("id" to "42")
    }

    @Test
    fun `should reject a different third capture`() {
        matcher("items/{id}/{id}/{id}").match("items/42/42/43") shouldBe null
    }

    @Test
    fun `should allow independent values for distinct names`() {
        matcher("items/{left}/{right}").match("items/42/43")?.variables shouldBe
            mapOf("left" to "42", "right" to "43")
    }

    @Test
    fun `should not decode repeated values a second time`() {
        val matcher = matcher("items/{id}/{id}")
        matcher.match("items/%252F/%2F") shouldBe null
        matcher.match("items/%252F/%252F")?.variables shouldBe mapOf("id" to "%2F")
    }

    private fun matcher(template: String): PathSegmentTemplateMatcher =
        PathSegmentTemplateMatcher(ResourceTemplate(template, "Test"))
}
