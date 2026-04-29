package org.kotlinlang.mcp.content

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

internal class PageFetcher(private val httpClient: HttpClient) {
    suspend fun fetch(url: String): String {
        return httpClient.get(url).bodyAsText()
    }
}
