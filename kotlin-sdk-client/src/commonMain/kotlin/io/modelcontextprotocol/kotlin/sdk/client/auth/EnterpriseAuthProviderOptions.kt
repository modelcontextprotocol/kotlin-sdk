package io.modelcontextprotocol.kotlin.sdk.client.auth

/**
 * Configuration options for [EnterpriseAuthProvider].
 *
 * At minimum, [clientId] and [assertionCallback] are required.
 *
 * @param clientId The OAuth 2.0 client ID registered at the MCP authorization server. Required.
 * @param assertionCallback Callback that obtains a JWT Authorization Grant (ID-JAG) assertion
 *   for the given [EnterpriseAuthAssertionContext]. Required. The callback receives context
 *   describing the MCP resource and its authorization server, and must return the assertion
 *   string (e.g., the result of [EnterpriseAuth.requestJwtAuthorizationGrant]).
 * @param clientSecret The OAuth 2.0 client secret. Optional for public clients.
 * @param scope The `scope` parameter to request when exchanging the JWT bearer grant. Optional.
 */
public class EnterpriseAuthProviderOptions(
    public val clientId: String,
    public val assertionCallback: suspend (EnterpriseAuthAssertionContext) -> String,
    public val clientSecret: String? = null,
    public val scope: String? = null,
)
