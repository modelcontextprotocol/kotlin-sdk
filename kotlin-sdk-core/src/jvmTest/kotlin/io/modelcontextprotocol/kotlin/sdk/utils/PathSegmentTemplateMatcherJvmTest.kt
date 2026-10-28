package io.modelcontextprotocol.kotlin.sdk.utils

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate
import kotlin.test.Test

/** Dot-segment normalization only exists on JVM and JS, see [normalizeUri]. */
class PathSegmentTemplateMatcherJvmTest {

    @Test
    fun `should match dot-segment URI against its normalized target`() {
        matcher("app://host/private/{name}").match("app://host/public/../private/secret")
            ?.variables shouldBe mapOf("name" to "secret")
    }

    @Test
    fun `should not capture dot segments in variables`() {
        matcher("files/{a}/{b}").match("files/../etc") shouldBe null
    }

    private fun matcher(uriTemplate: String) = PathSegmentTemplateMatcher(ResourceTemplate(uriTemplate, "Test"))
}
