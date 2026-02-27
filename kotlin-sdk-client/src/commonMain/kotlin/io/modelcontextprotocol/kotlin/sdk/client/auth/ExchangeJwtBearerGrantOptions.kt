package io.modelcontextprotocol.kotlin.sdk.client.auth

/**
 * Options for [EnterpriseAuth.exchangeJwtBearerGrant] — performs Step 2 of the
 * Enterprise Managed Authorization (SEP-990) flow.
 *
 * Posts an RFC 7523 JWT Bearer grant exchange to the MCP authorization server's token
 * endpoint, exchanging the JAG (JWT Authorization Grant / ID-JAG) for a standard OAuth
 * 2.0 access token that can be used to call the MCP server.
 *
 * @param tokenEndpoint The full URL of the MCP authorization server's token endpoint.
 * @param assertion The JWT Authorization Grant (ID-JAG) obtained from Step 1.
 * @param clientId The OAuth 2.0 client ID registered at the MCP authorization server.
 * @param clientSecret The OAuth 2.0 client secret (optional; `null` for public clients).
 * @param scope Optional `scope` parameter for the token request.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523">RFC 7523</a>
 */
public class ExchangeJwtBearerGrantOptions(
    public val tokenEndpoint: String,
    public val assertion: String,
    public val clientId: String,
    public val clientSecret: String? = null,
    public val scope: String? = null,
)
