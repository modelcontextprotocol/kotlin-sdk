package io.modelcontextprotocol.sample.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

fun main(vararg args: String) {
    val authEnabled = args.any { it == "--auth" }
    val port = args.firstOrNull { it != "--auth" }?.toIntOrNull() ?: 3001
    val authToken = if (authEnabled) {
        val envToken = System.getenv("MCP_AUTH_TOKEN")
        requireNotNull(envToken) {
            "MCP_AUTH_TOKEN environment variable must be set when using --auth"
        }
        envToken
    } else {
        null
    }

    println("Starting MCP Streamable HTTP server on port $port")
    println("Use MCP inspector to connect to http://localhost:$port/mcp")
    if (authToken != null) {
        println("Bearer auth enabled (token sourced from MCP_AUTH_TOKEN)")
    }

    embeddedServer(CIO, host = "127.0.0.1", port = port) {
        configureServer(authToken)
    }.start(wait = true)
}
