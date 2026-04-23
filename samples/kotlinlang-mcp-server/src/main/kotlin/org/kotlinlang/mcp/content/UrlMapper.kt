package org.kotlinlang.mcp.content

private const val BASE_URL = "https://kotlinlang.org/docs"

internal fun normalizePath(path: String): String = path.trim().trim('/').removeSuffix(".html")

internal fun mapPathToUrl(path: String): String {
    val normalized = normalizePath(path)
    require(normalized.isNotBlank()) { "Path must not be empty" }
    require(".." !in normalized.split('/')) { "Path must not contain '..' segments" }

    val lastSlash = normalized.lastIndexOf('/')
    return if (lastSlash == -1) {
        "$BASE_URL/_llms/$normalized.txt"
    } else {
        val dir = normalized.substring(0, lastSlash)
        val file = normalized.substring(lastSlash + 1)
        "$BASE_URL/$dir/_llms/$file.txt"
    }
}
