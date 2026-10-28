package io.modelcontextprotocol.kotlin.sdk.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate
import kotlin.test.Test

class PathSegmentTemplateMatcherTest {

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

    @Test
    fun `score should count 2 per literal and 1 per variable segment`() {
        listOf("users/profile" to 4, "users/{id}" to 3, "{a}/{b}" to 2).forEach { (template, score) ->
            withClue(template) {
                matcher(template).match("users/profile")?.score shouldBe score
            }
        }
    }

    @Test
    fun `should percent-decode variable values exactly once`() {
        val files = matcher("files/{path}")
        files.match("files/..%2Fetc%2Fpasswd")?.variables shouldBe mapOf("path" to "../etc/passwd")
        files.match("files/%252Fetc%252Fpasswd")?.variables shouldBe mapOf("path" to "%2Fetc%2Fpasswd")
    }

    @Test
    fun `should URL-decode percent-encoded literal segment before comparing`() {
        // %66 decodes to 'f', so "pro%66ile" == "profile" after decoding — it matches
        matcher("users/profile").match("users/pro%66ile").shouldNotBeNull()
    }

    @Test
    fun `should keep query string and fragment in variable value`() {
        val api = matcher("api://host/{id}")
        api.match("api://host/foo?bar=baz")?.variables shouldBe mapOf("id" to "foo?bar=baz")
        api.match("api://host/foo#section")?.variables shouldBe mapOf("id" to "foo#section")
    }

    @Test
    fun `should return null when URI exceeds maxUrlLength`() {
        val longUri = "a/" + "x".repeat(2048)
        matcher("a/{id}", maxUrlLength = 2048).match(longUri) shouldBe null
    }

    @Test
    fun `should match URI at exactly maxUrlLength`() {
        val uri = "a/" + "x".repeat(2046) // length = 2048
        matcher("a/{id}", maxUrlLength = 2048).match(uri).shouldNotBeNull()
    }

    @Test
    fun `should ignore leading and trailing slashes`() {
        matcher("/users/{id}/").match("users/7")?.variables shouldBe mapOf("id" to "7")
        matcher("users/{id}").match("/users/7/")?.variables shouldBe mapOf("id" to "7")
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

    @Test
    fun `MatchResult should be equal by variables and score`() {
        val result = MatchResult(mapOf("id" to "1"), score = 3)
        val same = MatchResult(mapOf("id" to "1"), score = 3)

        result shouldBe same
        result.hashCode() shouldBe same.hashCode()
        result shouldNotBe MatchResult(mapOf("id" to "1"), score = 2)
        result shouldNotBe MatchResult(mapOf("id" to "2"), score = 3)
    }

    private fun matcher(
        uriTemplate: String,
        maxUrlLength: Int = 2048,
        maxDepth: Int = 10,
    ): PathSegmentTemplateMatcher = PathSegmentTemplateMatcher(
        resourceTemplate = ResourceTemplate(uriTemplate, "Test"),
        maxUriLength = maxUrlLength,
        maxDepth = maxDepth,
    )
}
