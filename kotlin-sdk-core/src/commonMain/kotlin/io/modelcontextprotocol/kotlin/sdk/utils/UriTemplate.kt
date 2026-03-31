package io.modelcontextprotocol.kotlin.sdk.utils

import io.modelcontextprotocol.kotlin.sdk.utils.UriTemplateParser.Modifier
import io.modelcontextprotocol.kotlin.sdk.utils.UriTemplateParser.OpInfo
import io.modelcontextprotocol.kotlin.sdk.utils.UriTemplateParser.Part
import io.modelcontextprotocol.kotlin.sdk.utils.UriTemplateParser.VarSpec
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * Result of matching a URI against a [UriTemplate].
 *
 * @property variables Variable values extracted from the URI, keyed by variable name.
 *   All variables from every expression are captured, including multi-variable expressions
 *   like `{x,y}` or `{?x,y}`. Values are the raw (pct-encoded) substrings from the URI.
 * @property score Specificity score — total literal character count of the matched template.
 *   Higher scores indicate more specific templates. Use this to resolve ambiguity when
 *   multiple templates match the same URI.
 *
 * Example: selecting the most specific match
 * ```
 * val templates = listOf(
 *     UriTemplate("users/{id}"),         // score = 6  ("users/")
 *     UriTemplate("users/profile"),      // score = 13 ("users/profile")
 * )
 * val best = templates
 *     .mapNotNull { t -> t.match(uri)?.let { t to it } }
 *     .maxByOrNull { (_, result) -> result.score }
 * ```
 */
public data class MatchResult(val variables: Map<String, String>, val score: Int) {
    /** Returns the value of variable [name], or `null` if it was not captured. */
    public operator fun get(name: String): String? = variables[name]
}

/**
 * RFC 6570 URI Template implementation.
 *
 * Supports all four levels of URI template expansion:
 * - **Level 1** – Simple string expansion: `{var}`
 * - **Level 2** – Reserved (`{+var}`) and fragment (`{#var}`) expansion
 * - **Level 3** – Label (`{.var}`), path (`{/var}`), path-parameter (`{;var}`),
 *   query (`{?var}`), and query-continuation (`{&var}`) expansion with multiple variables
 * - **Level 4** – Prefix modifiers (`{var:3}`) and explode modifiers (`{var*}`)
 *
 * The template string is parsed eagerly on construction.
 *
 * Variable values supplied to [expand] may be:
 * - `null` or absent key → undefined (skipped in expansion)
 * - [String] → simple string value
 * - [List] of [String] → list value (an empty list is treated as undefined)
 * - [Map] of [String] to [String] → associative array (an empty map is treated as undefined)
 *
 * @param template The URI template string.
 * @throws IllegalArgumentException if the template is malformed.
 * @see [RFC 6570](https://www.rfc-editor.org/rfc/rfc6570)
 */
@Suppress("TooManyFunctions")
public class UriTemplate(public val template: String) {

    //region Typed variable value (expansion-only) ─────────────────────────────

    private sealed interface VarValue {
        @JvmInline
        value class Str(val value: String) : VarValue
        data class Lst(val values: List<String>) : VarValue
        data class Assoc(val pairs: List<Pair<String, String>>) : VarValue
    }

    //endregion
    //region Parsed model ───────────────────────────────────────────────────────

    private val parts: List<Part> = UriTemplateParser.parse(template)

    /**
     * The total number of encoded literal characters in this template.
     *
     * This value equals [MatchResult.score] when this template successfully matches a URI,
     * making it useful for pre-ranking templates before matching.
     */
    public val literalLength: Int = parts.sumOf { if (it is Part.Literal) it.text.length else 0 }

    //endregion
    //region Public API ─────────────────────────────────────────────────────────

    /**
     * Expands the URI template with the given [variables].
     *
     * @return The expanded URI string.
     */
    public fun expand(variables: Map<String, Any?>): String = buildString {
        for (part in parts) {
            when (part) {
                is Part.Literal -> append(part.text)
                is Part.Expression -> append(expandExpression(part, variables))
            }
        }
    }

    /**
     * Returns a compiled [UriTemplateMatcher] for this template (cached lazily).
     * Use [match] for one-off checks; use [matcher] when matching against many URIs.
     */
    public fun matcher(): UriTemplateMatcher = _matcher

    private val _matcher: UriTemplateMatcher by lazy {
        UriTemplateMatcher.build(parts, literalLength)
    }

    /**
     * Matches [uri] against this template and returns extracted variables plus a
     * specificity [MatchResult.score], or `null` if the URI does not match.
     *
     * Example:
     * ```
     * UriTemplate("https://api.example.com/users/{id}/posts/{postId}")
     *     .match("https://api.example.com/users/alice/posts/99")
     * // MatchResult(variables = {"id": "alice", "postId": "99"}, score = 36)
     * ```
     */
    public fun match(uri: String): MatchResult? = _matcher.match(uri)

    /**
     * Compares this instance of [UriTemplate] with another object for equality.
     *
     * @param other The object to compare with this instance. It may be `null` or of any type.
     * @return `true` if the given object is a [UriTemplate] and its `template` property is equal
     *         to that of this instance, otherwise `false`.
     */
    override fun equals(other: Any?): Boolean = other is UriTemplate && template == other.template

    /**
     * Returns a hash code value for this instance.
     *
     * The hash code is based on the `template` property.
     */
    override fun hashCode(): Int = template.hashCode()

    /**
     * Returns a string representation of this instance.
     *
     * The string representation is in the format `UriTemplate(template)`.
     */
    override fun toString(): String = "UriTemplate($template)"

    public companion object {
        /**
         * Expands [template] with [variables] in a single call.
         *
         * Equivalent to `UriTemplate(template).expand(variables)`. The template is parsed on
         * every call without caching. Prefer constructing a [UriTemplate] instance when the
         * same template is expanded repeatedly.
         *
         * Particularly useful from Java, where it is available as a static method.
         */
        @JvmStatic
        public fun expand(template: String, variables: Map<String, Any?>): String =
            UriTemplate(template).expand(variables)

        /**
         * Returns `true` if [uri] matches [template], without retaining a [UriTemplateMatcher] instance.
         * Equivalent to `UriTemplate(template).matcher().matches(uri)`.
         */
        @JvmStatic
        public fun matches(template: String, uri: String): Boolean = UriTemplate(template).matcher().matches(uri)
    }

    //endregion
    //region Expression expansion ───────────────────────────────────────────────

    private fun expandExpression(part: Part.Expression, variables: Map<String, Any?>): String {
        if (part.varSpecs.isEmpty()) return "{}"
        val op = part.op
        return buildString {
            var firstItem = true
            for (varSpec in part.varSpecs) {
                val value = resolveValue(variables[varSpec.name]) ?: continue
                for (item in itemsForVar(varSpec, value, op)) {
                    append(if (firstItem) op.first else op.sep)
                    firstItem = false
                    append(item)
                }
            }
        }
    }

    private fun itemsForVar(spec: VarSpec, value: VarValue, op: OpInfo): List<String> = when (value) {
        is VarValue.Str -> listOf(expandStr(spec, value.value, op))
        is VarValue.Lst -> expandList(spec, value.values, op)
        is VarValue.Assoc -> expandAssoc(spec, value.pairs, op)
    }

    //endregion
    //region String expansion ───────────────────────────────────────────────────

    private fun expandStr(spec: VarSpec, raw: String, op: OpInfo): String = buildString {
        if (op.named) {
            append(UriTemplateParser.pctEncodeUnreserved(spec.name))
            if (raw.isEmpty()) {
                append(op.ifemp)
                return@buildString
            }
            append('=')
        }
        val encoded = when (val mod = spec.modifier) {
            is Modifier.Prefix -> UriTemplateParser.pctEncode(
                UriTemplateParser.truncateCodePoints(raw, mod.length),
                op.allowReserved,
            )

            else -> UriTemplateParser.pctEncode(raw, op.allowReserved)
        }
        append(encoded)
    }

    //endregion
    //region List expansion ─────────────────────────────────────────────────────

    private fun expandList(spec: VarSpec, values: List<String>, op: OpInfo): List<String> = when (spec.modifier) {
        Modifier.Explode -> values.map { v ->
            if (op.named) {
                val name = UriTemplateParser.pctEncodeUnreserved(spec.name)
                if (v.isEmpty()) {
                    "$name${op.ifemp}"
                } else {
                    "$name=${UriTemplateParser.pctEncode(v, op.allowReserved)}"
                }
            } else {
                UriTemplateParser.pctEncode(v, op.allowReserved)
            }
        }

        else -> listOf(
            buildString {
                if (op.named) {
                    append(UriTemplateParser.pctEncodeUnreserved(spec.name))
                    append('=')
                }
                append(values.joinToString(",") { UriTemplateParser.pctEncode(it, op.allowReserved) })
            },
        )
    }

    //endregion
    //region Associative-array expansion ────────────────────────────────────────

    private fun expandAssoc(spec: VarSpec, pairs: List<Pair<String, String>>, op: OpInfo): List<String> =
        when (spec.modifier) {
            Modifier.Explode -> pairs.map { (k, v) ->
                val encK = UriTemplateParser.pctEncode(k, op.allowReserved)
                if (v.isEmpty()) {
                    "$encK${op.ifemp}"
                } else {
                    "$encK=${UriTemplateParser.pctEncode(v, op.allowReserved)}"
                }
            }

            else -> listOf(
                buildString {
                    if (op.named) {
                        append(UriTemplateParser.pctEncodeUnreserved(spec.name))
                        append('=')
                    }
                    append(
                        pairs.joinToString(",") { (k, v) ->
                            "${UriTemplateParser.pctEncode(k, op.allowReserved)},${
                                UriTemplateParser.pctEncode(
                                    v,
                                    op.allowReserved,
                                )
                            }"
                        },
                    )
                },
            )
        }

    //endregion
    //region Value resolution ───────────────────────────────────────────────────

    private fun resolveValue(raw: Any?): VarValue? = when (raw) {
        null -> null

        is String -> VarValue.Str(raw)

        is List<*> -> {
            val strs = raw.filterNotNull().map { it.toString() }
            if (strs.isEmpty()) null else VarValue.Lst(strs)
        }

        is Map<*, *> -> {
            val pairs = raw.entries
                .mapNotNull { (k, v) -> if (k != null && v != null) k.toString() to v.toString() else null }
            if (pairs.isEmpty()) null else VarValue.Assoc(pairs)
        }

        else -> VarValue.Str(raw.toString())
    }
    //endregion
}
