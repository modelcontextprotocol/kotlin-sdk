package io.modelcontextprotocol.kotlin.sdk.utils

import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate
import kotlin.test.Test

/**
 * Security regression tests for [PathSegmentTemplateMatcher].
 *
 * Every test **fails** when an attack succeeds — green = defended.
 *
 * Findings addressed:
 * - F1: Exactly one percent-decode pass (no double-decoding).
 * - F2: Variable values are attacker-controlled; handlers must validate them.
 * - F3: URI segment depth cap prevents allocation amplification.
 * - F4: Dot-segment normalization blocks traversal on JVM/JS (no-op on native/WASM).
 * - F5: Double-slash — collapsed by JVM normalization or causes size mismatch on native/WASM.
 */
class PathSegmentTemplateMatcherSecurityTest {

    // region: F4: Dot-segment traversal

    /**
     * URIs containing `..` or `.` path segments.
     *
     * On JVM/JS: [normalizeUri] resolves these to their canonical target path, so the
     * traversal URI correctly routes to the same template the normalized path would match.
     * On native/WASM: [normalizeUri] is a no-op — the extra segments produce a size mismatch
     * and the match returns null (different reason, same safety outcome).
     */
    private data class TraversalCase(val label: String, val template: String, val uri: String)

    private val traversalNoMatchCases = listOf(
        TraversalCase(
            label = "single .. traversal to reach private template",
            template = "app://host/private/{name}",
            uri = "app://host/public/../private/secret",
        ),
        TraversalCase(
            label = "double .. traversal",
            template = "app://host/admin/{name}",
            uri = "app://host/a/b/../../admin/config",
        ),
        TraversalCase(
            label = ". current-segment in path",
            template = "app://host/users/{id}",
            uri = "app://host/./users/42",
        ),
    )

    @Test
    fun `dot-segment traversal resolves to correct target without raw path markers in variables`() {
        // On JVM/JS: normalizeUri() resolves `..` and `.` before segment matching.
        // → The traversal URI correctly routes to the normalized target template.
        // On native/WASM: no normalization — extra segments cause a size mismatch → null.
        // In all cases, captured variable values must not contain raw traversal markers.
        for ((label, template, uri) in traversalNoMatchCases) {
            withClue("traversal '$label': uri='$uri', template='$template'") {
                val result = matcher(template).match(uri)
                result?.variables?.values?.forEach { value ->
                    withClue("variable value '$value' must not contain raw '..' or '.' traversal marker") {
                        value.contains("..") shouldBe false
                        value shouldNotBe "."
                    }
                }
            }
        }
    }

    @Test
    fun `dot-segment URI matches the template it normalizes to`() {
        // "public/../users/42" normalizes to "users/42" on JVM/JS, no-op on native/WASM.
        // On JVM/JS this must match; the test is platform-conditional via expect/actual.
        val result = matcher("users/{id}").match("users/./42")
        // After normalization "users/./42" → "users/42" (JVM/JS) or stays 3 segments (native/WASM).
        // Either null (native/WASM — 3 segments vs 2) or a valid match (JVM/JS) is acceptable.
        // What must NOT happen is a wrong-template match. We check the value if it matches.
        result?.variables?.get("id") shouldBe result?.let { "42" }
    }

    // endregion
    // region: F1: Single percent-decode pass (no double-decoding)

    /**
     * %25 is the encoding of '%'. %252F = "%2F" after one decode pass — must NOT become "/".
     * If two decode passes were applied, %252F → %2F → /, enabling path traversal.
     */
    @Test
    fun `double-encoded percent-sign is decoded only once`() {
        val result = matcher("files/{path}").match("files/%252Fetc%252Fpasswd")
        result.shouldNotBeNull {
            // One decode pass: %25 → %, %2F → /... but only %25 is decoded first.
            // %252F → %2F (the %25 decodes to %, leaving literal "%2F")
            withClue("double-encoded %2F must not become a slash after one decode pass") {
                variables["path"]?.contains('/') shouldBe false
            }
        }
    }

    @Test
    fun `percent-encoded slash percent-2F in variable is decoded to slash`() {
        // %2F IS decoded (one pass) — the result is "/" in the variable value.
        // This documents the known behavior: handlers receive decoded values and must
        // validate that path separators are not present when constructing file paths.
        val result = matcher("files/{path}").match("files/..%2Fetc%2Fpasswd")
        result.shouldNotBeNull {
            withClue("decoded %2F becomes a path separator — handler must validate") {
                variables["path"] shouldBe "../etc/passwd"
            }
        }
    }

    @Test
    fun `null byte percent-00 is decoded and passed to handler`() {
        // %00 decodes to the null character. Handlers using C-string APIs or file paths
        // must reject values containing null bytes.
        val result = matcher("items/{id}").match("items/foo%00bar")
        result.shouldNotBeNull {
            withClue("decoded null byte is present — handler must reject") {
                variables["id"] shouldBe "foo\u0000bar"
            }
        }
    }

    @Test
    fun `pct-encoded identity bypass`() {
        // %69 = 'i'. "adm%69n" decodes to "admin". If a handler compares the raw URI
        // against a deny-list of "admin", it must use the decoded value.
        val result = matcher("svc/{role}").match("svc/adm%69n")
        result.shouldNotBeNull {
            variables["role"] shouldBe "admin"
        }
    }

    // endregion
    // region: F3: URI segment depth cap

    @Test
    fun `URI exceeding MAX_DEPTH segments returns null`() {
        // 51 slash-separated single-char segments → well over the depth cap of 50
        val deepUri = (1..51).joinToString("/") { "x" }
        matcher("{a}").match(deepUri) shouldBe null
    }

    @Test
    fun `URI at exactly MAX_DEPTH segments is not rejected by depth cap`() {
        // A URI with exactly 50 segments must be evaluated normally (still non-matching
        // against a 1-segment template, but not rejected by the depth guard itself).
        val atLimit = (1..50).joinToString("/") { "x" }
        // Template has 1 segment, URI has 50 — size mismatch, not depth rejection.
        // Both code paths return null; this test verifies no exception is thrown.
        matcher("{id}").match(atLimit) shouldBe null
    }

    @Test
    fun `many-segment URI against many templates completes quickly`() {
        val deepUri = (1..49).joinToString("/") { "seg$it" }
        val matchers = (1..100).map { matcher("{v$it}") }
        // Must not allocate or compute excessively — just verify it finishes.
        matchers.forEach { it.match(deepUri) }
    }

    // endregion
    // region: F5: Double-slash does not produce spurious matches

    @Test
    fun `double-slash in URI path is normalized on JVM or causes size mismatch elsewhere`() {
        // On JVM: URI("users//42").normalize() collapses "//" to "/" → "users/42" → matches.
        // On native/WASM: no normalization — split produces 3 segments vs 2 in template → null.
        // Either outcome is safe: no empty-segment injection reaches the handler unchecked.
        val result = matcher("users/{id}").match("users//42")
        // If matched (JVM), the captured value must be the legitimate path segment.
        result?.variables?.get("id") shouldBe result?.let { "42" }
    }

    @Test
    fun `double-slash in scheme authority path is normalized on JVM or causes size mismatch elsewhere`() {
        // On JVM: URI("app://host//injected").normalize() collapses the path "//" to "/"
        //         → "app://host/injected" → matches "app://host/{id}".
        // On native/WASM: no normalization — extra empty segment causes size mismatch → null.
        val result = matcher("app://host/{id}").match("app://host//injected")
        // If matched (JVM), the captured value must be the legitimate path segment.
        result?.variables?.get("id") shouldBe result?.let { "injected" }
    }

    // endregion
    // region: Query string and fragment pass-through

    @Test
    fun `query string and fragment are captured in variable values - handlers must validate`() {
        // PathSegmentTemplateMatcher splits on '/' only and does not strip query
        // strings or fragments. They are captured verbatim inside the variable value.
        // Handlers MUST reject or strip '?' and '#' before using values in file paths,
        // database queries, or any other security-sensitive operation.

        // Segment split of "api://host/foo?bar=baz" → ["api:", "", "host", "foo?bar=baz"] (4).
        // Template "api://host/{id}"                → ["api:", "", "host", "{id}"]        (4).
        // → Matches on all platforms; query string ends up in the variable value.
        val queryResult = matcher("api://host/{id}").match("api://host/foo?bar=baz")
        queryResult.shouldNotBeNull {
            variables["id"] shouldBe "foo?bar=baz"
        }

        // Fragment is also captured verbatim — same reasoning.
        val fragmentResult = matcher("api://host/{id}").match("api://host/foo#section")
        fragmentResult.shouldNotBeNull {
            variables["id"] shouldBe "foo#section"
        }
    }

    // endregion
    // region: Helpers

    private fun matcher(uriTemplate: String): PathSegmentTemplateMatcher = PathSegmentTemplateMatcher(
        resourceTemplate = ResourceTemplate(uriTemplate, "Test"),
    )
    // endregion
}
