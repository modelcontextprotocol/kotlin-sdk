package io.modelcontextprotocol.kotlin.sdk.conformance

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

fun Server.registerConformancePrompts() {
    // 1. Simple prompt
    addPrompt(
        name = "test_simple_prompt",
        description = "test_simple_prompt",
    ) {
        GetPromptResult(
            messages = listOf(
                PromptMessage(Role.User, TextContent("This is a simple prompt for testing.")),
            ),
        )
    }

    // 2. Prompt with arguments
    addPrompt(
        name = "test_prompt_with_arguments",
        description = "test_prompt_with_arguments",
        arguments = listOf(
            PromptArgument(name = "arg1", description = "First test argument", required = true),
            PromptArgument(name = "arg2", description = "Second test argument", required = true),
        ),
    ) { request ->
        val arg1 = request.arguments?.get("arg1") ?: ""
        val arg2 = request.arguments?.get("arg2") ?: ""
        GetPromptResult(
            messages = listOf(
                PromptMessage(
                    Role.User,
                    TextContent("Prompt with arguments: arg1='$arg1', arg2='$arg2'"),
                ),
            ),
        )
    }

    // 3. Prompt with image
    addPrompt(
        name = "test_prompt_with_image",
        description = "test_prompt_with_image",
    ) {
        GetPromptResult(
            messages = listOf(
                PromptMessage(Role.User, ImageContent(data = PNG_BASE64, mimeType = "image/png")),
                PromptMessage(Role.User, TextContent("Please analyze the image above.")),
            ),
        )
    }

    // 4. Prompt with embedded resource
    addPrompt(
        name = "test_prompt_with_embedded_resource",
        description = "test_prompt_with_embedded_resource",
        arguments = listOf(
            PromptArgument(name = "resourceUri", description = "URI of the resource to embed", required = true),
        ),
    ) { request ->
        val resourceUri = request.arguments?.get("resourceUri") ?: "test://embedded-resource"
        GetPromptResult(
            messages = listOf(
                PromptMessage(
                    Role.User,
                    EmbeddedResource(
                        resource = TextResourceContents(
                            text = "Embedded resource content for testing",
                            uri = resourceUri,
                            mimeType = "text/plain",
                        ),
                    ),
                ),
                PromptMessage(Role.User, TextContent("Please process the embedded resource above.")),
            ),
        )
    }

    // 5. Dynamic prompt
    val server = this
    addPrompt(
        name = "test_dynamic_prompt",
        description = "test_dynamic_prompt",
    ) {
        // Add a temporary prompt, triggering listChanged
        server.addPrompt(
            name = "test_dynamic_prompt_temp",
            description = "Temporary dynamic prompt",
        ) {
            GetPromptResult(
                messages = listOf(
                    PromptMessage(Role.User, TextContent("Temporary prompt response")),
                ),
            )
        }
        delay(100.milliseconds)
        // Remove the temporary prompt, triggering listChanged again
        server.removePrompt("test_dynamic_prompt_temp")
        GetPromptResult(
            messages = listOf(
                PromptMessage(Role.User, TextContent("Dynamic prompt executed successfully")),
            ),
        )
    }
}
