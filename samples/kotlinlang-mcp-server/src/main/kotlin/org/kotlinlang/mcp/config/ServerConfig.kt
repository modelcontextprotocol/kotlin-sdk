package org.kotlinlang.mcp.config

import io.ktor.server.config.*

internal data class ServerConfig(
    val algoliaAppId: String,
    val algoliaApiKey: String,
    val algoliaIndexName: String,
) {
    override fun toString(): String =
        "ServerConfig(algoliaAppId=$algoliaAppId, algoliaIndexName=$algoliaIndexName)"
}

internal fun ApplicationConfig.toServerConfig(): ServerConfig = ServerConfig(
    algoliaAppId = property("algolia.appId").getString(),
    algoliaApiKey = property("algolia.apiKey").getString(),
    algoliaIndexName = property("algolia.indexName").getString(),
)
