package io.modelcontextprotocol.kotlin.sdk.client.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * RFC 8693 Token Exchange response for the JAG (JWT Authorization Grant) flow.
 *
 * Returned by the enterprise IdP when exchanging an ID Token for a JWT Authorization
 * Grant (ID-JAG) during Enterprise Managed Authorization (SEP-990).
 *
 * The key fields are:
 * - [accessToken] — the issued JAG (despite the name, this is an ID-JAG, not an OAuth access token)
 * - [issuedTokenType] — must be `urn:ietf:params:oauth:token-type:id-jag`
 * - [tokenType] — informational; per RFC 8693 §2.2.1 it SHOULD be `N_A` when the issued
 *   token is not an access token, but this is not strictly enforced as some conformant
 *   IdPs may omit or vary the field
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693</a>
 */
@Serializable
public data class JagTokenExchangeResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("issued_token_type") val issuedTokenType: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
)
