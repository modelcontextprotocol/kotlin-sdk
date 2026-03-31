package io.modelcontextprotocol.kotlin.sdk.utils

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * Unit tests for [UriTemplateMatcher] and [UriTemplate.match] / [UriTemplate.matcher].
 */
class UriTemplateMatcherTest {

    //region match() – variable extraction ─────────────────────────────────────

    @Test
    fun `match returns variable values for simple template`() {
        val result = UriTemplate("https://example.com/users/{id}").match("https://example.com/users/42")
        result shouldNotBeNull {
            variables shouldBe mapOf("id" to "42")
        }
    }

    @Test
    fun `match returns multiple variables`() {
        val result = UriTemplate("https://api.example.com/users/{userId}/posts/{postId}")
            .match("https://api.example.com/users/alice/posts/99")
        result shouldNotBeNull {
            variables shouldBe mapOf("userId" to "alice", "postId" to "99")
        }
    }

    @Test
    fun `match returns null when URI does not match template`() {
        UriTemplate("https://example.com/items/{id}").match("https://example.com/other/42") shouldBe null
    }

    @Test
    fun `match works with custom scheme template`() {
        val result = UriTemplate("test://template/{id}/data").match("test://template/abc123/data")
        result shouldNotBeNull {
            variables shouldBe mapOf("id" to "abc123")
        }
    }

    @Test
    fun `match returns empty variables for literal-only template`() {
        val result = UriTemplate("https://example.com/static").match("https://example.com/static")
        result shouldNotBeNull {
            variables shouldBe emptyMap()
        }
    }

    @Test
    fun `match returns null when URI is shorter than template`() {
        UriTemplate("https://example.com/a/{id}/b").match("https://example.com/a/") shouldBe null
    }

    @Test
    fun `match operator get returns extracted variable value`() {
        val result = UriTemplate("{id}").match("42")
        result shouldNotBeNull {
            get("id") shouldBe "42"
            get("missing") shouldBe null
        }
    }

    //endregion
    //region UriTemplate.matches() companion ───────────────────────────────────

    @Test
    fun `companion matches returns true when URI fits template`() {
        UriTemplate.matches("test://items/{id}", "test://items/42") shouldBe true
    }

    @Test
    fun `companion matches returns false when URI does not fit template`() {
        UriTemplate.matches("test://items/{id}", "test://other/42") shouldBe false
    }

    //endregion
    //region matcher() ─────────────────────────────────────────────────────────

    @Test
    fun `matcher returns a UriTemplateMatcher consistent with match`() {
        val tmpl = UriTemplate("test://items/{id}")
        val matcher = tmpl.matcher()
        matcher.match("test://items/42") shouldBe tmpl.match("test://items/42")
    }

    @Test
    fun `matcher is cached - same instance on repeated calls`() {
        val tmpl = UriTemplate("test://items/{id}")
        tmpl.matcher() shouldBe tmpl.matcher()
    }

    @Test
    fun `match score equals template literalLength`() {
        val tmpl = UriTemplate("https://example.com/users/{id}")
        tmpl.match("https://example.com/users/42")!!.score shouldBe tmpl.literalLength
    }

    @Test
    fun `matches returns true for matching URI and false otherwise`() {
        val matcher = UriTemplate("test://items/{id}").matcher()
        matcher.matches("test://items/42") shouldBe true
        matcher.matches("test://other/42") shouldBe false
    }

    @Test
    fun `matches returns false when URI has extra path segments beyond template`() {
        // Verifies the regex is anchored end-to-end; a prefix match must not be accepted
        val matcher = UriTemplate("test://items/{id}").matcher()
        matcher.matches("test://items/42/extra") shouldBe false
        matcher.matches("test://items/42?query=1") shouldBe false
    }

    //endregion
    //region multi-variable expression matching ────────────────────────────────

    @Test
    fun `match extracts all variables from multi-variable query expression`() {
        val result = UriTemplate("test://search{?x,y}").match("test://search?x=1024&y=768")
        result shouldNotBeNull {
            variables shouldBe mapOf("x" to "1024", "y" to "768")
        }
    }

    @Test
    fun `match extracts all variables from multi-variable path expression`() {
        val result = UriTemplate("test://items/{a},{b}").match("test://items/foo,bar")
        result shouldNotBeNull {
            variables shouldBe mapOf("a" to "foo", "b" to "bar")
        }
    }

    //endregion
    //region literalLength / score ──────────────────────────────────────────────

    @Test
    fun `literalLength counts only literal characters`() {
        // "https://example.com/users/" = 26 chars
        UriTemplate("https://example.com/users/{id}").literalLength shouldBe 26
    }

    @Test
    fun `more specific template has higher score than generic one`() {
        val generic = UriTemplate("test://items/{id}")
        val specific = UriTemplate("test://items/special")

        val uri = "test://items/special"
        val genericResult = generic.match(uri)
        val specificResult = specific.match(uri)

        genericResult.shouldNotBeNull()
        specificResult.shouldNotBeNull()
        (specificResult.score > genericResult.score) shouldBe true
    }

    @Test
    fun `selecting most specific template via score`() {
        val templates = listOf(
            UriTemplate("test://items/{id}"),
            UriTemplate("test://items/{id}/details"),
            UriTemplate("test://items/special/details"),
        )
        val uri = "test://items/special/details"
        val best = templates.mapNotNull { it.match(uri) }.maxByOrNull { it.score }

        best.shouldNotBeNull()
        best.score shouldBe "test://items/special/details".length
    }

    //endregion
}
