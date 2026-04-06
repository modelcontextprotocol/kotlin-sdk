package io.modelcontextprotocol.kotlin.sdk.client.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.ComparableTimeMark

/**
 * OAuth 2.0 access token response returned by the MCP authorization server after a
 * successful RFC 7523 JWT Bearer grant exchange.
 *
 * This is the result of Step 2 in the Enterprise Managed Authorization (SEP-990) flow:
 * exchanging the JWT Authorization Grant (ID-JAG) for an access token at the MCP
 * authorization server.
 *
 * The [expiresAt] field is **not serialized** — it is computed from [expiresIn] by
 * [EnterpriseAuth.exchangeJwtBearerGrant] immediately after deserialization using a
 * monotonic time mark.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523">RFC 7523</a>
 */
@Serializable
public class JwtBearerAccessTokenResponse(
    @SerialName("access_token") public val accessToken: String? = null,
    @SerialName("token_type") public val tokenType: String? = null,
    /** Lifetime of the access token in seconds, as reported by the authorization server. */
    @SerialName("expires_in") public val expiresIn: Int? = null,
    public val scope: String? = null,
    @SerialName("refresh_token") public val refreshToken: String? = null,
) {
    /**
     * Absolute expiry time computed from [expiresIn] after deserialization. Not serialized.
     *
     * Set by [EnterpriseAuth.exchangeJwtBearerGrant] using [kotlin.time.TimeSource.Monotonic].
     */
    public var expiresAt: ComparableTimeMark? = null
        internal set

    /**
     * Returns `true` if this token has passed its [expiresAt] mark, or `false` if
     * no expiry information was provided by the server.
     */
    public fun isExpired(): Boolean = expiresAt?.hasPassedNow() ?: false
}
