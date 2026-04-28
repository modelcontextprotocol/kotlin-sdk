package org.kotlinlang.mcp.tools

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import org.kotlinlang.mcp.cache.TtlCache
import org.kotlinlang.mcp.content.PageFetcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class GetKotlinlangPageTest {

    private fun createTool(mockEngine: MockEngine): GetKotlinlangPage {
        val httpClient = HttpClient(mockEngine) {
            expectSuccess = true
        }
        val pageFetcher = PageFetcher(httpClient)
        val cache = TtlCache<String, String>(ttl = 1.hours)
        return GetKotlinlangPage(pageFetcher, cache)
    }

    @Test
    fun `handle returns page content for valid path`() = runTest {
        val pageContent = "# Coroutines\n\nKotlin provides coroutines support."
        val mockEngine = MockEngine { request ->
            assertEquals(
                "https://kotlinlang.org/docs/_llms/coroutines-overview.txt",
                request.url.toString(),
            )
            respond(
                content = pageContent,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        val tool = createTool(mockEngine)

        val result = tool.handle("coroutines-overview")
        val text = (result.content.first() as TextContent).text

        assertNull(result.isError)
        assertEquals(pageContent, text)
    }

    @Test
    fun `handle returns cached content on second call`() = runTest {
        var callCount = 0
        val mockEngine = MockEngine {
            callCount++
            respond(
                content = "# Page content",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        val tool = createTool(mockEngine)

        val result1 = tool.handle("coroutines-overview")
        val result2 = tool.handle("coroutines-overview")

        assertEquals(1, callCount)
        assertEquals(
            (result1.content.first() as TextContent).text,
            (result2.content.first() as TextContent).text,
        )
    }

    @Test
    fun `handle returns error with hint on 404`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Not Found",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        val tool = createTool(mockEngine)

        val result = tool.handle("nonexistent-page")
        val text = (result.content.first() as TextContent).text

        assertEquals(true, result.isError)
        assertTrue(text.contains("Page not found: nonexistent-page"))
        assertTrue(text.contains("search_kotlinlang"))
    }

    @Test
    fun `handle returns error on network failure`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        val tool = createTool(mockEngine)

        val result = tool.handle("coroutines-overview")

        assertEquals(true, result.isError)
        val text = (result.content.first() as TextContent).text
        assertTrue(text.startsWith("Failed to fetch page:"))
    }

    @Test
    fun `handle returns error on empty path`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "should not reach",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        val tool = createTool(mockEngine)

        val result = tool.handle("")

        assertEquals(true, result.isError)
        val text = (result.content.first() as TextContent).text
        assertTrue(text.contains("Invalid path:"))
    }

    @Test
    fun `handle rethrows CancellationException`() = runTest {
        val mockEngine = MockEngine {
            throw kotlinx.coroutines.CancellationException("cancelled")
        }
        val tool = createTool(mockEngine)

        kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
            tool.handle("coroutines-overview")
        }
    }
}
