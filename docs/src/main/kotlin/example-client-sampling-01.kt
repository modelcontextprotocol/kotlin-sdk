// This file was automatically generated from README.md by Knit tool. Do not edit.
package io.modelcontextprotocol.kotlin.sdk.examples.exampleClientSampling01

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject

fun main() {

val client = Client(
    clientInfo = Implementation("demo-client", "1.0.0"),
    options = ClientOptions(
        capabilities = ClientCapabilities(
            sampling = buildJsonObject { putJsonObject("tools") { } }, // drop tools if you don't support tool use
        ),
    ),
)

client.setRequestHandler<CreateMessageRequest>(Method.Defined.SamplingCreateMessage) { request, _ ->
    val content = request.messages.lastOrNull()?.content
    val prompt = if (content is TextContent) content.text else "your topic"
    CreateMessageResult(
        model = "gpt-4o-mini",
        role = Role.Assistant,
        content = TextContent(text = "Here is a short note about $prompt"),
    )
}
}
