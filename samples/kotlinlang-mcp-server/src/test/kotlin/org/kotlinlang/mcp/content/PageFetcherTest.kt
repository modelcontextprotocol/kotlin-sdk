package org.kotlinlang.mcp.content

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PageFetcherTest {

    private fun createFetcher(mockEngine: MockEngine): PageFetcher {
        val httpClient = HttpClient(mockEngine) {
            expectSuccess = true
        }
        return PageFetcher(httpClient)
    }

    @Test
    fun `fetch sends GET request to provided URL`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("https://kotlinlang.org/docs/_llms/coroutines-overview.txt", request.url.toString())
            assertEquals(HttpMethod.Get, request.method)

            respond(
                content = "# Coroutines overview",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }

        val fetcher = createFetcher(mockEngine)
        fetcher.fetch("https://kotlinlang.org/docs/_llms/coroutines-overview.txt")
    }

    @Test
    fun `fetch returns response body as text`() = runTest {
        val pageContent = "# Coroutines\n\nKotlin provides coroutines support at the language level."

        val mockEngine = MockEngine {
            respond(
                content = pageContent,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }

        val fetcher = createFetcher(mockEngine)
        val result = fetcher.fetch("https://kotlinlang.org/docs/_llms/coroutines-overview.txt")

        assertEquals(pageContent, result)
    }

    @Test
    fun `fetch throws ClientRequestException on 404`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Not Found",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }

        val fetcher = createFetcher(mockEngine)
        assertFailsWith<ClientRequestException> {
            fetcher.fetch("https://kotlinlang.org/docs/_llms/nonexistent.txt")
        }
    }

    @Test
    fun `fetch throws ServerResponseException on 5xx`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }

        val fetcher = createFetcher(mockEngine)
        assertFailsWith<ServerResponseException> {
            fetcher.fetch("https://kotlinlang.org/docs/_llms/coroutines-overview.txt")
        }
    }
}
