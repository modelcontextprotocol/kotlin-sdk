// This file was automatically generated from README.md by Knit tool. Do not edit.
package io.modelcontextprotocol.kotlin.sdk.examples.exampleServerResources01

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents

fun main() {

val server = Server(
    serverInfo = Implementation(
        name = "example-server",
        version = "1.0.0"
    ),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
        ),
    )
)

server.addResource(
    uri = "note://release/latest",
    name = "Release notes",
    description = "Last deployment summary",
    mimeType = "text/markdown",
) { request ->
    ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                text = "Ship 42 reached production successfully.",
                uri = request.uri,
                mimeType = "text/markdown",
            ),
        ),
    )
}
}
