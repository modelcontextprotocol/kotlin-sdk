package io.modelcontextprotocol.kotlin.sdk.client.auth

/**
 * Context passed to the [EnterpriseAuthProviderOptions.assertionCallback].
 *
 * Contains the resource URL of the MCP server and the URL of the authorization server
 * discovered for that resource. The callback uses this context to obtain a suitable
 * assertion (e.g., an OIDC ID token) from the enterprise IdP.
 *
 * @param resourceUrl The base URL of the MCP resource being accessed.
 * @param authorizationServerUrl The URL of the MCP authorization server discovered for
 *   the resource (the `issuer` from RFC 8414 metadata, or the resource base URL).
 */
public class EnterpriseAuthAssertionContext(
    public val resourceUrl: String,
    public val authorizationServerUrl: String,
)
