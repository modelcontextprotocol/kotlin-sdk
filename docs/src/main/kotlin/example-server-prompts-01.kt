// This file was automatically generated from README.md by Knit tool. Do not edit.
package io.modelcontextprotocol.kotlin.sdk.examples.exampleServerPrompts01

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

fun main() {

val server = Server(
    serverInfo = Implementation(
        name = "example-server",
        version = "1.0.0"
    ),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            prompts = ServerCapabilities.Prompts(listChanged = true),
        ),
    )
)

server.addPrompt(
    name = "code-review",
    description = "Ask the model to review a diff",
    arguments = listOf(
        PromptArgument(name = "diff", description = "Unified diff", required = true),
    ),
) { request ->
    GetPromptResult(
        description = "Quick code review helper",
        messages = listOf(
            PromptMessage(
                role = Role.User,
                content = TextContent(text = "Review this change:\n${request.arguments?.get("diff")}"),
            ),
        ),
    )
}
}
