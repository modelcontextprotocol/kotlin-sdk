package io.modelcontextprotocol.kotlin.sdk.client.auth

/**
 * Options for [EnterpriseAuth.discoverAndRequestJwtAuthorizationGrant] — performs Step 1
 * of the Enterprise Managed Authorization (SEP-990) flow, first discovering the IdP's
 * token endpoint via RFC 8414 metadata discovery, then requesting the JAG.
 *
 * If [idpTokenEndpoint] is provided, the discovery step is skipped and the provided
 * endpoint is used directly.
 *
 * @param idpUrl The base URL of the enterprise IdP. Used for RFC 8414 discovery when
 *   [idpTokenEndpoint] is not set (tries `/.well-known/oauth-authorization-server` and
 *   then `/.well-known/openid-configuration`).
 * @param idToken The OIDC ID token issued by the enterprise IdP.
 * @param clientId The OAuth 2.0 client ID registered at the enterprise IdP.
 * @param idpTokenEndpoint Optional override for the IdP's token endpoint. When provided,
 *   RFC 8414 discovery is skipped.
 * @param clientSecret The OAuth 2.0 client secret (optional; `null` for public clients).
 * @param audience Optional `audience` parameter for the token exchange request.
 * @param resource Optional `resource` parameter for the token exchange request.
 * @param scope Optional `scope` parameter for the token exchange request.
 */
public data class DiscoverAndRequestJwtAuthGrantOptions(
    public val idpUrl: String,
    public val idToken: String,
    public val clientId: String,
    public val idpTokenEndpoint: String? = null,
    public val clientSecret: String? = null,
    public val audience: String? = null,
    public val resource: String? = null,
    public val scope: String? = null,
) {
    override fun toString(): String =
        "DiscoverAndRequestJwtAuthGrantOptions(idpUrl=$idpUrl, idToken=<redacted>, " +
            "clientId=$clientId, idpTokenEndpoint=$idpTokenEndpoint, " +
            "clientSecret=${if (clientSecret != null) "<redacted>" else "null"}, " +
            "audience=$audience, resource=$resource, scope=$scope)"
}
