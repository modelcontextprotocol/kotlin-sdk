package io.modelcontextprotocol.kotlin.sdk.conformance

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

fun main() {
    val port = System.getenv("MCP_PORT")?.toIntOrNull() ?: 4001
    embeddedServer(CIO, port = port) {
        install(ContentNegotiation) {
            json(McpJson)
        }
        mcpStreamableHttp {
            createConformanceServer()
        }
    }.start(wait = true)
}

fun createConformanceServer(): Server = Server(
    serverInfo = Implementation("mcp-kotlin-sdk-conformance", "0.1.0"),
    options = ServerOptions(
        ServerCapabilities(
            tools = ServerCapabilities.Tools(listChanged = true),
            resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
            prompts = ServerCapabilities.Prompts(listChanged = true),
            logging = ServerCapabilities.Logging,
        ),
    ),
) {
    registerConformanceTools()
    registerConformanceResources()
    registerConformancePrompts()
}
