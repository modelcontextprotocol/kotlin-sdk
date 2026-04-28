package org.kotlinlang.mcp.config

import io.ktor.server.config.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerConfigTest {

    @Test
    fun `toServerConfig maps all Algolia properties`() {
        val config = MapApplicationConfig(
            "algolia.appId" to "test-app-id",
            "algolia.apiKey" to "test-api-key",
            "algolia.indexName" to "test-index",
        )

        val serverConfig = config.toServerConfig()

        assertEquals("test-app-id", serverConfig.algoliaAppId)
        assertEquals("test-api-key", serverConfig.algoliaApiKey)
        assertEquals("test-index", serverConfig.algoliaIndexName)
    }

    @Test
    fun `toServerConfig throws when required property is missing`() {
        val config = MapApplicationConfig(
            "algolia.appId" to "test-app-id",
        )

        assertFailsWith<ApplicationConfigurationException> {
            config.toServerConfig()
        }
    }
}
