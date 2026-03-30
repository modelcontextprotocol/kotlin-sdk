package io.modelcontextprotocol.kotlin.sdk.client.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OAuth 2.0 Authorization Server Metadata as defined by RFC 8414.
 *
 * Returned by the `/.well-known/oauth-authorization-server` or
 * `/.well-known/openid-configuration` discovery endpoints and used during
 * Enterprise Managed Authorization (SEP-990) to locate the token endpoint of the
 * enterprise Identity Provider and the MCP authorization server.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8414">RFC 8414</a>
 */
@Serializable
public data class AuthServerMetadata(
    /** The authorization server's issuer identifier URI. */
    val issuer: String? = null,
    /** The URL of the token endpoint used for token exchange and JWT Bearer grant requests. */
    @SerialName("token_endpoint") val tokenEndpoint: String? = null,
    /** The URL of the authorization endpoint (for interactive flows). */
    @SerialName("authorization_endpoint") val authorizationEndpoint: String? = null,
    /** The URL of the JSON Web Key Set (JWKS) for public key retrieval. */
    @SerialName("jwks_uri") val jwksUri: String? = null,
)
