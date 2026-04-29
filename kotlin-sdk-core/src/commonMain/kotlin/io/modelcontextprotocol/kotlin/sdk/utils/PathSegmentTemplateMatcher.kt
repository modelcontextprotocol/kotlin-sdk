package io.modelcontextprotocol.kotlin.sdk.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.decodeURLPart
import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate
import kotlinx.collections.immutable.toImmutableMap
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

// Max URL/path length to prevent DoS or unexpected large payloads.
private const val MAX_URL_LENGTH = 2048

// Max template/uri depth to prevent overly nested or complex templates.
private const val MAX_DEPTH = 50

// Literal segments are more specific than variable captures.
private const val LITERAL_MATCH_SCORE = 2
private const val VARIABLE_MATCH_SCORE = 1

/**
 * A [ResourceTemplateMatcher] that matches resource URIs against an RFC 6570 Level 1
 * URI template by splitting both the URI and the template on `/` and comparing each
 * segment in order.
 *
 * ### Supported template syntax
 *
 * Only RFC 6570 **Level 1** is supported: simple `{variable}` expressions where the
 * entire path segment is a single variable. Operator expressions (`{+var}`, `{#var}`,
 * `{.var}`, `{/var}`, etc.) and multi-variable expressions (`{a,b}`) are **not**
 * recognized — segments containing them are treated as literals.
 *
 * ### Matching rules
 *
 * - The URI and template must have the same number of `/`-delimited segments.
 * - Literal segments must match exactly (after percent-decoding the URI segment).
 * - `{variable}` segments capture the percent-decoded URI segment value.
 * - Query strings and fragments (`?`, `#`) are **not** stripped — they become part of
 *   the captured variable value for the segment that contains them.
 *
 * ### Specificity scoring
 *
 * When multiple templates match the same URI, each matched literal segment contributes
 * 2 points and each variable capture contributes 1 point. The highest-scoring match wins.
 *
 * ### Safety limits
 *
 * - URIs longer than [maxUriLength] characters are rejected.
 * - Templates and URIs with more than [maxDepth] segments are rejected.
 *
 * ### Security contract for handler authors
 *
 * Values in [MatchResult.variables] are attacker-controlled strings extracted from
 * the incoming URI. They are percent-decoded (one pass only) before being returned.
 * Handlers **must** treat them as untrusted input and validate or sanitize them
 * before using them to construct file paths, database queries, downstream URLs, or
 * any other security-sensitive operation.
 *
 * In particular:
 * - A decoded value may contain `/`, `..`, null bytes (`\u0000`), `?`, or `#`.
 * - `%252F` in the URI becomes `%2F` in the variable — exactly one decode pass is applied.
 *
 * ### Platform limitations
 *
 * Dot-segment normalization (resolving `..` and `.` in the URI path) is performed
 * on JVM and JS targets using the platform-native URI parser. On native and WASM targets
 * no normalizer is available, so dot-segment traversal is **not** mitigated at this
 * layer — handlers on those targets must normalize paths themselves.
 *
 * @property resourceTemplate the resource template to match against; must follow RFC 6570 Level 1 syntax
 * @param maxDepth maximum allowed segment count for the template/uri (defaults to 50)
 * @param maxUriLength maximum allowed length for incoming URIs (defaults to 2048)
 */
public class PathSegmentTemplateMatcher @JvmOverloads constructor(
    override val resourceTemplate: ResourceTemplate,
    private val maxDepth: Int = MAX_DEPTH,
    private val maxUriLength: Int = MAX_URL_LENGTH,
) : ResourceTemplateMatcher {

    public companion object {
        /**
         * A [ResourceTemplateMatcherFactory] that creates [PathSegmentTemplateMatcher] instances
         * with default limits. Pass this to [io.modelcontextprotocol.kotlin.sdk.server.ServerOptions]
         * to use path-segment matching, or supply a custom factory to override the matching strategy.
         */
        @JvmStatic
        public val factory: ResourceTemplateMatcherFactory = ResourceTemplateMatcherFactory {
            PathSegmentTemplateMatcher(it)
        }

        @JvmStatic
        private val logger = KotlinLogging.logger {}
    }

    private val templateParts: List<String>

    // Maps segment index to variable name; indices absent from this map are literal segments.
    private val variableIndices: Map<Int, String>

    init {
        val template = resourceTemplate.uriTemplate
        require(template.isNotBlank()) { "Resource template cannot be blank" }
        templateParts = template.trim('/').split("/")
        require(templateParts.size <= maxDepth) {
            "Template is too complex (max depth=$maxDepth)"
        }

        val vars = mutableMapOf<Int, String>()
        for (i in templateParts.indices) {
            val segment = templateParts[i]
            if (segment.startsWith("{") && segment.endsWith("}")) {
                val name = segment.removeSurrounding("{", "}").trim()
                require(name.isNotEmpty()) { "Invalid variable name in template: $segment" }
                vars[i] = name
            }
        }
        variableIndices = vars.toImmutableMap()
    }

    override fun match(resourceUri: String): MatchResult? {
        if (resourceUri.length > maxUriLength) {
            logger.debug { "URL is too long (max=$maxUriLength)" }
            return null
        }

        // Resolve dot-segments (e.g. /a/../b → /a/b) before splitting.
        // Prevents traversal attacks on JVM/JS; no-op on native/WASM targets.
        val normalized = normalizeUri(resourceUri)

        val urlParts = normalized.trim('/').split("/")
        if (urlParts.size > maxDepth) {
            logger.debug { "URI has too many segments (max=$maxDepth)" }
            return null
        }
        if (urlParts.size != templateParts.size) return null

        val variables = mutableMapOf<String, String>()
        var score = 0

        for (i in templateParts.indices) {
            val urlSegment = urlParts[i].decodeURLPart()
            val variableName = variableIndices[i]
            if (variableName != null) {
                variables[variableName] = urlSegment
                score += VARIABLE_MATCH_SCORE
            } else if (templateParts[i] == urlSegment) {
                score += LITERAL_MATCH_SCORE
            } else {
                return null
            }
        }

        return MatchResult(variables = variables.toImmutableMap(), score = score)
    }
}
