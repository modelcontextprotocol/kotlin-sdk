package io.modelcontextprotocol.kotlin.sdk.conformance

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

fun Server.registerConformanceResources() {
    // 1. Static text resource
    addResource(
        uri = "test://static-text",
        name = "static-text",
        description = "A static text resource for testing",
        mimeType = "text/plain",
    ) {
        ReadResourceResult(
            listOf(
                TextResourceContents(
                    text = "This is the content of the static text resource.",
                    uri = "test://static-text",
                    mimeType = "text/plain",
                ),
            ),
        )
    }

    // 2. Static binary resource
    addResource(
        uri = "test://static-binary",
        name = "static-binary",
        description = "A static binary resource for testing",
        mimeType = "image/png",
    ) {
        ReadResourceResult(
            listOf(
                BlobResourceContents(
                    blob = PNG_BASE64,
                    uri = "test://static-binary",
                    mimeType = "image/png",
                ),
            ),
        )
    }

    // 3. Template resource
    addResourceTemplate(
        uriTemplate = "test://template/{id}/data",
        name = "template",
        description = "A template resource for testing",
        mimeType = "application/json",
    ) { request, variables ->
        val id = variables["id"]
        ReadResourceResult(
            listOf(
                TextResourceContents(
                    text = """{"id": "$id"}""",
                    uri = request.uri,
                    mimeType = "application/json",
                ),
            ),
        )
    }

    // 4. Watched resource
    addResource(
        uri = "test://watched-resource",
        name = "watched-resource",
        description = "A watched resource for testing",
        mimeType = "text/plain",
    ) {
        ReadResourceResult(
            listOf(
                TextResourceContents(
                    text = "Watched resource content.",
                    uri = "test://watched-resource",
                    mimeType = "text/plain",
                ),
            ),
        )
    }

    // 5. Dynamic resource
    val server = this
    addResource(
        uri = "test://dynamic-resource",
        name = "dynamic-resource",
        description = "A dynamic resource for testing",
        mimeType = "text/plain",
    ) {
        // Add a temporary resource, triggering listChanged
        server.addResource(
            uri = "test://dynamic-resource-temp",
            name = "dynamic-resource-temp",
            description = "Temporary dynamic resource",
            mimeType = "text/plain",
        ) {
            ReadResourceResult(
                listOf(
                    TextResourceContents(
                        text = "Temporary resource content.",
                        uri = "test://dynamic-resource-temp",
                        mimeType = "text/plain",
                    ),
                ),
            )
        }
        delay(100.milliseconds)
        // Remove the temporary resource, triggering listChanged again
        server.removeResource("test://dynamic-resource-temp")
        ReadResourceResult(
            listOf(
                TextResourceContents(
                    text = "Dynamic resource content.",
                    uri = "test://dynamic-resource",
                    mimeType = "text/plain",
                ),
            ),
        )
    }
}
