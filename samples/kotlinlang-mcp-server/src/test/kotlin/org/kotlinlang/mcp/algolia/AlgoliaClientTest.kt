package org.kotlinlang.mcp.algolia

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.kotlinlang.mcp.config.ServerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AlgoliaClientTest {

    private val testConfig = ServerConfig(
        algoliaAppId = "test-app-id",
        algoliaApiKey = "test-api-key",
        algoliaIndexName = "test-index",
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun createClient(mockEngine: MockEngine): AlgoliaClient {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
            expectSuccess = true
        }
        return AlgoliaClient(testConfig, httpClient)
    }

    @Test
    fun `search sends correct URL and headers`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals(
                "https://test-app-id-dsn.algolia.net/1/indexes/test-index/query",
                request.url.toString(),
            )
            assertEquals("test-app-id", request.headers["x-algolia-application-id"])
            assertEquals("test-api-key", request.headers["x-algolia-api-key"])
            assertEquals(HttpMethod.Post, request.method)

            respond(
                content = """{"hits": []}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        client.search("coroutines")
    }

    @Test
    fun `search sends correct request body`() = runTest {
        val mockEngine = MockEngine { request ->
            val body = json.decodeFromString<AlgoliaSearchRequest>(request.body.toByteArray().decodeToString())
            assertEquals("coroutines", body.query)
            assertEquals(15, body.hitsPerPage)
            assertEquals(listOf("objectID", "mainTitle", "url", "headings"), body.attributesToRetrieve)
            assertEquals(listOf("content:40"), body.attributesToSnippet)

            respond(
                content = """{"hits": []}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        client.search("coroutines")
    }

    @Test
    fun `search parses response with hits`() = runTest {
        val responseJson = """
            {
                "hits": [
                    {
                        "objectID": "abc123",
                        "mainTitle": "Coroutines overview",
                        "url": "/docs/coroutines-overview.html",
                        "headings": "Introduction | First coroutine",
                        "_snippetResult": {
                            "content": {
                                "value": "Kotlin provides coroutines support",
                                "matchLevel": "full"
                            }
                        }
                    }
                ]
            }
        """.trimIndent()

        val mockEngine = MockEngine {
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        val result = client.search("coroutines")

        assertEquals(1, result.hits.size)
        val hit = result.hits[0]
        assertEquals("abc123", hit.objectID)
        assertEquals("Coroutines overview", hit.mainTitle)
        assertEquals("/docs/coroutines-overview.html", hit.url)
        assertEquals("Introduction | First coroutine", hit.headings)
        assertEquals("Kotlin provides coroutines support", hit.snippetResult?.content?.value)
        assertEquals("full", hit.snippetResult?.content?.matchLevel)
    }

    @Test
    fun `search returns empty list when no hits`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"hits": []}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        val result = client.search("nonexistent")

        assertTrue(result.hits.isEmpty())
    }

    @Test
    fun `search throws on 4xx error`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"message": "Invalid Application-ID or API key"}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        assertFailsWith<ClientRequestException> {
            client.search("coroutines")
        }
    }

    @Test
    fun `search throws on 5xx error`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"message": "Internal server error"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        assertFailsWith<ServerResponseException> {
            client.search("coroutines")
        }
    }
}
