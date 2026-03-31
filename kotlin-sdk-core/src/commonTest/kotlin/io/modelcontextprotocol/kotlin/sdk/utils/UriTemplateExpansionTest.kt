package io.modelcontextprotocol.kotlin.sdk.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * Unit tests for [UriTemplate] covering all RFC 6570 levels and operators.
 *
 * Variable definitions follow Appendix A of RFC 6570:
 *   count  = ("one", "two", "three")
 *   dom    = ("example", "com")
 *   dub    = "me/too"
 *   hello  = "Hello World!"
 *   half   = "50%"
 *   var    = "value"
 *   who    = "fred"
 *   base   = "http://example.com/home/"
 *   path   = "/foo/bar"
 *   list   = ("red", "green", "blue")
 *   keys   = [("semi",";"), ("dot","."), ("comma",",")]
 *   v      = "6"
 *   x      = "1024"
 *   y      = "768"
 *   empty  = ""
 *   empty_keys = []
 *   undef  = null
 */
class UriTemplateExpansionTest {

    //region Shared variable set ────────────────────────────────────────────────

    private val vars: Map<String, Any?> = mapOf(
        "count" to listOf("one", "two", "three"),
        "dom" to listOf("example", "com"),
        "dub" to "me/too",
        "hello" to "Hello World!",
        "half" to "50%",
        "var" to "value",
        "who" to "fred",
        "base" to "http://example.com/home/",
        "path" to "/foo/bar",
        "list" to listOf("red", "green", "blue"),
        "keys" to mapOf("semi" to ";", "dot" to ".", "comma" to ","),
        "v" to "6",
        "x" to "1024",
        "y" to "768",
        "empty" to "",
        "empty_keys" to emptyMap<String, String>(),
        "undef" to null,
    )

    private fun expand(template: String): String = UriTemplate.expand(template, vars)

    //endregion
    //region Level 1: Simple string expansion {var} ─────────────────────────────

    @Test
    fun `level 1 - simple variable expansion`() {
        expand("{var}") shouldBe "value"
        expand("{hello}") shouldBe "Hello%20World%21"
        expand("{half}") shouldBe "50%25"
    }

    @Test
    fun `level 1 - undefined and empty variables`() {
        expand("O{empty}X") shouldBe "OX"
        expand("O{undef}X") shouldBe "OX"
    }

    @Test
    fun `level 1 - multiple variables in expression`() {
        expand("{x,y}") shouldBe "1024,768"
        expand("{x,hello,y}") shouldBe "1024,Hello%20World%21,768"
        expand("?{x,empty}") shouldBe "?1024,"
        expand("?{x,undef}") shouldBe "?1024"
        expand("?{undef,y}") shouldBe "?768"
    }

    //endregion
    //region Level 1: Prefix modifier {var:N} ──────────────────────────────────

    @Test
    fun `level 1 - prefix modifier on simple string`() {
        expand("{var:3}") shouldBe "val"
        expand("{var:30}") shouldBe "value"
    }

    //endregion
    //region Level 1: List and map without explode ──────────────────────────────

    @Test
    fun `level 1 - list expansion without explode`() {
        expand("{list}") shouldBe "red,green,blue"
        expand("{list*}") shouldBe "red,green,blue"
    }

    @Test
    fun `level 1 - map expansion without explode`() {
        expand("{keys}") shouldBe "semi,%3B,dot,.,comma,%2C"
        expand("{keys*}") shouldBe "semi=%3B,dot=.,comma=%2C"
    }

    //endregion
    //region Level 2: Reserved expansion {+var} ────────────────────────────────

    @Test
    fun `reserved expansion - basic`() {
        expand("{+var}") shouldBe "value"
        expand("{+hello}") shouldBe "Hello%20World!"
        expand("{+half}") shouldBe "50%25"
    }

    @Test
    fun `reserved expansion - allows reserved chars and pct-encoded passthrough`() {
        expand("{base}index") shouldBe "http%3A%2F%2Fexample.com%2Fhome%2Findex"
        expand("{+base}index") shouldBe "http://example.com/home/index"
        expand("O{+empty}X") shouldBe "OX"
        expand("O{+undef}X") shouldBe "OX"
    }

    @Test
    fun `reserved expansion - path variable`() {
        expand("{+path}/here") shouldBe "/foo/bar/here"
        expand("here?ref={+path}") shouldBe "here?ref=/foo/bar"
        expand("up{+path}{var}/here") shouldBe "up/foo/barvalue/here"
    }

    @Test
    fun `reserved expansion - multiple variables`() {
        expand("{+x,hello,y}") shouldBe "1024,Hello%20World!,768"
        expand("{+path,x}/here") shouldBe "/foo/bar,1024/here"
    }

    @Test
    fun `reserved expansion - prefix modifier`() {
        expand("{+path:6}/here") shouldBe "/foo/b/here"
    }

    @Test
    fun `reserved expansion - list and map`() {
        expand("{+list}") shouldBe "red,green,blue"
        expand("{+list*}") shouldBe "red,green,blue"
        expand("{+keys}") shouldBe "semi,;,dot,.,comma,,"
        expand("{+keys*}") shouldBe "semi=;,dot=.,comma=,"
    }

    //endregion
    //region Level 2: Fragment expansion {#var} ────────────────────────────────

    @Test
    fun `fragment expansion - basic`() {
        expand("{#var}") shouldBe "#value"
        expand("{#hello}") shouldBe "#Hello%20World!"
        expand("{#half}") shouldBe "#50%25"
        expand("foo{#empty}") shouldBe "foo#"
        expand("foo{#undef}") shouldBe "foo"
    }

    @Test
    fun `fragment expansion - multiple variables`() {
        expand("{#x,hello,y}") shouldBe "#1024,Hello%20World!,768"
        expand("{#path,x}/here") shouldBe "#/foo/bar,1024/here"
    }

    @Test
    fun `fragment expansion - prefix modifier and composites`() {
        expand("{#path:6}/here") shouldBe "#/foo/b/here"
        expand("{#list}") shouldBe "#red,green,blue"
        expand("{#list*}") shouldBe "#red,green,blue"
        expand("{#keys}") shouldBe "#semi,;,dot,.,comma,,"
        expand("{#keys*}") shouldBe "#semi=;,dot=.,comma=,"
    }

    //endregion
    //region Level 3: Label expansion {.var} ────────────────────────────────────

    @Test
    fun `label expansion - basic`() {
        expand("{.who}") shouldBe ".fred"
        expand("{.who,who}") shouldBe ".fred.fred"
        expand("{.half,who}") shouldBe ".50%25.fred"
        expand("www{.dom*}") shouldBe "www.example.com"
        expand("X{.var}") shouldBe "X.value"
        expand("X{.empty}") shouldBe "X."
        expand("X{.undef}") shouldBe "X"
    }

    @Test
    fun `label expansion - prefix modifier`() {
        expand("X{.var:3}") shouldBe "X.val"
    }

    @Test
    fun `label expansion - composites`() {
        expand("X{.list}") shouldBe "X.red,green,blue"
        expand("X{.list*}") shouldBe "X.red.green.blue"
        expand("X{.keys}") shouldBe "X.semi,%3B,dot,.,comma,%2C"
        expand("X{.keys*}") shouldBe "X.semi=%3B.dot=..comma=%2C"
        expand("X{.empty_keys}") shouldBe "X"
        expand("X{.empty_keys*}") shouldBe "X"
    }

    //endregion
    //region Level 3: Path segment expansion {/var} ─────────────────────────────

    @Test
    fun `path segment expansion - basic`() {
        expand("{/who}") shouldBe "/fred"
        expand("{/who,who}") shouldBe "/fred/fred"
        expand("{/half,who}") shouldBe "/50%25/fred"
        expand("{/who,dub}") shouldBe "/fred/me%2Ftoo"
        expand("{/var}") shouldBe "/value"
        expand("{/var,empty}") shouldBe "/value/"
        expand("{/var,undef}") shouldBe "/value"
        expand("{/var,x}/here") shouldBe "/value/1024/here"
    }

    @Test
    fun `path segment expansion - prefix modifier`() {
        expand("{/var:1,var}") shouldBe "/v/value"
    }

    @Test
    fun `path segment expansion - composites`() {
        expand("{/list}") shouldBe "/red,green,blue"
        expand("{/list*}") shouldBe "/red/green/blue"
        expand("{/list*,path:4}") shouldBe "/red/green/blue/%2Ffoo"
        expand("{/keys}") shouldBe "/semi,%3B,dot,.,comma,%2C"
        expand("{/keys*}") shouldBe "/semi=%3B/dot=./comma=%2C"
    }

    //endregion
    //region Level 3: Path-style parameter expansion {;var} ─────────────────────

    @Test
    fun `path parameter expansion - basic`() {
        expand("{;who}") shouldBe ";who=fred"
        expand("{;half}") shouldBe ";half=50%25"
        expand("{;empty}") shouldBe ";empty"
        expand("{;v,empty,who}") shouldBe ";v=6;empty;who=fred"
        expand("{;v,bar,who}") shouldBe ";v=6;who=fred"
        expand("{;x,y}") shouldBe ";x=1024;y=768"
        expand("{;x,y,empty}") shouldBe ";x=1024;y=768;empty"
        expand("{;x,y,undef}") shouldBe ";x=1024;y=768"
    }

    @Test
    fun `path parameter expansion - prefix modifier`() {
        expand("{;hello:5}") shouldBe ";hello=Hello"
    }

    @Test
    fun `path parameter expansion - composites`() {
        expand("{;list}") shouldBe ";list=red,green,blue"
        expand("{;list*}") shouldBe ";list=red;list=green;list=blue"
        expand("{;keys}") shouldBe ";keys=semi,%3B,dot,.,comma,%2C"
        expand("{;keys*}") shouldBe ";semi=%3B;dot=.;comma=%2C"
    }

    //endregion
    //region Level 3: Form-style query expansion {?var} ─────────────────────────

    @Test
    fun `query expansion - basic`() {
        expand("{?who}") shouldBe "?who=fred"
        expand("{?half}") shouldBe "?half=50%25"
        expand("{?x,y}") shouldBe "?x=1024&y=768"
        expand("{?x,y,empty}") shouldBe "?x=1024&y=768&empty="
        expand("{?x,y,undef}") shouldBe "?x=1024&y=768"
    }

    @Test
    fun `query expansion - prefix modifier`() {
        expand("{?var:3}") shouldBe "?var=val"
    }

    @Test
    fun `query expansion - composites`() {
        expand("{?list}") shouldBe "?list=red,green,blue"
        expand("{?list*}") shouldBe "?list=red&list=green&list=blue"
        expand("{?keys}") shouldBe "?keys=semi,%3B,dot,.,comma,%2C"
        expand("{?keys*}") shouldBe "?semi=%3B&dot=.&comma=%2C"
    }

    //endregion
    //region Level 3: Form-style query continuation {&var} ──────────────────────

    @Test
    fun `query continuation - basic`() {
        expand("{&who}") shouldBe "&who=fred"
        expand("{&half}") shouldBe "&half=50%25"
        expand("?fixed=yes{&x}") shouldBe "?fixed=yes&x=1024"
        expand("{&x,y,empty}") shouldBe "&x=1024&y=768&empty="
        expand("{&x,y,undef}") shouldBe "&x=1024&y=768"
    }

    @Test
    fun `query continuation - prefix modifier`() {
        expand("{&var:3}") shouldBe "&var=val"
    }

    @Test
    fun `query continuation - composites`() {
        expand("{&list}") shouldBe "&list=red,green,blue"
        expand("{&list*}") shouldBe "&list=red&list=green&list=blue"
        expand("{&keys}") shouldBe "&keys=semi,%3B,dot,.,comma,%2C"
        expand("{&keys*}") shouldBe "&semi=%3B&dot=.&comma=%2C"
    }

    //endregion
    //region List operator behaviour (Section 3.2.1) ────────────────────────────

    @Test
    fun `list expansion across operators`() {
        expand("{count}") shouldBe "one,two,three"
        expand("{count*}") shouldBe "one,two,three"
        expand("{/count}") shouldBe "/one,two,three"
        expand("{/count*}") shouldBe "/one/two/three"
        expand("{;count}") shouldBe ";count=one,two,three"
        expand("{;count*}") shouldBe ";count=one;count=two;count=three"
        expand("{?count}") shouldBe "?count=one,two,three"
        expand("{?count*}") shouldBe "?count=one&count=two&count=three"
        expand("{&count*}") shouldBe "&count=one&count=two&count=three"
    }

    //endregion
    //region Literal-only templates ─────────────────────────────────────────────

    @Test
    fun `literal template without expressions is returned verbatim`() {
        expand("https://example.com/api/v1") shouldBe "https://example.com/api/v1"
    }

    @Test
    fun `literal characters outside expressions pass through or get encoded`() {
        // Unreserved and reserved chars are allowed in literals
        expand("http://example.com/~user") shouldBe "http://example.com/~user"
        // Space in literal gets pct-encoded
        expand("hello world") shouldBe "hello%20world"
    }

    //endregion
    //region Empty and undefined edge-cases ─────────────────────────────────────

    @Test
    fun `all variables undefined yields empty string for expression`() {
        // RFC 6570 §3.2.1: when all variables are undefined the expression expands to ""
        // and operator prefix/first characters are NOT emitted — verified for all 8 operators
        UriTemplate.expand("{undef}", mapOf("undef" to null)) shouldBe ""
        UriTemplate.expand("{+undef}", mapOf("undef" to null)) shouldBe ""
        UriTemplate.expand("{#undef}", mapOf("undef" to null)) shouldBe ""
        UriTemplate.expand("{.undef}", mapOf("undef" to null)) shouldBe ""
        UriTemplate.expand("{/undef}", mapOf("undef" to null)) shouldBe ""
        UriTemplate.expand("{;undef}", mapOf("undef" to null)) shouldBe ""
        UriTemplate.expand("{?undef}", mapOf("undef" to null)) shouldBe ""
        UriTemplate.expand("{&undef}", mapOf("undef" to null)) shouldBe ""
    }

    @Test
    fun `empty list and empty map are treated as undefined`() {
        UriTemplate.expand("{list}", mapOf("list" to emptyList<String>())) shouldBe ""
        UriTemplate.expand("{keys}", mapOf("keys" to emptyMap<String, String>())) shouldBe ""
    }

    @Test
    fun `empty expression braces return malformed marker`() {
        expand("{}") shouldBe "{}"
    }

    @Test
    fun `template with unclosed brace throws IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> { UriTemplate("{unclosed") }
    }

    //endregion
    //region Companion function ─────────────────────────────────────────────────

    @Test
    fun `companion expand delegates to instance expand`() {
        UriTemplate.expand("http://example.com/{path}", mapOf("path" to "home")) shouldBe
            "http://example.com/home"
    }

    //endregion
    //region Prefix modifier clamping ───────────────────────────────────────────

    @Test
    fun `prefix longer than value uses full value`() {
        UriTemplate.expand("{var:30}", mapOf("var" to "short")) shouldBe "short"
    }

    @Test
    fun `prefix of zero is treated as positive constraint clamped to empty`() {
        // :0 is not valid per the spec (max-length = %x31-39 0*3DIGIT), but we handle it gracefully.
        UriTemplate.expand("{var:0}", mapOf("var" to "value")) shouldBe ""
    }

    //endregion
    //region Non-String variable values ─────────────────────────────────────────

    @Test
    fun `map entries with null values are filtered out in expansion`() {
        // resolveValue Map branch: entries where value is null are dropped via mapNotNull
        val result = UriTemplate.expand("{keys}", mapOf("keys" to mapOf("a" to "1", "b" to null, "c" to "3")))
        result shouldBe "a,1,c,3"
    }

    @Test
    fun `non-String non-List non-Map value is converted via toString`() {
        // resolveValue's else branch: Int, Boolean, etc. are coerced to String
        UriTemplate.expand("{n}", mapOf("n" to 42)) shouldBe "42"
        UriTemplate.expand("{flag}", mapOf("flag" to true)) shouldBe "true"
    }

    //endregion
    //region Empty-string element in exploded named-operator list ───────────────

    @Test
    fun `exploded list with empty-string element uses ifemp for named operators`() {
        // {;list*} where one element is "" → ;list instead of ;list=
        UriTemplate.expand("{;list*}", mapOf("list" to listOf("a", "", "b"))) shouldBe ";list=a;list;list=b"
        // {?list*} where one element is "" → ?list=&... (ifemp = "=")
        UriTemplate.expand("{?list*}", mapOf("list" to listOf("a", "", "b"))) shouldBe "?list=a&list=&list=b"
    }

    @Test
    fun `exploded assoc with empty-string value uses ifemp for named operators`() {
        // {;keys*} where a value is "" → ;key instead of ;key=
        UriTemplate.expand("{;keys*}", mapOf("keys" to mapOf("a" to "1", "b" to "", "c" to "3"))) shouldBe
            ";a=1;b;c=3"
        // {?keys*} where a value is "" → ?a=1&b=&c=3 (ifemp = "=")
        UriTemplate.expand("{?keys*}", mapOf("keys" to mapOf("a" to "1", "b" to "", "c" to "3"))) shouldBe
            "?a=1&b=&c=3"
    }
}
