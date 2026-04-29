package io.modelcontextprotocol.kotlin.sdk.utils

import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate

/**
 * Represents the result of a successful template match.
 *
 * A higher [score] indicates a more specific match. Implementations must ensure that
 * literal segment matches contribute more to the score than variable captures, so that
 * a fully literal template (e.g., `users/profile`) always outscores a parameterized
 * template (e.g., `users/{id}`) for the same URI.
 *
 * @property variables A mapping of variable names in the template to their matched values in the URL.
 * @property score A non-negative measure of match specificity — higher means more literal segments matched.
 */
public class MatchResult(public val variables: Map<String, String>, public val score: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MatchResult) return false
        return score == other.score && variables == other.variables
    }

    override fun hashCode(): Int = 31 * variables.hashCode() + score

    override fun toString(): String = "MatchResult(variables=$variables, score=$score)"
}

/**
 * Matches resource URIs against a [ResourceTemplate].
 *
 * Implementations parse a URI template once at construction time and then match
 * candidate URIs against that template via [match]. The returned [MatchResult.score]
 * must reflect match specificity so that a selection algorithm can prefer the most
 * specific template when multiple templates match the same URI.
 */
public interface ResourceTemplateMatcher {

    /** The template to match URIs against. */
    public val resourceTemplate: ResourceTemplate

    /**
     * Matches a given resource URI against the defined resource template.
     *
     * @param resourceUri The resource URI to be matched.
     * @return A [MatchResult] containing the mapping of variables and a match score
     *      if the URI matches the template, or null if no match is found.
     */
    public fun match(resourceUri: String): MatchResult?
}

/**
 * Factory interface for creating instances of [ResourceTemplateMatcher].
 *
 * A [ResourceTemplateMatcher] is used to match resource URIs against a given
 * [ResourceTemplate], which adheres to the RFC 6570 URI Template specification.
 * This factory abstracts the creation process of a matcher, allowing different
 * implementations to define custom matching logic or safeguards (e.g., security
 * measures or restrictions on template complexity).
 */
public fun interface ResourceTemplateMatcherFactory {
    /**
     * Creates a resource template matcher for the given resource template.
     *
     * @param resourceTemplate The resource template to create a matcher for.
     * @return A [ResourceTemplateMatcher] instance that can match URIs against the provided template.
     */
    public fun create(resourceTemplate: ResourceTemplate): ResourceTemplateMatcher
}
