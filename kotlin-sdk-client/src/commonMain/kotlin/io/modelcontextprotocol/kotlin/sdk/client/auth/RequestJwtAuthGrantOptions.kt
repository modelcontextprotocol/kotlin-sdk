package io.modelcontextprotocol.kotlin.sdk.client.auth

/**
 * Options for [EnterpriseAuth.requestJwtAuthorizationGrant] — performs Step 1 of the
 * Enterprise Managed Authorization (SEP-990) flow using a known token endpoint.
 *
 * Posts an RFC 8693 token exchange request to the enterprise IdP's token endpoint and
 * returns the JAG (JWT Authorization Grant / ID-JAG token).
 *
 * @param tokenEndpoint The full URL of the enterprise IdP's token endpoint.
 * @param idToken The OIDC ID token issued by the enterprise IdP.
 * @param clientId The OAuth 2.0 client ID registered at the enterprise IdP.
 * @param clientSecret The OAuth 2.0 client secret (optional; `null` for public clients).
 * @param audience Optional `audience` parameter for the token exchange request.
 * @param resource Optional `resource` parameter for the token exchange request.
 * @param scope Optional `scope` parameter for the token exchange request.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693</a>
 */
public data class RequestJwtAuthGrantOptions(
    public val tokenEndpoint: String,
    public val idToken: String,
    public val clientId: String,
    public val clientSecret: String? = null,
    public val audience: String? = null,
    public val resource: String? = null,
    public val scope: String? = null,
)
