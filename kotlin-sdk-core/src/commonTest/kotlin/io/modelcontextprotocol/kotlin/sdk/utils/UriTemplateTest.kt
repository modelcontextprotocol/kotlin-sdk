package io.modelcontextprotocol.kotlin.sdk.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class UriTemplateTest {

    @Test
    fun `equal template strings produce equal UriTemplate instances`() {
        UriTemplate("test://items/{id}") shouldBe UriTemplate("test://items/{id}")
    }

    @Test
    fun `different template strings produce unequal UriTemplate instances`() {
        val a = UriTemplate("test://items/{id}")
        val b = UriTemplate("test://items/{name}")
        (a == b) shouldBe false
    }

    @Test
    fun `hashCode is consistent with equals`() {
        val a = UriTemplate("test://items/{id}")
        val b = UriTemplate("test://items/{id}")
        a.hashCode() shouldBe b.hashCode()
    }

    @Test
    fun `toString includes the template string`() {
        UriTemplate("test://items/{id}").toString() shouldBe "UriTemplate(test://items/{id})"
    }

    @Test
    fun `constructor throws IllegalArgumentException for unclosed brace`() {
        shouldThrow<IllegalArgumentException> { UriTemplate("test://items/{id") }
    }

    @Test
    fun `constructor throws IllegalArgumentException for unclosed brace mid-template`() {
        shouldThrow<IllegalArgumentException> { UriTemplate("users/{id}/posts/{") }
    }
}
