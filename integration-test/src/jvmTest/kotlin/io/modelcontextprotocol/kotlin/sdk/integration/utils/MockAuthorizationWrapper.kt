package io.modelcontextprotocol.kotlin.sdk.integration.utils

import io.modelcontextprotocol.kotlin.sdk.types.McpException

/**
 * Test-only authorization wrapper for validating security test scenarios.
 *
 * According to the MCP specification, authorization is an application-level responsibility.
 * This class demonstrates how applications can implement authorization logic by wrapping
 * server request handlers with permission checks.
 *
 * This is NOT production code - it's a test utility to verify that servers can implement
 * authorization at the application level and properly reject unauthorized requests.
 *
 * Example usage:
 * ```kotlin
 * val authWrapper = MockAuthorizationWrapper(
 *     AuthorizationRules(
 *         allowedPrompts = setOf("public-prompt"),
 *         deniedPrompts = setOf("secret-prompt")
 *     )
 * )
 *
 * server.addPrompt(...) { request ->
 *     if (!authWrapper.isAllowed("prompts", "get", mapOf("name" to request.params.name))) {
 *         throw authWrapper.createDeniedError()
 *     }
 *     // Normal handler logic
 * }
 * ```
 */
class MockAuthorizationWrapper(private val rules: AuthorizationRules) {
    companion object {
        /** Custom error code for authorization denied (server-defined range: -32000 to -32099) */
        const val ERROR_CODE_AUTHORIZATION_DENIED = -32002
    }

    /**
     * Checks if a given operation on a feature is allowed based on the configured rules.
     *
     * @param feature The feature being accessed (e.g., "prompts", "resources", "tools", "logging")
     * @param operation The operation being performed (e.g., "list", "get", "call", "read")
     * @param params Additional parameters for the operation (e.g., prompt name, resource URI)
     * @return true if the operation is allowed, false otherwise
     */
    fun isAllowed(feature: String, operation: String, params: Map<String, Any>): Boolean = when (feature) {
        "prompts" -> {
            val promptName = params["name"] as? String
            checkPromptAccess(promptName)
        }

        "resources" -> {
            val resourceUri = params["uri"] as? String
            checkResourceAccess(resourceUri)
        }

        "tools" -> {
            val toolName = params["name"] as? String
            checkToolAccess(toolName)
        }

        "logging" -> {
            // Logging access is controlled separately
            checkLoggingAccess()
        }

        else -> false // Unknown feature, deny by default
    }

    /**
     * Creates an MCP exception for authorization denied errors.
     * This exception should be thrown when a request is rejected due to insufficient permissions.
     *
     * @param reason Optional reason for the denial (defaults to generic message)
     * @return McpException with authorization denied error code
     */
    fun createDeniedError(reason: String = "Access denied: insufficient permissions"): McpException = McpException(
        code = ERROR_CODE_AUTHORIZATION_DENIED,
        message = reason,
        data = null,
    )

    private fun checkPromptAccess(promptName: String?): Boolean {
        if (promptName == null) {
            // Listing prompts - check if any prompts are allowed
            return rules.allowedPrompts == null || rules.allowedPrompts.isNotEmpty()
        }

        // Check denied list first (explicit deny takes precedence)
        if (rules.deniedPrompts?.contains(promptName) == true) {
            return false
        }

        // Check allowed list (null means all allowed, empty means none allowed)
        return when {
            rules.allowedPrompts == null -> true

            // No restrictions
            rules.allowedPrompts.isEmpty() -> false

            // Empty set means deny all
            else -> rules.allowedPrompts.contains(promptName)
        }
    }

    private fun checkResourceAccess(resourceUri: String?): Boolean {
        if (resourceUri == null) {
            // Listing resources - check if any resources are allowed
            return rules.allowedResources == null || rules.allowedResources.isNotEmpty()
        }

        // Check denied list first
        if (rules.deniedResources?.contains(resourceUri) == true) {
            return false
        }

        // Check allowed list
        return when {
            rules.allowedResources == null -> true
            rules.allowedResources.isEmpty() -> false
            else -> rules.allowedResources.contains(resourceUri)
        }
    }

    private fun checkToolAccess(toolName: String?): Boolean {
        if (toolName == null) {
            // Listing tools - check if any tools are allowed
            return rules.allowedTools == null || rules.allowedTools.isNotEmpty()
        }

        // Check denied list first
        if (rules.deniedTools?.contains(toolName) == true) {
            return false
        }

        // Check allowed list
        return when {
            rules.allowedTools == null -> true
            rules.allowedTools.isEmpty() -> false
            else -> rules.allowedTools.contains(toolName)
        }
    }

    private fun checkLoggingAccess(): Boolean {
        // Simple boolean check for logging
        return rules.allowLogging
    }
}

/**
 * Configuration for authorization rules in integration tests.
 *
 * For each feature (prompts, resources, tools), you can specify:
 * - allowedXxx: Set of allowed items (null = all allowed, empty = none allowed)
 * - deniedXxx: Set of explicitly denied items (takes precedence over allowed)
 *
 * @property allowedPrompts Set of allowed prompt names, or null to allow all
 * @property deniedPrompts Set of explicitly denied prompt names
 * @property allowedResources Set of allowed resource URIs, or null to allow all
 * @property deniedResources Set of explicitly denied resource URIs
 * @property allowedTools Set of allowed tool names, or null to allow all
 * @property deniedTools Set of explicitly denied tool names
 * @property allowLogging Whether logging operations are allowed
 */
data class AuthorizationRules(
    val allowedPrompts: Set<String>? = null,
    val deniedPrompts: Set<String>? = null,
    val allowedResources: Set<String>? = null,
    val deniedResources: Set<String>? = null,
    val allowedTools: Set<String>? = null,
    val deniedTools: Set<String>? = null,
    val allowLogging: Boolean = true,
)
