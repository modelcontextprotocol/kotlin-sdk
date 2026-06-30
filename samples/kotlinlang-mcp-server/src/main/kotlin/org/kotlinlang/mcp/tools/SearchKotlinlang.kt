package org.kotlinlang.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import org.kotlinlang.mcp.algolia.AlgoliaClient
import org.kotlinlang.mcp.algolia.AlgoliaHit
import org.kotlinlang.mcp.cache.TtlCache
import org.slf4j.LoggerFactory

internal class SearchKotlinlang(
    private val algoliaClient: AlgoliaClient,
    private val cache: TtlCache<String, String>,
) {
    private val logger = LoggerFactory.getLogger(SearchKotlinlang::class.java)

    companion object {
        private const val MAX_RESULTS = 5
        private val HTML_TAG_REGEX = Regex("<[^>]+>")
    }

    suspend fun handle(query: String): CallToolResult {
        return try {
            val text = cache.getOrPut(query) {
                val response = algoliaClient.search(query)
                val filtered = response.hits
                    .filter { it.url.startsWith("/docs/") }
                    .take(MAX_RESULTS)
                if (filtered.isEmpty()) {
                    "No results found for: $query"
                } else {
                    formatResults(filtered)
                }
            }
            CallToolResult(content = listOf(TextContent(text = text)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Search failed for query=\"{}\"", query, e)
            CallToolResult(
                content = listOf(TextContent(text = "Search failed: ${e.message ?: "unknown error"}")),
                isError = true,
            )
        }
    }

    private fun formatResults(hits: List<AlgoliaHit>): String =
        hits.mapIndexed { index, hit ->
            val title = hit.mainTitle ?: "Untitled"
            val path = extractPath(hit.url)
            val snippetLine = hit.snippetResult?.content?.value
                ?.let { "\n   ${stripHtml(it)}" }
                ?: ""
            "${index + 1}. $title [$path]$snippetLine"
        }.joinToString("\n\n")

    private fun extractPath(url: String): String =
        url.removePrefix("/docs/").removeSuffix(".html")

    private fun stripHtml(text: String): String =
        text.replace(HTML_TAG_REGEX, "")
}
