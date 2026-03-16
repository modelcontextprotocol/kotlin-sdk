package io.modelcontextprotocol.kotlin.sdk.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate
import kotlin.test.Test

class PathSegmentTemplateMatcherTest {

    // region: Construction

    @Test
    fun `should throw on blank template`() {
        shouldThrow<IllegalArgumentException> {
            matcher("   ")
        }
    }

    @Test
    fun `should throw on empty variable name`() {
        shouldThrow<IllegalArgumentException> {
            matcher("users/{}")
        }
    }

    @Test
    fun `should throw when depth exceeds maxTemplateDepth`() {
        val deep = "a/b/c/d/e/f/g/h/i/j/k" // 11 segments
        shouldThrow<IllegalArgumentException> {
            matcher(deep, maxDepth = 10)
        }
    }

    @Test
    fun `should accept template at exactly maxTemplateDepth`() {
        val atLimit = "a/b/c/d/e/f/g/h/i/j" // 10 segments
        matcher(atLimit, maxDepth = 10) // must not throw
    }

    // endregion
    // region: Basic matching

    @Test
    fun `should return null for URI with fewer segments than template`() {
        matcher("users/{id}/posts").match("users/42") shouldBe null
    }

    @Test
    fun `should return null for URI with more segments than template`() {
        matcher("users/{id}").match("users/42/extra") shouldBe null
    }

    @Test
    fun `should match all-literal template`() {
        val result = matcher("users/profile").match("users/profile")
        result.shouldNotBeNull {
            variables.shouldBeEmpty()
        }
    }

    @Test
    fun `should return null when literal segment does not match`() {
        matcher("users/profile").match("users/settings") shouldBe null
    }

    @Test
    fun `should extract single variable`() {
        val result = matcher("users/{id}").match("users/42")
        result.shouldNotBeNull {
            variables["id"] shouldBe "42"
        }
    }

    @Test
    fun `should extract multiple variables`() {
        val result = matcher("users/{userId}/posts/{postId}").match("users/alice/posts/99")
        result.shouldNotBeNull {
            variables["userId"] shouldBe "alice"
            variables["postId"] shouldBe "99"
        }
    }

    @Test
    fun `should match template with scheme`() {
        val result = matcher("test://items/{id}").match("test://items/42")
        result.shouldNotBeNull {
            variables["id"] shouldBe "42"
        }
    }

    // endregion
    // region:Scoring

    @Test
    fun `all-literal template scores higher than parameterized template for same URI`() {
        val literal = matcher("users/profile").match("users/profile")!!
        val parameterized = matcher("users/{id}").match("users/profile")!!
        (literal.score > parameterized.score) shouldBe true
    }

    @Test
    fun `score increases with number of segments`() {
        val short = matcher("a/b").match("a/b")!!
        val long = matcher("a/b/c").match("a/b/c")!!
        (long.score > short.score) shouldBe true
    }

    @Test
    fun `all-literal two-segment template score is 4`() {
        // 2 literal segments × LITERAL_MATCH_SCORE(2) = 4
        matcher("users/profile").match("users/profile")!!.score shouldBe 4
    }

    @Test
    fun `one-variable two-segment template score is 3`() {
        // 1 literal × 2 + 1 variable × 1 = 3
        matcher("users/{id}").match("users/42")!!.score shouldBe 3
    }

    @Test
    fun `all-variable template scores one per segment`() {
        // 2 variables × VARIABLE_MATCH_SCORE(1) = 2
        matcher("{a}/{b}").match("x/y")!!.score shouldBe 2
    }

    // endregion
    // region:URL decoding

    @Test
    fun `should URL-decode percent-encoded variable value`() {
        val result = matcher("search/{query}").match("search/hello%20world")
        result.shouldNotBeNull {
            variables["query"] shouldBe "hello world"
        }
    }

    @Test
    fun `should URL-decode percent-encoded literal segment before comparing`() {
        // %66 decodes to 'f', so "pro%66ile" == "profile" after decoding — it matches
        matcher("users/profile").match("users/pro%66ile").shouldNotBeNull()
    }

    // endregion
    // region:Length guard

    @Test
    fun `should return null when URI exceeds maxUrlLength`() {
        val longUri = "a/" + "x".repeat(2048)
        matcher("a/{id}", maxUrlLength = 2048).match(longUri) shouldBe null
    }

    @Test
    fun `should match URI at exactly maxUrlLength`() {
        // URI of exactly maxUrlLength characters must be accepted
        val uri = "a/" + "x".repeat(2046) // length = 2048
        matcher("a/{id}", maxUrlLength = 2048).match(uri).shouldNotBeNull()
    }

    // endregion
    // region:Edge cases

    @Test
    fun `should match root-level single segment template`() {
        val result = matcher("{id}").match("42")
        result.shouldNotBeNull {
            variables["id"] shouldBe "42"
        }
    }

    @Test
    fun `should treat leading and trailing slashes as equivalent`() {
        val result = matcher("/users/{id}/").match("/users/7/")
        result.shouldNotBeNull {
            variables["id"] shouldBe "7"
        }
    }

    @Test
    fun `should capture empty string for single-segment variable when URI is empty`() {
        // "".trim('/').split("/") == [""] — one segment — so {id} captures ""
        val result = matcher("{id}").match("")
        result.shouldNotBeNull {
            variables["id"] shouldBe ""
        }
    }

    @Test
    fun `factory creates matcher equal to direct construction`() {
        val template = ResourceTemplate("items/{id}", "Items")
        val fromFactory = PathSegmentTemplateMatcher.factory.create(template)
        val direct = PathSegmentTemplateMatcher(template)

        val uriToMatch = "items/99"
        fromFactory.match(uriToMatch) shouldBe direct.match(uriToMatch)
    }

    // endregion
    // region: MatchResult equality

    @Test
    fun `MatchResult equals by value`() {
        val a = MatchResult(mapOf("id" to "1"), score = 3)
        val b = MatchResult(mapOf("id" to "1"), score = 3)
        a shouldBe b
    }

    @Test
    fun `MatchResult not equal when score differs`() {
        val a = MatchResult(mapOf("id" to "1"), score = 3)
        val b = MatchResult(mapOf("id" to "1"), score = 2)
        (a == b) shouldBe false
    }

    @Test
    fun `MatchResult not equal when variables differ`() {
        val a = MatchResult(mapOf("id" to "1"), score = 3)
        val b = MatchResult(mapOf("id" to "2"), score = 3)
        (a == b) shouldBe false
    }

    // endregion
    // region: Helpers

    private fun matcher(
        uriTemplate: String,
        maxUrlLength: Int = 2048,
        maxDepth: Int = 10,
    ): PathSegmentTemplateMatcher = PathSegmentTemplateMatcher(
        resourceTemplate = ResourceTemplate(uriTemplate, "Test"),
        maxUriLength = maxUrlLength,
        maxDepth = maxDepth,
    )
    // endregion
}
