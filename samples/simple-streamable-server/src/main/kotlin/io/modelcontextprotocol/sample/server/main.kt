package io.modelcontextprotocol.sample.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

fun main(vararg args: String) {
    val authEnabled = args.any { it == "--auth" }
    val port = args.firstOrNull { it != "--auth" }?.toIntOrNull() ?: 3001
    val authToken = if (authEnabled) {
        System.getenv("MCP_AUTH_TOKEN") ?: "demo-token-12345"
    } else {
        null
    }

    println("Starting MCP Streamable HTTP server on port $port")
    println("Use MCP inspector to connect to http://localhost:$port/mcp")
    if (authToken != null) {
        println("Bearer auth enabled — use Authorization: Bearer $authToken")
    }

    embeddedServer(CIO, host = "127.0.0.1", port = port) {
        configureServer(authToken)
    }.start(wait = true)
}
