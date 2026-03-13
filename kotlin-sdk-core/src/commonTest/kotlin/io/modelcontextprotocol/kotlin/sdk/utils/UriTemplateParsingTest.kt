package io.modelcontextprotocol.kotlin.sdk.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.utils.UriTemplateParser.Modifier
import io.modelcontextprotocol.kotlin.sdk.utils.UriTemplateParser.Part
import io.modelcontextprotocol.kotlin.sdk.utils.UriTemplateParser.VarSpec
import kotlin.test.Test

/**
 * Unit tests for [UriTemplateParser] — verifies the parsed [Part] model,
 * operator properties, modifier detection, literal encoding, and encoding
 * helpers. These test the intermediate representation, not just the final
 * expanded string.
 */
class UriTemplateParsingTest {

    //region parse() — structural model ─────────────────────────────────────────

    @Test
    fun `empty template produces no parts`() {
        UriTemplateParser.parse("") shouldBe emptyList()
    }

    @Test
    fun `literal-only template produces a single Literal part`() {
        UriTemplateParser.parse("hello/world") shouldBe listOf(Part.Literal("hello/world"))
    }

    @Test
    fun `single expression produces an Expression part with NUL operator`() {
        val parts = UriTemplateParser.parse("{var}")
        parts.size shouldBe 1
        val expr = parts[0] as Part.Expression
        expr.op shouldBe UriTemplateParser.NUL_OP
        expr.varSpecs shouldBe listOf(VarSpec("var", Modifier.None))
    }

    @Test
    fun `mixed literal and expression produces two parts in order`() {
        val parts = UriTemplateParser.parse("users/{id}")
        parts shouldBe listOf(
            Part.Literal("users/"),
            Part.Expression(UriTemplateParser.NUL_OP, listOf(VarSpec("id", Modifier.None))),
        )
    }

    @Test
    fun `adjacent expressions with no literal produce two Expression parts`() {
        val parts = UriTemplateParser.parse("{a}{b}")
        parts.size shouldBe 2
        (parts[0] as Part.Expression).varSpecs[0].name shouldBe "a"
        (parts[1] as Part.Expression).varSpecs[0].name shouldBe "b"
    }

    @Test
    fun `expression surrounded by literals produces three parts`() {
        val parts = UriTemplateParser.parse("prefix/{id}/suffix")
        parts shouldBe listOf(
            Part.Literal("prefix/"),
            Part.Expression(UriTemplateParser.NUL_OP, listOf(VarSpec("id", Modifier.None))),
            Part.Literal("/suffix"),
        )
    }

    //endregion
    //region Operator detection ─────────────────────────────────────────────────

    @Test
    fun `all seven operator chars are parsed correctly`() {
        val cases = mapOf(
            "{+var}" to '+',
            "{#var}" to '#',
            "{.var}" to '.',
            "{/var}" to '/',
            "{;var}" to ';',
            "{?var}" to '?',
            "{&var}" to '&',
        )
        for ((template, expectedChar) in cases) {
            val op = (UriTemplateParser.parse(template)[0] as Part.Expression).op
            op.char shouldBe expectedChar
        }
    }

    @Test
    fun `only plus and hash operators allow reserved characters`() {
        val allowReserved = setOf('+', '#')
        val allOps = listOf("+", "#", ".", "/", ";", "?", "&", "")
        for (prefix in allOps) {
            val op = (UriTemplateParser.parse("{${prefix}var}")[0] as Part.Expression).op
            op.allowReserved shouldBe (op.char in allowReserved)
        }
    }

    @Test
    fun `named operators are semicolon question mark and ampersand`() {
        val namedOps = setOf(';', '?', '&')
        val allOps = listOf("+", "#", ".", "/", ";", "?", "&", "")
        for (prefix in allOps) {
            val op = (UriTemplateParser.parse("{${prefix}var}")[0] as Part.Expression).op
            op.named shouldBe (op.char in namedOps)
        }
    }

    @Test
    fun `ifemp is = for query operators and empty for others`() {
        val queryOps = setOf('?', '&')
        val allOps = listOf("+", "#", ".", "/", ";", "?", "&", "")
        for (prefix in allOps) {
            val op = (UriTemplateParser.parse("{${prefix}var}")[0] as Part.Expression).op
            op.ifemp shouldBe (if (op.char in queryOps) "=" else "")
        }
    }

    @Test
    fun `operator first and sep strings match RFC 6570 Appendix A table`() {
        // RFC 6570 Appendix A value table (first, sep per operator):
        //   NUL  ""   ","  |  +   ""   ","  |  #   "#"  ","
        //   .    "."  "."  |  /   "/"  "/"  |  ;   ";"  ";"
        //   ?    "?"  "&"  |  &   "&"  "&"
        val expected = mapOf(
            "" to ("" to ","),
            "+" to ("" to ","),
            "#" to ("#" to ","),
            "." to ("." to "."),
            "/" to ("/" to "/"),
            ";" to (";" to ";"),
            "?" to ("?" to "&"),
            "&" to ("&" to "&"),
        )
        for ((prefix, firstSep) in expected) {
            val op = (UriTemplateParser.parse("{${prefix}var}")[0] as Part.Expression).op
            val (expectedFirst, expectedSep) = firstSep
            op.first shouldBe expectedFirst
            op.sep shouldBe expectedSep
        }
    }

    //endregion
    //region VarSpec modifier detection ─────────────────────────────────────────

    @Test
    fun `no modifier produces Modifier None`() {
        val spec = (UriTemplateParser.parse("{var}")[0] as Part.Expression).varSpecs[0]
        spec.modifier shouldBe Modifier.None
    }

    @Test
    fun `trailing asterisk produces Modifier Explode`() {
        val spec = (UriTemplateParser.parse("{list*}")[0] as Part.Expression).varSpecs[0]
        spec.name shouldBe "list"
        spec.modifier shouldBe Modifier.Explode
    }

    @Test
    fun `colon-integer produces Modifier Prefix with correct length`() {
        (UriTemplateParser.parse("{var:3}")[0] as Part.Expression).varSpecs[0].modifier shouldBe Modifier.Prefix(3)
        (UriTemplateParser.parse("{var:30}")[0] as Part.Expression).varSpecs[0].modifier shouldBe Modifier.Prefix(30)
        (UriTemplateParser.parse("{var:1000}")[0] as Part.Expression).varSpecs[0].modifier shouldBe
            Modifier.Prefix(1000)
    }

    @Test
    fun `prefix length of zero is not valid and falls back to None`() {
        // RFC 6570: max-length = %x31-39 0*3DIGIT (must start with 1-9)
        val spec = (UriTemplateParser.parse("{var:0}")[0] as Part.Expression).varSpecs[0]
        spec.modifier shouldBe Modifier.None
    }

    @Test
    fun `non-numeric prefix falls back to None modifier`() {
        val spec = (UriTemplateParser.parse("{var:abc}")[0] as Part.Expression).varSpecs[0]
        spec.modifier shouldBe Modifier.None
    }

    @Test
    fun `multiple variables in one expression are all parsed`() {
        val specs = (UriTemplateParser.parse("{x,y,z}")[0] as Part.Expression).varSpecs
        specs.map { it.name } shouldBe listOf("x", "y", "z")
        specs.all { it.modifier == Modifier.None } shouldBe true
    }

    @Test
    fun `mixed modifiers in multi-variable expression`() {
        val specs = (UriTemplateParser.parse("{var:3,list*,plain}")[0] as Part.Expression).varSpecs
        specs[0] shouldBe VarSpec("var", Modifier.Prefix(3))
        specs[1] shouldBe VarSpec("list", Modifier.Explode)
        specs[2] shouldBe VarSpec("plain", Modifier.None)
    }

    //endregion
    //region Empty expression ───────────────────────────────────────────────────

    @Test
    fun `empty braces produce Expression with NUL_OP and no varSpecs`() {
        val expr = UriTemplateParser.parse("{}")[0] as Part.Expression
        expr.op shouldBe UriTemplateParser.NUL_OP
        expr.varSpecs shouldBe emptyList()
    }

    //endregion
    //region Malformed templates ────────────────────────────────────────────────

    @Test
    fun `unclosed brace throws IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            UriTemplateParser.parse("{unclosed")
        }.message shouldContain "Unclosed brace"
    }

    @Test
    fun `unclosed brace mid-template throws IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            UriTemplateParser.parse("foo/{unclosed")
        }.message shouldContain "Unclosed brace"
    }

    @Test
    fun `unclosed brace at end of otherwise valid template throws`() {
        shouldThrow<IllegalArgumentException> {
            UriTemplateParser.parse("users/{id}/posts/{")
        }.message shouldContain "Unclosed brace"
    }

    @Test
    fun `error message includes the offending template string`() {
        val template = "bad/{template"
        shouldThrow<IllegalArgumentException> {
            UriTemplateParser.parse(template)
        }.message shouldContain template
    }

    //endregion
    //region Literal PCT-encoding ───────────────────────────────────────────────

    @Test
    fun `space in literal is pct-encoded to space`() {
        UriTemplateParser.parse("hello world") shouldBe listOf(Part.Literal("hello%20world"))
    }

    @Test
    fun `percent sign in literal passes through unchanged`() {
        // % is allowed verbatim in literals (already pct-encoded in source)
        UriTemplateParser.parse("50%25") shouldBe listOf(Part.Literal("50%25"))
    }

    @Test
    fun `reserved characters in literals pass through unchanged`() {
        // All of :/?#[]@!$&'()*+,;= are allowed verbatim in URI template literals
        UriTemplateParser.parse("https://host/path?q=1&r=2") shouldBe
            listOf(Part.Literal("https://host/path?q=1&r=2"))
    }

    @Test
    fun `non-ascii character in literal is pct-encoded as utf-8`() {
        // é (U+00E9) → UTF-8 0xC3 0xA9 → %C3%A9
        UriTemplateParser.parse("caf\u00E9") shouldBe listOf(Part.Literal("caf%C3%A9"))
    }

    //endregion
    //region pctEncode ─────────────────────────────────────────────────────────

    @Test
    fun `pctEncode leaves unreserved characters unchanged`() {
        val unreserved = "abcxyzABCXYZ0189-._~"
        UriTemplateParser.pctEncode(unreserved, allowReserved = false) shouldBe unreserved
        UriTemplateParser.pctEncode(unreserved, allowReserved = true) shouldBe unreserved
    }

    @Test
    fun `pctEncode encodes space in both modes`() {
        UriTemplateParser.pctEncode(" ", allowReserved = false) shouldBe "%20"
        UriTemplateParser.pctEncode(" ", allowReserved = true) shouldBe "%20"
    }

    @Test
    fun `pctEncode passes reserved chars through only when allowReserved is true`() {
        UriTemplateParser.pctEncode(":/", allowReserved = true) shouldBe ":/"
        UriTemplateParser.pctEncode(":/", allowReserved = false) shouldBe "%3A%2F"
    }

    @Test
    fun `pctEncode preserves existing pct-triplets only when allowReserved is true`() {
        UriTemplateParser.pctEncode("50%25", allowReserved = true) shouldBe "50%25"
        // When allowReserved is false, % itself is encoded
        UriTemplateParser.pctEncode("50%25", allowReserved = false) shouldBe "50%2525"
    }

    @Test
    fun `pctEncode encodes non-ascii as utf-8 pct-triplets`() {
        // é (U+00E9) → UTF-8 0xC3 0xA9 → %C3%A9
        UriTemplateParser.pctEncode("\u00E9", allowReserved = false) shouldBe "%C3%A9"
    }

    //endregion
    //region truncateCodePoints ─────────────────────────────────────────────────

    @Test
    fun `truncateCodePoints truncates ascii strings by code point count`() {
        UriTemplateParser.truncateCodePoints("hello", 3) shouldBe "hel"
        UriTemplateParser.truncateCodePoints("hello", 10) shouldBe "hello"
        UriTemplateParser.truncateCodePoints("hello", 0) shouldBe ""
    }

    @Test
    fun `truncateCodePoints treats each BMP character as one code point`() {
        // é (U+00E9) is a BMP character — one Char, one code point
        UriTemplateParser.truncateCodePoints("caf\u00E9", 3) shouldBe "caf"
        UriTemplateParser.truncateCodePoints("caf\u00E9", 4) shouldBe "caf\u00E9"
    }

    @Test
    fun `truncateCodePoints counts a surrogate pair as one code point`() {
        // 😀 U+1F600 → surrogate pair \uD83D\uDE00 (two Chars, one code point)
        val emoji = "\uD83D\uDE00"
        UriTemplateParser.truncateCodePoints("a${emoji}b", 1) shouldBe "a"
        UriTemplateParser.truncateCodePoints("a${emoji}b", 2) shouldBe "a$emoji"
        UriTemplateParser.truncateCodePoints("a${emoji}b", 3) shouldBe "a${emoji}b"
    }

    @Test
    fun `pctEncode encodes surrogate pair as utf-8 pct-triplets`() {
        // 😀 U+1F600 → UTF-8 F0 9F 98 80 → %F0%9F%98%80
        val emoji = "\uD83D\uDE00"
        UriTemplateParser.pctEncode(emoji, allowReserved = false) shouldBe "%F0%9F%98%80"
        UriTemplateParser.pctEncode(emoji, allowReserved = true) shouldBe "%F0%9F%98%80"
    }

    @Test
    fun `pctEncode recognises lowercase hex digits in pct-triplets when allowReserved is true`() {
        // %2f uses lowercase hex — must be preserved as-is, not re-encoded
        UriTemplateParser.pctEncode("a%2fb", allowReserved = true) shouldBe "a%2fb"
        // Lowercase that isn't a valid triplet gets encoded normally
        UriTemplateParser.pctEncode("a%gg", allowReserved = true) shouldBe "a%25gg"
    }
}
