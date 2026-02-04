// This file was automatically generated from README.md by Knit tool. Do not edit.
package io.modelcontextprotocol.kotlin.sdk.examples.exampleServerTools01

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.jsonPrimitive

fun main() {

val server = Server(
    serverInfo = Implementation(
        name = "example-server",
        version = "1.0.0"
    ),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            tools = ServerCapabilities.Tools(listChanged = true),
        ),
    )
)

server.addTool(
    name = "echo",
    description = "Return whatever the user sent back to them",
) { request ->
    val text = request.arguments?.get("text")?.jsonPrimitive?.content ?: "(empty)"
    CallToolResult(content = listOf(TextContent(text = "Echo: $text")))
}
}
