package org.kotlinlang.mcp.tools

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.kotlinlang.mcp.algolia.AlgoliaClient
import org.kotlinlang.mcp.cache.TtlCache
import org.kotlinlang.mcp.config.ServerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class SearchKotlinlangTest {

    private val testConfig = ServerConfig(
        algoliaAppId = "test-app-id",
        algoliaApiKey = "test-api-key",
        algoliaIndexName = "test-index",
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun createSearchTool(
        mockEngine: MockEngine,
        timeSource: TestTimeSource = TestTimeSource(),
    ): SearchKotlinlang {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val algoliaClient = AlgoliaClient(testConfig, httpClient)
        val cache = TtlCache<String, String>(ttl = 10.minutes, timeSource = timeSource)
        return SearchKotlinlang(algoliaClient, cache)
    }

    private fun mockEngineWithHits(hitsJson: String): MockEngine = MockEngine {
        respond(
            content = """{"hits": [$hitsJson]}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    private fun hit(
        objectID: String = "id1",
        mainTitle: String? = "Title",
        url: String = "/docs/test.html",
        snippet: String? = "some text",
        matchLevel: String = "full",
    ): String = buildString {
        append("""{"objectID":"$objectID","url":"$url"""")
        if (mainTitle != null) append(""","mainTitle":"$mainTitle"""")
        if (snippet != null) {
            append(""","_snippetResult":{"content":{"value":"$snippet","matchLevel":"$matchLevel"}}""")
        }
        append("}")
    }

    @Test
    fun `filters out non-docs hits`() = runTest {
        val engine = mockEngineWithHits(
            listOf(
                hit(objectID = "1", url = "/docs/coroutines.html", mainTitle = "Coroutines"),
                hit(objectID = "2", url = "https://kotlinlang.org/api/core/kotlin/", mainTitle = "API"),
                hit(objectID = "3", url = "/docs/flow.html", mainTitle = "Flow"),
            ).joinToString(",")
        )
        val tool = createSearchTool(engine)

        val result = tool.handle("coroutines")
        val text = result.content.first().let { (it as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text }

        assertTrue(text.contains("Coroutines"))
        assertTrue(text.contains("Flow"))
        assertTrue(!text.contains("API"))
    }

    @Test
    fun `takes only top 5 after filtering`() = runTest {
        val hits = (1..10).map { i ->
            hit(objectID = "id$i", url = "/docs/page$i.html", mainTitle = "Page $i", snippet = "text $i")
        }
        val engine = mockEngineWithHits(hits.joinToString(","))
        val tool = createSearchTool(engine)

        val result = tool.handle("kotlin")
        val text = result.content.first().let { (it as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text }

        for (i in 1..5) assertTrue(text.contains("Page $i"), "Should contain Page $i")
        for (i in 6..10) assertTrue(!text.contains("Page $i"), "Should not contain Page $i")
    }

    @Test
    fun `formats results with title path and snippet`() = runTest {
        val engine = mockEngineWithHits(
            hit(
                objectID = "1",
                url = "/docs/coroutines-overview.html",
                mainTitle = "Coroutines overview",
                snippet = "Kotlin provides coroutines support",
            )
        )
        val tool = createSearchTool(engine)

        val result = tool.handle("coroutines")
        val text = result.content.first().let { (it as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text }

        assertEquals(
            """
            1. Coroutines overview [coroutines-overview]
               Kotlin provides coroutines support
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `strips HTML tags from snippet`() = runTest {
        val engine = mockEngineWithHits(
            hit(snippet = "<em>coroutines</em> are <strong>great</strong>")
        )
        val tool = createSearchTool(engine)

        val result = tool.handle("coroutines")
        val text = result.content.first().let { (it as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text }

        assertTrue(text.contains("coroutines are great"))
        assertTrue(!text.contains("<em>"))
        assertTrue(!text.contains("<strong>"))
    }

    @Test
    fun `uses Untitled when mainTitle is null`() = runTest {
        val engine = mockEngineWithHits(
            hit(mainTitle = null, url = "/docs/basics.html", snippet = "some text")
        )
        val tool = createSearchTool(engine)

        val result = tool.handle("basics")
        val text = result.content.first().let { (it as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text }

        assertTrue(text.contains("Untitled [basics]"))
    }

    @Test
    fun `omits snippet line when snippetResult is null`() = runTest {
        val engine = mockEngineWithHits(
            hit(url = "/docs/basics.html", mainTitle = "Basics", snippet = null)
        )
        val tool = createSearchTool(engine)

        val result = tool.handle("basics")
        val text = result.content.first().let { (it as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text }

        assertEquals("1. Basics [basics]", text)
    }

    @Test
    fun `returns no results message when all hits are filtered out`() = runTest {
        val engine = mockEngineWithHits(
            hit(url = "https://kotlinlang.org/api/core/kotlin/", mainTitle = "API")
        )
        val tool = createSearchTool(engine)

        val result = tool.handle("something")
        val text = result.content.first().let { (it as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text }

        assertNull(result.isError)
        assertEquals("No results found for: something", text)
    }

    @Test
    fun `returns isError true when Algolia fails`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message": "Internal server error"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val tool = createSearchTool(engine)

        val result = tool.handle("coroutines")

        assertEquals(true, result.isError)
        val text = result.content.first().let { (it as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text }
        assertTrue(text.startsWith("Search failed:"))
    }

    @Test
    fun `caches result and does not call Algolia on second request`() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            respond(
                content = """{"hits": [${hit(url = "/docs/test.html")}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val tool = createSearchTool(engine)

        tool.handle("coroutines")
        tool.handle("coroutines")

        assertEquals(1, callCount)
    }

    @Test
    fun `extracts path from nested URL`() = runTest {
        val engine = mockEngineWithHits(
            hit(
                url = "/docs/multiplatform/compose.html",
                mainTitle = "Compose",
                snippet = "text",
            )
        )
        val tool = createSearchTool(engine)

        val result = tool.handle("compose")
        val text = result.content.first().let { (it as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text }

        assertTrue(text.contains("[multiplatform/compose]"))
    }
}
