package org.kotlinlang.mcp

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*
import org.kotlinlang.mcp.algolia.AlgoliaClient
import org.kotlinlang.mcp.cache.TtlCache
import org.kotlinlang.mcp.config.ServerConfig
import org.kotlinlang.mcp.content.PageFetcher
import org.kotlinlang.mcp.tools.GetKotlinlangPage
import org.kotlinlang.mcp.tools.SearchKotlinlang
import org.slf4j.LoggerFactory
import java.io.Closeable
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

internal class KotlinlangServer(config: ServerConfig) : Closeable {

    private val logger = LoggerFactory.getLogger(KotlinlangServer::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 10_000 }
        expectSuccess = true
    }

    private val searchCache = TtlCache<String, String>(10.minutes)
    private val pageCache = TtlCache<String, String>(1.hours)

    private val algoliaClient = AlgoliaClient(config, httpClient)
    private val pageFetcher = PageFetcher(httpClient)
    private val searchTool = SearchKotlinlang(algoliaClient, searchCache)
    private val pageTool = GetKotlinlangPage(pageFetcher, pageCache)

    val server: Server = Server(
        serverInfo = Implementation(name = "kotlinlang-mcp-server", version = "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    ) {
        addTool(
            name = "search_kotlinlang",
            description = "Search across Kotlin documentation on kotlinlang.org to find relevant pages, " +
                "code examples, API references, and guides. Use this tool when you need to answer questions " +
                "about Kotlin language features, standard library, coroutines, multiplatform, tooling, or any " +
                "other topic covered in the official Kotlin documentation. Returns up to 5 results with page " +
                "titles, paths, and text snippets. To get the full content of a specific page, use the " +
                "get_kotlinlang_page tool with the page path from the search results.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put(
                            "description",
                            "A search query to find relevant Kotlin documentation pages " +
                                "(e.g. 'coroutines', 'sealed classes', 'multiplatform setup')",
                        )
                    }
                },
                required = listOf("query"),
            ),
            toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
        ) { request ->
            val query = request.arguments?.get("query")?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Missing required argument: query")),
                    isError = true,
                )
            logger.info("search_kotlinlang query=\"{}\"", query)
            searchTool.handle(query)
        }

        addTool(
            name = "get_kotlinlang_page",
            description = "Retrieve the full content of a specific Kotlin documentation page from " +
                "kotlinlang.org in md format. Use this tool when you already know the " +
                "page path (e.g., from search results) and need the complete content of that page rather " +
                "than just a snippet. If the page is not found, use search_kotlinlang to discover the " +
                "correct path.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("path") {
                        put("type", "string")
                        put(
                            "description",
                            "Page path relative to /docs/, without extension. Use the page paths " +
                                "returned from search_kotlinlang results " +
                                "(e.g. 'coroutines-overview', " +
                                "'multiplatform/compose-multiplatform-and-jetpack-compose')",
                        )
                    }
                },
                required = listOf("path"),
            ),
            toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
        ) { request ->
            val path = request.arguments?.get("path")?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Missing required argument: path")),
                    isError = true,
                )
            logger.info("get_kotlinlang_page path=\"{}\"", path)
            pageTool.handle(path)
        }
    }

    override fun close() {
        httpClient.close()
    }
}
