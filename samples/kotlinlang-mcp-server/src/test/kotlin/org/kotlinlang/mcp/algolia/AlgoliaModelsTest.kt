package org.kotlinlang.mcp.algolia

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AlgoliaModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serializes search request to JSON`() {
        val request = AlgoliaSearchRequest(
            query = "coroutines",
            hitsPerPage = 15,
            attributesToRetrieve = listOf("mainTitle", "url", "headings"),
            attributesToSnippet = listOf("content:40"),
        )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<AlgoliaSearchRequest>(encoded)

        assertEquals(request, decoded)
    }

    @Test
    fun `parses full response with nested snippet`() {
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
                                "value": "Kotlin provides <em>coroutines</em> support",
                                "matchLevel": "full"
                            }
                        }
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<AlgoliaSearchResponse>(responseJson)

        assertEquals(1, response.hits.size)
        val hit = response.hits[0]
        assertEquals("abc123", hit.objectID)
        assertEquals("Coroutines overview", hit.mainTitle)
        assertEquals("/docs/coroutines-overview.html", hit.url)
        assertEquals("Introduction | First coroutine", hit.headings)
        assertEquals("Kotlin provides <em>coroutines</em> support", hit.snippetResult?.content?.value)
        assertEquals("full", hit.snippetResult?.content?.matchLevel)
    }

    @Test
    fun `parses hit with missing nullable fields`() {
        val responseJson = """
            {
                "hits": [
                    {
                        "objectID": "def456",
                        "url": "/docs/basic-syntax.html"
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<AlgoliaSearchResponse>(responseJson)

        val hit = response.hits[0]
        assertEquals("def456", hit.objectID)
        assertEquals("/docs/basic-syntax.html", hit.url)
        assertEquals(null, hit.mainTitle)
        assertEquals(null, hit.headings)
        assertEquals(null, hit.snippetResult)
    }

    @Test
    fun `parses response ignoring unknown fields`() {
        val responseJson = """
            {
                "hits": [
                    {
                        "objectID": "ghi789",
                        "url": "/docs/functions.html",
                        "unknownField": 42,
                        "_snippetResult": {
                            "content": {
                                "value": "snippet text",
                                "matchLevel": "none",
                                "fullyHighlighted": false
                            },
                            "anotherUnknown": "ignored"
                        }
                    }
                ],
                "nbHits": 100,
                "page": 0,
                "processingTimeMS": 5
            }
        """.trimIndent()

        val response = json.decodeFromString<AlgoliaSearchResponse>(responseJson)

        assertEquals(1, response.hits.size)
        assertEquals("ghi789", response.hits[0].objectID)
        assertEquals("snippet text", response.hits[0].snippetResult?.content?.value)
    }

    @Test
    fun `parses response with empty hits list`() {
        val responseJson = """
            {
                "hits": []
            }
        """.trimIndent()

        val response = json.decodeFromString<AlgoliaSearchResponse>(responseJson)

        assertEquals(emptyList(), response.hits)
    }
}
