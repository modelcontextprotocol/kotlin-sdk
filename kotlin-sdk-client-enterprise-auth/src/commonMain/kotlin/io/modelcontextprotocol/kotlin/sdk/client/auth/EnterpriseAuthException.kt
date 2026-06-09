package io.modelcontextprotocol.kotlin.sdk.client.auth

/**
 * Exception thrown when an error occurs during the Enterprise Managed Authorization (SEP-990) flow.
 *
 * This includes failures during:
 * - OAuth 2.0 authorization server metadata discovery (RFC 8414)
 * - RFC 8693 token exchange (ID Token → ID-JAG)
 * - RFC 7523 JWT Bearer grant exchange (ID-JAG → Access Token)
 */
public class EnterpriseAuthException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
