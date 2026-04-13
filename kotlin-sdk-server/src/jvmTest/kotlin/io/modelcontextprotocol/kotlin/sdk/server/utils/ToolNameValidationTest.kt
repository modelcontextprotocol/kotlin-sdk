package io.modelcontextprotocol.kotlin.sdk.server.utils

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ToolNameValidationTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "getUser",
            "get_user_profile",
            "user-profile-update",
            "admin.tools.list",
            "DATA_EXPORT_v2.1",
            "a",
            "Z",
            "0",
            "a-b.c_d",
        ],
    )
    fun `should return no warnings for valid tool names`(name: String) {
        validateToolName(name).shouldBeEmpty()
    }

    @Test
    fun `should return no warnings for max length name`() {
        validateToolName("a".repeat(128)).shouldBeEmpty()
    }

    @Test
    fun `should warn for empty name`() {
        val warnings = validateToolName("")
        warnings shouldHaveSize 1
        warnings shouldContain "Tool name cannot be empty"
    }

    @Test
    fun `should warn for name exceeding max length`() {
        val warnings = validateToolName("a".repeat(129))
        warnings.shouldNotBeEmpty()
        warnings.any { "exceeds maximum length" in it }.shouldBeTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["get user profile", "my tool"])
    fun `should warn for names with spaces`(name: String) {
        val warnings = validateToolName(name)
        warnings shouldContain "Tool name contains spaces, which may cause parsing issues"
    }

    @ParameterizedTest
    @ValueSource(strings = ["get,user,profile", "a,b"])
    fun `should warn for names with commas`(name: String) {
        val warnings = validateToolName(name)
        warnings shouldContain "Tool name contains commas, which may cause parsing issues"
    }

    @ParameterizedTest
    @ValueSource(strings = ["user/profile/update", "a/b"])
    fun `should warn for names with forward slashes`(name: String) {
        val warnings = validateToolName(name)
        warnings.any { "invalid characters" in it }.shouldBeTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["user@domain.com", "tool#1", "tool\$name"])
    fun `should warn for names with special characters`(name: String) {
        val warnings = validateToolName(name)
        warnings.any { "invalid characters" in it }.shouldBeTrue()
        warnings shouldContain "Allowed characters are: A-Z, a-z, 0-9, underscore (_), dash (-), and dot (.)"
    }

    @Test
    fun `should warn for unicode characters`() {
        val warnings = validateToolName("user-\u00f1ame")
        warnings.any { "invalid characters" in it }.shouldBeTrue()
    }

    @Test
    fun `should warn for name starting with dash`() {
        val warnings = validateToolName("-my-tool")
        warnings shouldContain "Tool name starts or ends with a dash, which may cause parsing issues in some contexts"
    }

    @Test
    fun `should warn for name ending with dash`() {
        val warnings = validateToolName("my-tool-")
        warnings shouldContain "Tool name starts or ends with a dash, which may cause parsing issues in some contexts"
    }

    @Test
    fun `should warn for name starting with dot`() {
        val warnings = validateToolName(".hidden")
        warnings shouldContain "Tool name starts or ends with a dot, which may cause parsing issues in some contexts"
    }

    @Test
    fun `should warn for name ending with dot`() {
        val warnings = validateToolName("config.")
        warnings shouldContain "Tool name starts or ends with a dot, which may cause parsing issues in some contexts"
    }

    @Test
    fun `should produce multiple warnings for multiple issues`() {
        val warnings = validateToolName("my tool,name")
        warnings.any { "spaces" in it }.shouldBeTrue()
        warnings.any { "commas" in it }.shouldBeTrue()
    }

    @Test
    fun `should not duplicate space or comma in generic invalid characters message`() {
        val warnings = validateToolName("my tool")
        warnings shouldContain "Tool name contains spaces, which may cause parsing issues"
        warnings.none { it.startsWith("Tool name contains invalid characters") }.shouldBeTrue()
    }
}
