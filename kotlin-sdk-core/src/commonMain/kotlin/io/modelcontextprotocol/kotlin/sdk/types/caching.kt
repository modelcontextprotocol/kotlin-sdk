package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Defines where a cached result may be reused. */
@Serializable
@ExperimentalMcpApi
public enum class CacheScope {
    /** The result may only be reused within the same authorization context. */
    @SerialName("private")
    Private,

    /** The result may be shared across authorization contexts. */
    @SerialName("public")
    Public,
}

/**
 * A result carrying client-side caching directives.
 *
 * Both fields travel on every result that carries them, and say for how long a client may reuse the
 * result and for whom. The defaults — immediately stale, private — are conservative, so a handler
 * that sets neither still produces a valid result without enabling shared caching by accident.
 *
 * The set of results that carry directives is closed: `server/discover`, the four list methods and
 * `resources/read`.
 */
@ExperimentalMcpApi
public sealed interface CacheableResult : RequestResult {
    /**
     * How long, in milliseconds, a client may treat this result as fresh. `0` is immediately stale.
     *
     * Never negative.
     */
    public val ttlMs: Long

    /** Whether shared caches may serve this result to principals other than the one that fetched it. */
    public val cacheScope: CacheScope
}
