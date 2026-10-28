package io.modelcontextprotocol.kotlin.sdk.server.utils

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ToolNameValidationTest {

    // Together these use every allowed character class: A-Z, a-z, 0-9, '_', '-', '.'.
    @ParameterizedTest
    @ValueSource(strings = ["DATA_EXPORT_v2.1", "a-b.c_d"])
    fun `should return no warnings for valid tool names`(name: String) {
        validateToolName(name).shouldBeEmpty()
    }

    @Test
    fun `should return no warnings for max length name`() {
        validateToolName("a".repeat(128)).shouldBeEmpty()
    }

    @Test
    fun `should warn for empty name`() {
        validateToolName("") shouldContainExactly listOf("Tool name cannot be empty")
    }

    @Test
    fun `should warn for name exceeding max length`() {
        validateToolName("a".repeat(129)) shouldContain
            "Tool name exceeds maximum length of 128 characters (current: 129)"
    }

    @Test
    fun `should warn for spaces and commas without reporting them as invalid characters`() {
        val warnings = validateToolName("my tool,name")

        warnings shouldContainAll listOf(
            "Tool name contains spaces, which may cause parsing issues",
            "Tool name contains commas, which may cause parsing issues",
        )
        warnings.filter { it.startsWith("Tool name contains invalid characters") }.shouldBeEmpty()
    }

    @ParameterizedTest
    @CsvSource("user/profile, /", "user@domain.com, @", "user-ñame, ñ")
    fun `should warn for invalid characters`(name: String, invalidChar: String) {
        validateToolName(name) shouldContainAll listOf(
            "Tool name contains invalid characters: \"$invalidChar\"",
            "Allowed characters are: A-Z, a-z, 0-9, underscore (_), dash (-), and dot (.)",
        )
    }

    @ParameterizedTest
    @CsvSource("-my-tool, dash", "my-tool-, dash", ".hidden, dot", "config., dot")
    fun `should warn for name starting or ending with a dash or dot`(name: String, symbol: String) {
        validateToolName(name) shouldContain
            "Tool name starts or ends with a $symbol, which may cause parsing issues in some contexts"
    }
}
