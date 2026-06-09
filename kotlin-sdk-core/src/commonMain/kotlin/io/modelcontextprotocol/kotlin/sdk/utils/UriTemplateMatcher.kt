package io.modelcontextprotocol.kotlin.sdk.utils

import io.modelcontextprotocol.kotlin.sdk.utils.UriTemplateParser.Part

/**
 * Compiled regex-based matcher for an RFC 6570 URI template.
 *
 * Created via [UriTemplate.matcher]. Each instance holds a pre-compiled [Regex] and
 * the ordered list of captured variable names, so repeated matching is allocation-cheap.
 *
 * All variables in every expression are captured, including multi-variable expressions
 * like `{x,y}` or `{?x,y}`.
 *
 * Example:
 * ```
 * val matcher = UriTemplate("users/{userId}/posts/{postId}").matcher()
 * val result = matcher.match("users/alice/posts/99")
 * // MatchResult(variables={"userId":"alice","postId":"99"}, score=12)
 * ```
 */
public class UriTemplateMatcher internal constructor(
    private val regex: Regex,
    private val variableNames: List<String>,
    private val score: Int,
) {
    /**
     * Matches [uri] against the compiled template regex.
     *
     * @return a [MatchResult] with extracted variables and score, or `null` if [uri] does not match.
     */
    @Suppress("ReturnCount")
    public fun match(uri: String): MatchResult? {
        if (variableNames.isEmpty()) {
            return if (regex.matches(uri)) MatchResult(emptyMap(), score) else null
        }
        val matchResult = regex.matchEntire(uri) ?: return null
        val variables = variableNames.mapIndexed { idx, name ->
            name to matchResult.groupValues[idx + 1]
        }.toMap()
        return MatchResult(variables = variables, score = score)
    }

    /** Returns `true` if [uri] fully matches this template pattern. */
    public fun matches(uri: String): Boolean = regex.matches(uri)

    internal companion object {
        /**
         * Builds a [UriTemplateMatcher] from pre-parsed template [parts] and the template's [score].
         *
         * All variables in each expression are registered as capture groups.
         * The `+` and `#` operators allow slashes and reserved characters; all others stop at
         * URI delimiters (`/`, `?`, `#`, `,`).
         */
        @Suppress("CyclomaticComplexMethod")
        internal fun build(parts: List<Part>, score: Int): UriTemplateMatcher {
            val variableNames = mutableListOf<String>()
            val pattern = buildString {
                append('^')
                for (part in parts) {
                    when (part) {
                        is Part.Literal -> append(Regex.escape(part.text))

                        is Part.Expression -> {
                            val op = part.op
                            if (part.varSpecs.isEmpty()) continue

                            var firstVar = true
                            for (varSpec in part.varSpecs) {
                                val name = varSpec.name
                                variableNames.add(name)

                                // Emit the operator prefix (first var) or separator (subsequent vars).
                                if (firstVar) {
                                    // For named operators the prefix includes the variable name.
                                    val prefix = when (op.char) {
                                        ';', '?', '&' -> "${op.first}$name="
                                        else -> op.first // NUL/+: "", #: "#", .: ".", /: "/"
                                    }
                                    if (prefix.isNotEmpty()) append(Regex.escape(prefix))
                                    firstVar = false
                                } else {
                                    // For named operators the separator includes the variable name.
                                    val sep = if (op.named) "${op.sep}$name=" else op.sep
                                    append(Regex.escape(sep))
                                }

                                // Each operator restricts which characters a value may contain.
                                // The capture pattern uses the tightest stop-char set per RFC 6570.
                                val capture = when (op.char) {
                                    // '+' and '#' allow reserved chars (including ',') in values
                                    // per RFC 6570 §3.2.3–3.2.4, so comma-containing values
                                    // cannot be reverse-matched correctly.
                                    // RFC 6570 §1.4 acknowledges this: "Variable matching only works well
                                    // if the template expressions are delimited by characters
                                    // that cannot be part of the expansion."
                                    '+', '#' -> "([^,]*)"

                                    // path operators: stop at all URI path and query delimiters
                                    '.', '/' -> "([^/?#,;=&]*)"

                                    // path-parameter: stop at ';' (next param) and query delimiters
                                    ';' -> "([^/?#,;=&]*)"

                                    // query operators: stop at '&' (next pair) too
                                    '?', '&' -> "([^/?#,;&=]*)"

                                    // NUL: unreserved characters only
                                    else -> "([^/?#,;=&]*)"
                                }
                                append(capture)
                            }
                        }
                    }
                }
                append('$')
            }
            return UriTemplateMatcher(Regex(pattern), variableNames, score)
        }
    }
}
