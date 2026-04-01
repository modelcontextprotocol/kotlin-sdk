package io.modelcontextprotocol.kotlin.sdk.conformance

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

fun main() {
    val port = System.getenv("MCP_PORT")?.toIntOrNull() ?: 3001
    embeddedServer(CIO, port = port) {
        mcpStreamableHttp(
            enableDnsRebindingProtection = true,
            allowedHosts = listOf("localhost", "127.0.0.1", "localhost:$port", "127.0.0.1:$port"),
            eventStore = InMemoryEventStore(),
        ) {
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
            completions = ServerCapabilities.Completions,
        ),
    ),
) {
    registerConformanceTools()
    registerConformanceResources()
    registerConformancePrompts()
    registerConformanceCompletions()
}
