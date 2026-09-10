package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.CacheScope
import io.modelcontextprotocol.kotlin.sdk.types.CacheableResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** How a call should treat whatever the response cache already holds. */
@ExperimentalMcpApi
public enum class CacheMode {
    /** Serve a fresh entry when there is one, and store what the server answers otherwise. */
    Use,

    /** Ignore any stored entry, ask the server, and store the answer. */
    Refresh,

    /** Ignore the cache entirely, in both directions. */
    Bypass,
}

/**
 * What a cached result is filed under.
 *
 * @property method the JSON-RPC method that produced the result
 * @property paramsKey the part of the request that changes the result — a URI for `resources/read`,
 * empty for the list methods, whose results depend on nothing else
 * @property partition the authorization context the result was fetched under, supplied by the
 * caller. A [CacheScope.Private] entry is never served across partitions; empty scopes it to this
 * client instance.
 */
@ExperimentalMcpApi
public data class CacheKey(val method: String, val paramsKey: String = "", val partition: String = "")

/**
 * A stored result and the terms it was stored under.
 *
 * @property result the result exactly as the server sent it
 * @property expiresAt the instant after which the result is stale
 * @property cacheScope whether shared caches may serve it to other principals
 */
@ExperimentalMcpApi
@OptIn(ExperimentalTime::class)
public data class CacheEntry(val result: RequestResult, val expiresAt: Instant, val cacheScope: CacheScope)

/**
 * Where a client keeps results a server said were worth reusing.
 *
 * Implement it to share a cache across client instances or processes; [InMemoryResponseCacheStore]
 * is the single-instance default. Implementations are called from arbitrary coroutines and must be
 * safe to use concurrently.
 */
@ExperimentalMcpApi
public interface ResponseCacheStore {
    /** The entry filed under [key], whether or not it is still fresh, or `null` when there is none. */
    public suspend fun get(key: CacheKey): CacheEntry?

    /** Files [entry] under [key], replacing any entry already there. */
    public suspend fun put(key: CacheKey, entry: CacheEntry)

    /** Drops every entry produced by [method], whatever its params or partition. */
    public suspend fun invalidate(method: String)

    /** Drops everything. */
    public suspend fun clear()
}

/** The longest a result may be treated as fresh, however large a `ttlMs` a server asks for. */
@ExperimentalMcpApi
public const val MAX_CACHE_TTL_MS: Long = 24L * 60 * 60 * 1000

/**
 * A [ResponseCacheStore] holding entries in memory for the lifetime of this instance.
 *
 * Unbounded, holding one entry per method-and-params combination the client has issued.
 */
@ExperimentalMcpApi
public class InMemoryResponseCacheStore : ResponseCacheStore {
    private val entries = atomic(persistentMapOf<CacheKey, CacheEntry>())

    override suspend fun get(key: CacheKey): CacheEntry? = entries.value[key]

    override suspend fun put(key: CacheKey, entry: CacheEntry) {
        entries.update { it.putting(key, entry) }
    }

    override suspend fun invalidate(method: String) {
        entries.update { current -> current.removeAll { key -> key.method == method } }
    }

    override suspend fun clear() {
        entries.update { it.cleared() }
    }
}

/** The map without every key [predicate] accepts. */
@OptIn(ExperimentalMcpApi::class)
private fun PersistentMap<CacheKey, CacheEntry>.removeAll(
    predicate: (CacheKey) -> Boolean,
): PersistentMap<CacheKey, CacheEntry> = keys.filter(predicate).fold(this) { map, key -> map.removing(key) }

/** The methods whose cached results each notification stales. */
@ExperimentalMcpApi
internal val CACHE_INVALIDATING_NOTIFICATIONS: Map<String, List<String>> = mapOf(
    Method.Defined.NotificationsToolsListChanged.value to listOf(Method.Defined.ToolsList.value),
    Method.Defined.NotificationsPromptsListChanged.value to listOf(Method.Defined.PromptsList.value),
    Method.Defined.NotificationsResourcesListChanged.value to listOf(
        Method.Defined.ResourcesList.value,
        Method.Defined.ResourcesTemplatesList.value,
    ),
    Method.Defined.NotificationsResourcesUpdated.value to listOf(Method.Defined.ResourcesRead.value),
)

/**
 * The instant [result] goes stale, measured from [now].
 *
 * A `ttlMs` longer than [MAX_CACHE_TTL_MS] is clamped rather than refused.
 */
@OptIn(ExperimentalTime::class)
@ExperimentalMcpApi
internal fun CacheableResult.expiryFrom(now: Instant = Clock.System.now()): Instant =
    now + ttlMs.coerceAtMost(MAX_CACHE_TTL_MS).milliseconds
