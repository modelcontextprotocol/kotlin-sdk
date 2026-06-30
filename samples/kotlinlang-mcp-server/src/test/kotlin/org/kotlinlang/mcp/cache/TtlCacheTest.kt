package org.kotlinlang.mcp.cache

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class TtlCacheTest {

    @Test
    fun `get returns value that was put`() {
        val cache = TtlCache<String, String>(ttl = 10.minutes)
        cache.put("key", "value")
        assertEquals("value", cache.get("key"))
    }

    @Test
    fun `get returns null for missing key`() {
        val cache = TtlCache<String, String>(ttl = 10.minutes)
        assertNull(cache.get("missing"))
    }

    @Test
    fun `get returns null after TTL expires`() {
        val timeSource = TestTimeSource()
        val cache = TtlCache<String, String>(ttl = 10.minutes, timeSource = timeSource)

        cache.put("key", "value")
        assertEquals("value", cache.get("key"))

        timeSource += 11.minutes
        assertNull(cache.get("key"))
    }

    @Test
    fun `get returns value just before TTL expires`() {
        val timeSource = TestTimeSource()
        val cache = TtlCache<String, String>(ttl = 10.minutes, timeSource = timeSource)

        cache.put("key", "value")
        timeSource += 9.minutes
        assertEquals("value", cache.get("key"))
    }

    @Test
    fun `put overwrites value and resets TTL`() {
        val timeSource = TestTimeSource()
        val cache = TtlCache<String, String>(ttl = 10.minutes, timeSource = timeSource)

        cache.put("key", "old")
        timeSource += 8.minutes

        cache.put("key", "new")
        timeSource += 8.minutes

        assertEquals("new", cache.get("key"))
    }

    @Test
    fun `getOrPut returns cached value without calling loader`() = runTest {
        val cache = TtlCache<String, String>(ttl = 10.minutes)
        cache.put("key", "cached")

        var loaderCalled = false
        val result = cache.getOrPut("key") {
            loaderCalled = true
            "loaded"
        }

        assertEquals("cached", result)
        assertEquals(false, loaderCalled)
    }

    @Test
    fun `getOrPut calls loader on cache miss and caches result`() = runTest {
        val cache = TtlCache<String, String>(ttl = 10.minutes)

        val result = cache.getOrPut("key") { "loaded" }

        assertEquals("loaded", result)
        assertEquals("loaded", cache.get("key"))
    }

    @Test
    fun `getOrPut calls loader again after TTL expires`() = runTest {
        val timeSource = TestTimeSource()
        val cache = TtlCache<String, String>(ttl = 10.minutes, timeSource = timeSource)

        cache.getOrPut("key") { "first" }
        timeSource += 11.minutes

        val result = cache.getOrPut("key") { "second" }
        assertEquals("second", result)
    }

    @Test
    fun `put evicts expired entries`() {
        val timeSource = TestTimeSource()
        val cache = TtlCache<String, String>(ttl = 10.minutes, timeSource = timeSource)

        cache.put("a", "1")
        cache.put("b", "2")
        timeSource += 11.minutes

        // Both entries are expired but still in memory.
        // A new put triggers eviction of expired entries.
        cache.put("c", "3")

        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals("3", cache.get("c"))
    }

    @Test
    fun `put does not evict non-expired entries`() {
        val timeSource = TestTimeSource()
        val cache = TtlCache<String, String>(ttl = 10.minutes, timeSource = timeSource)

        cache.put("a", "1")
        timeSource += 5.minutes
        cache.put("b", "2")

        assertEquals("1", cache.get("a"))
        assertEquals("2", cache.get("b"))
    }

    @Test
    fun `concurrent put and get do not throw`() = runTest {
        val cache = TtlCache<Int, String>(ttl = 10.minutes)
        val jobs = (1..100).map { i ->
            launch {
                cache.put(i, "value-$i")
                cache.get(i)
                cache.put(i, "updated-$i")
                cache.get(i)
            }
        }
        jobs.joinAll()
    }
}
