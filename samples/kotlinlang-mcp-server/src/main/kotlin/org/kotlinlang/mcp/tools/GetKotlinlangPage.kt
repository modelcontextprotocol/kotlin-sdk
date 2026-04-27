package org.kotlinlang.mcp.tools

import io.ktor.client.plugins.*
import io.ktor.http.*
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import org.kotlinlang.mcp.cache.TtlCache
import org.kotlinlang.mcp.content.PageFetcher
import org.kotlinlang.mcp.content.mapPathToUrl
import org.kotlinlang.mcp.content.normalizePath
import org.slf4j.LoggerFactory

internal class GetKotlinlangPage(
    private val pageFetcher: PageFetcher,
    private val cache: TtlCache<String, String>,
) {
    private val logger = LoggerFactory.getLogger(GetKotlinlangPage::class.java)
    suspend fun handle(path: String): CallToolResult {
        val normalizedPath = normalizePath(path)
        return try {
            val content = cache.getOrPut(normalizedPath) {
                val url = mapPathToUrl(normalizedPath)
                pageFetcher.fetch(url)
            }
            CallToolResult(content = listOf(TextContent(text = content)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            CallToolResult(
                content = listOf(TextContent(text = "Invalid path: ${e.message}")),
                isError = true,
            )
        } catch (e: ClientRequestException) {
            if (e.response.status != HttpStatusCode.NotFound) {
                logger.error("Failed to fetch page path=\"{}\"", path, e)
            }
            val message = if (e.response.status == HttpStatusCode.NotFound) {
                "Page not found: $path. Use search_kotlinlang to find the correct page path."
            } else {
                "Failed to fetch page: ${e.message}"
            }
            CallToolResult(
                content = listOf(TextContent(text = message)),
                isError = true,
            )
        } catch (e: Exception) {
            logger.error("Failed to fetch page path=\"{}\"", path, e)
            CallToolResult(
                content = listOf(TextContent(text = "Failed to fetch page: ${e.message ?: "unknown error"}")),
                isError = true,
            )
        }
    }
}
