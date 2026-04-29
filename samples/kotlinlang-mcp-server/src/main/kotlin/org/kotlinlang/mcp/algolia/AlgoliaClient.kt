package org.kotlinlang.mcp.algolia

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.kotlinlang.mcp.config.ServerConfig

internal class AlgoliaClient(
    private val config: ServerConfig,
    private val httpClient: HttpClient,
) {
    suspend fun search(query: String): AlgoliaSearchResponse {
        val response = httpClient.post(buildSearchUrl()) {
            header("x-algolia-application-id", config.algoliaAppId)
            header("x-algolia-api-key", config.algoliaApiKey)
            contentType(ContentType.Application.Json)
            setBody(
                AlgoliaSearchRequest(
                    query = query,
                    hitsPerPage = HITS_PER_PAGE,
                    attributesToRetrieve = ATTRIBUTES_TO_RETRIEVE,
                    attributesToSnippet = ATTRIBUTES_TO_SNIPPET,
                )
            )
        }
        return response.body()
    }

    private fun buildSearchUrl(): String =
        "https://${config.algoliaAppId}-dsn.algolia.net/1/indexes/${config.algoliaIndexName}/query"

    companion object {
        private const val HITS_PER_PAGE = 15
        private val ATTRIBUTES_TO_RETRIEVE = listOf("objectID", "mainTitle", "url", "headings")
        private val ATTRIBUTES_TO_SNIPPET = listOf("content:40")
    }
}
