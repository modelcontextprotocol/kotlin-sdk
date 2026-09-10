package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@OptIn(ExperimentalMcpApi::class)
class CacheableResultTest {

    private fun cacheableResults(ttlMs: Long = 0, cacheScope: CacheScope = CacheScope.Private) =
        listOf<CacheableResult>(
            DiscoverResult(
                supportedVersions = MODERN_PROTOCOL_VERSIONS,
                capabilities = ServerCapabilities(),
                ttlMs = ttlMs,
                cacheScope = cacheScope,
            ),
            ListToolsResult(tools = emptyList(), ttlMs = ttlMs, cacheScope = cacheScope),
            ListPromptsResult(prompts = emptyList(), ttlMs = ttlMs, cacheScope = cacheScope),
            ListResourcesResult(resources = emptyList(), ttlMs = ttlMs, cacheScope = cacheScope),
            ListResourceTemplatesResult(
                resourceTemplates = emptyList(),
                ttlMs = ttlMs,
                cacheScope = cacheScope,
            ),
            ReadResourceResult(contents = emptyList(), ttlMs = ttlMs, cacheScope = cacheScope),
        )

    @Test
    fun `the cacheable results should be exactly the six the spec names`() {
        cacheableResults().map { it::class.simpleName }.toSet() shouldBe setOf(
            "DiscoverResult",
            "ListToolsResult",
            "ListPromptsResult",
            "ListResourcesResult",
            "ListResourceTemplatesResult",
            "ReadResourceResult",
        )
    }

    @Test
    fun `a result that sets no hint should be immediately stale and private`() {
        // The conservative reading: a handler that says nothing must not accidentally authorize a
        // shared cache to hand its answer to another principal.
        cacheableResults().forEach { result ->
            result.ttlMs shouldBe 0
            result.cacheScope shouldBe CacheScope.Private
        }
    }

    @Test
    fun `a negative freshness window should be refused at construction`() {
        assertFailsWith<IllegalArgumentException> {
            DiscoverResult(MODERN_PROTOCOL_VERSIONS, ServerCapabilities(), ttlMs = -1)
        }
        assertFailsWith<IllegalArgumentException> { ListToolsResult(tools = emptyList(), ttlMs = -1) }
        assertFailsWith<IllegalArgumentException> { ListPromptsResult(prompts = emptyList(), ttlMs = -1) }
        assertFailsWith<IllegalArgumentException> { ListResourcesResult(resources = emptyList(), ttlMs = -1) }
        assertFailsWith<IllegalArgumentException> {
            ListResourceTemplatesResult(resourceTemplates = emptyList(), ttlMs = -1)
        }
        assertFailsWith<IllegalArgumentException> { ReadResourceResult(contents = emptyList(), ttlMs = -1) }
    }

    @Test
    fun `hints should survive a round trip`() {
        cacheableResults(ttlMs = 250, cacheScope = CacheScope.Public).forEach { result ->
            // Encoded through RequestResult: CacheableResult is a read-side marker, and results
            // reach the wire through the result hierarchy's own polymorphic serializer.
            val encoded = McpJson.encodeToString<RequestResult>(result)
            val decoded = McpJson.decodeFromString<RequestResult>(encoded)

            val cacheable = assertIs<CacheableResult>(decoded)
            cacheable.ttlMs shouldBe 250
            cacheable.cacheScope shouldBe CacheScope.Public
        }
    }

    @Test
    fun `a receiver should tolerate hints the peer left out`() {
        val terse = mapOf(
            """{"tools": []}""" to "ListToolsResult",
            """{"prompts": []}""" to "ListPromptsResult",
            """{"resources": []}""" to "ListResourcesResult",
            """{"resourceTemplates": []}""" to "ListResourceTemplatesResult",
            """{"contents": []}""" to "ReadResourceResult",
        )

        terse.forEach { (wire, expected) ->
            val decoded = McpJson.decodeFromString<ServerResult>(wire)

            decoded::class.simpleName shouldBe expected
            val cacheable = assertIs<CacheableResult>(decoded)
            cacheable.ttlMs shouldBe 0
            cacheable.cacheScope shouldBe CacheScope.Private
        }
    }
}
