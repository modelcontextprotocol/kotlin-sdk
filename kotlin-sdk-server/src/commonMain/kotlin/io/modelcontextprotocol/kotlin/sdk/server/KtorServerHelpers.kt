package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson

private val logger = KotlinLogging.logger {}

/**
 * Installs [ContentNegotiation] configured with [McpJson].
 *
 * MCP requires specific JSON settings (`explicitNulls = false`, `encodeDefaults = true`,
 * `classDiscriminatorMode = NONE`). Using the default `json()` instead of `json(McpJson)`
 * causes serialization failures at runtime (e.g., explicit null fields in JSON-RPC responses).
 *
 * If [ContentNegotiation] was already installed by user code, a warning is logged because
 * Ktor's public API does not expose the Json configuration for verification.
 */
internal fun Application.installMcpContentNegotiation() {
    if (pluginOrNull(ContentNegotiation) != null) {
        // ContentNegotiation was already installed by user code.
        logger.warn {
            "ContentNegotiation plugin is already installed with an unknown JSON configuration. " +
                "MCP requires json(McpJson) for correct JSON-RPC serialization " +
                "(explicitNulls = false, encodeDefaults = true). " +
                "Remove your install(ContentNegotiation) { … } block and let " +
                "mcp() / mcpStreamableHttp() / mcpStatelessStreamableHttp() configure it automatically."
        }
    } else {
        install(ContentNegotiation) {
            json(McpJson)
        }
    }
}
