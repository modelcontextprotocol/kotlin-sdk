package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CacheHintsTest {

    @Test
    fun `should reject a negative ttlMs`() {
        assertFailsWith<IllegalArgumentException> { ListToolsResult(tools = emptyList(), ttlMs = -1) }
        assertFailsWith<IllegalArgumentException> { ListPromptsResult(prompts = emptyList(), ttlMs = -1) }
        assertFailsWith<IllegalArgumentException> { ListResourcesResult(resources = emptyList(), ttlMs = -1) }
        assertFailsWith<IllegalArgumentException> {
            ListResourceTemplatesResult(resourceTemplates = emptyList(), ttlMs = -1)
        }
        assertFailsWith<IllegalArgumentException> { ReadResourceResult(contents = emptyList(), ttlMs = -1) }
    }

    @Test
    fun `should decode results without cache hints as immediately stale and private`() {
        val tools = McpJson.decodeFromString<ListToolsResult>("""{"tools": []}""")
        val prompts = McpJson.decodeFromString<ListPromptsResult>("""{"prompts": []}""")
        val resources = McpJson.decodeFromString<ListResourcesResult>("""{"resources": []}""")
        val templates = McpJson.decodeFromString<ListResourceTemplatesResult>("""{"resourceTemplates": []}""")
        val read = McpJson.decodeFromString<ReadResourceResult>("""{"contents": []}""")

        listOf(
            tools.ttlMs to tools.cacheScope,
            prompts.ttlMs to prompts.cacheScope,
            resources.ttlMs to resources.cacheScope,
            templates.ttlMs to templates.cacheScope,
            read.ttlMs to read.cacheScope,
        ).forEach { it shouldBe (0L to CacheScope.Private) }
    }
}
