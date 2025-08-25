package io.modelcontextprotocol.kotlin.sdk

import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class TypesTest {

    @Test
    fun `should have correct latest protocol version`() {
        assertNotEquals("", LATEST_PROTOCOL_VERSION)
        assertEquals("2025-03-26", LATEST_PROTOCOL_VERSION)
    }

    @Test
    fun `should have correct supported protocol versions`() {
        assertIs<Array<String>>(SUPPORTED_PROTOCOL_VERSIONS)
        assertTrue(SUPPORTED_PROTOCOL_VERSIONS.contains(LATEST_PROTOCOL_VERSION))
        assertTrue(SUPPORTED_PROTOCOL_VERSIONS.contains("2024-11-05"))
        assertEquals(2, SUPPORTED_PROTOCOL_VERSIONS.size)
    }

    @Test
    fun `should validate JSONRPC version constant`() {
        assertEquals("2.0", JSONRPC_VERSION)
    }

    // Reference Tests
    @Test
    fun `should validate ResourceTemplateReference`() {
        val resourceRef = ResourceTemplateReference(uri = "file:///path/to/file.txt")

        assertEquals("ref/resource", resourceRef.type)
        assertEquals("file:///path/to/file.txt", resourceRef.uri)
    }

    @Test
    fun `should serialize and deserialize ResourceTemplateReference correctly`() {
        val resourceRef = ResourceTemplateReference(uri = "https://example.com/resource")

        val json = McpJson.encodeToString<Reference>(resourceRef)
        val decoded = McpJson.decodeFromString<Reference>(json)

        assertIs<ResourceTemplateReference>(decoded)
        assertEquals("ref/resource", decoded.type)
        assertEquals("https://example.com/resource", decoded.uri)
    }

    @Test
    fun `should validate PromptReference`() {
        val promptRef = PromptReference(name = "greeting")

        assertEquals("ref/prompt", promptRef.type)
        assertEquals("greeting", promptRef.name)
    }

    @Test
    fun `should serialize and deserialize PromptReference correctly`() {
        val promptRef = PromptReference(name = "test-prompt")

        val json = McpJson.encodeToString<Reference>(promptRef)
        val decoded = McpJson.decodeFromString<Reference>(json)

        assertIs<PromptReference>(decoded)
        assertEquals("ref/prompt", decoded.type)
        assertEquals("test-prompt", decoded.name)
    }

    @Test
    fun `should handle UnknownReference for invalid type`() {
        val invalidJson = """{"type": "invalid_type"}"""

        val decoded = McpJson.decodeFromString<Reference>(invalidJson)

        assertIs<UnknownReference>(decoded)
        assertEquals("invalid_type", decoded.type)
    }

    // PromptMessageContent Tests
    @Test
    fun `should validate text content`() {
        val textContent = TextContent(text = "Hello, world!")

        assertEquals("text", textContent.type)
        assertEquals("Hello, world!", textContent.text)
    }

    @Test
    fun `should serialize and deserialize text content correctly`() {
        val textContent = TextContent(text = "Test message")

        val json = McpJson.encodeToString<ContentBlock>(textContent)
        val decoded = McpJson.decodeFromString<ContentBlock>(json)

        assertIs<TextContent>(decoded)
        assertEquals("text", decoded.type)
        assertEquals("Test message", decoded.text)
    }

    @Test
    fun `should validate image content`() {
        val imageContent = ImageContent(
            data = "aGVsbG8=", // base64 encoded "hello"
            mimeType = "image/png",
        )

        assertEquals("image", imageContent.type)
        assertEquals("aGVsbG8=", imageContent.data)
        assertEquals("image/png", imageContent.mimeType)
    }

    @Test
    fun `should serialize and deserialize image content correctly`() {
        val imageContent = ImageContent(
            data = "dGVzdA==", // base64 encoded "test"
            mimeType = "image/jpeg",
        )

        val json = McpJson.encodeToString<ContentBlock>(imageContent)
        val decoded = McpJson.decodeFromString<ContentBlock>(json)

        assertIs<ImageContent>(decoded)
        assertEquals("image", decoded.type)
        assertEquals("dGVzdA==", decoded.data)
        assertEquals("image/jpeg", decoded.mimeType)
    }

    @Test
    fun `should validate audio content`() {
        val audioContent = AudioContent(
            data = "aGVsbG8=", // base64 encoded "hello"
            mimeType = "audio/mp3",
        )

        assertEquals("audio", audioContent.type)
        assertEquals("aGVsbG8=", audioContent.data)
        assertEquals("audio/mp3", audioContent.mimeType)
    }

    @Test
    fun `should serialize and deserialize audio content correctly`() {
        val audioContent = AudioContent(
            data = "YXVkaW8=", // base64 encoded "audio"
            mimeType = "audio/wav",
        )

        val json = McpJson.encodeToString<ContentBlock>(audioContent)
        val decoded = McpJson.decodeFromString<ContentBlock>(json)

        assertIs<AudioContent>(decoded)
        assertEquals("audio", decoded.type)
        assertEquals("YXVkaW8=", decoded.data)
        assertEquals("audio/wav", decoded.mimeType)
    }

    @Test
    fun `should validate embedded resource content`() {
        val resource = TextResourceContents(
            text = "File contents",
            uri = "file:///path/to/file.txt",
            mimeType = "text/plain",
        )
        val embeddedResource = EmbeddedResource(resource = resource)

        assertEquals("resource", embeddedResource.type)
        assertEquals(resource, embeddedResource.resource)
    }

    @Test
    fun `should serialize and deserialize embedded resource content correctly`() {
        val resource = BlobResourceContents(
            blob = "YmluYXJ5ZGF0YQ==",
            uri = "file:///path/to/binary.dat",
            mimeType = "application/octet-stream",
        )
        val embeddedResource = EmbeddedResource(resource = resource)

        val json = McpJson.encodeToString<ContentBlock>(embeddedResource)
        val decoded = McpJson.decodeFromString<ContentBlock>(json)

        assertIs<EmbeddedResource>(decoded)
        assertEquals("resource", decoded.type)
        assertIs<BlobResourceContents>(decoded.resource)
        val decodedBlob = decoded.resource
        assertEquals("YmluYXJ5ZGF0YQ==", decodedBlob.blob)
        assertEquals("file:///path/to/binary.dat", decodedBlob.uri)
        assertEquals("application/octet-stream", decodedBlob.mimeType)
    }

    @Test
    fun `should handle unknown content type`() {
        val unknownJson = """{"type": "unknown_type"}"""

        val decoded = McpJson.decodeFromString<ContentBlock>(unknownJson)

        assertIs<UnknownContent>(decoded)
        assertEquals("unknown_type", decoded.type)
    }

    // PromptMessage Tests
    @Test
    fun `should validate prompt message with text content`() {
        val textContent = TextContent(text = "Hello, assistant!")
        val promptMessage = PromptMessage(
            role = Role.user,
            content = textContent,
        )

        assertEquals(Role.user, promptMessage.role)
        assertEquals(textContent, promptMessage.content)
        assertEquals("text", promptMessage.content.type)
    }

    @Test
    fun `should validate prompt message with embedded resource`() {
        val resource = TextResourceContents(
            text = "Primary application entry point",
            uri = "file:///project/src/main.rs",
            mimeType = "text/x-rust",
        )
        val embeddedResource = EmbeddedResource(resource = resource)
        val promptMessage = PromptMessage(
            role = Role.assistant,
            content = embeddedResource,
        )

        assertEquals(Role.assistant, promptMessage.role)
        assertEquals("resource", promptMessage.content.type)
        val content = promptMessage.content as EmbeddedResource
        val textResource = content.resource as TextResourceContents
        assertEquals("Primary application entry point", textResource.text)
        assertEquals("file:///project/src/main.rs", textResource.uri)
        assertEquals("text/x-rust", textResource.mimeType)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `should serialize and deserialize annotations correctly`() {
        val annotations = Annotations(
            audience = listOf(Role.assistant),
            lastModified = Instant.parse("2025-06-18T00:00:00Z"),
            priority = 0.5,
        )

        val json = McpJson.encodeToString(annotations)
        val decoded = McpJson.decodeFromString<Annotations>(json)

        assertEquals(listOf(Role.assistant), decoded.audience)
        assertEquals(Instant.parse("2025-06-18T00:00:00Z"), decoded.lastModified)
        assertEquals(0.5, decoded.priority)
    }

    @Test
    fun `should serialize and deserialize prompt message correctly`() {
        val imageContent = ImageContent(
            data = "aW1hZ2VkYXRh", // base64 encoded "imagedata"
            mimeType = "image/png",
        )
        val promptMessage = PromptMessage(
            role = Role.assistant,
            content = imageContent,
        )

        val json = McpJson.encodeToString(promptMessage)
        val decoded = McpJson.decodeFromString<PromptMessage>(json)

        assertEquals(Role.assistant, decoded.role)
        assertIs<ImageContent>(decoded.content)
        val decodedContent = decoded.content
        assertEquals("aW1hZ2VkYXRh", decodedContent.data)
        assertEquals("image/png", decodedContent.mimeType)
    }

    // CallToolResult Tests
    @Test
    fun `should validate tool result with multiple content types`() {
        val toolResult = CallToolResult(
            content = listOf(
                TextContent(text = "Found the following files:"),
                EmbeddedResource(
                    resource = TextResourceContents(
                        text = "fn main() {}",
                        uri = "file:///project/src/main.rs",
                        mimeType = "text/x-rust",
                    ),
                ),
                EmbeddedResource(
                    resource = TextResourceContents(
                        text = "pub mod lib;",
                        uri = "file:///project/src/lib.rs",
                        mimeType = "text/x-rust",
                    ),
                ),
            ),
        )

        assertEquals(3, toolResult.content.size)
        assertEquals("text", toolResult.content[0].type)
        assertEquals("resource", toolResult.content[1].type)
        assertEquals("resource", toolResult.content[2].type)
        assertEquals(false, toolResult.isError)
    }

    @Test
    fun `should validate empty content array with default`() {
        val toolResult = CallToolResult(content = emptyList())

        assertEquals(0, toolResult.content.size)
        assertEquals(false, toolResult.isError)
    }

    @Test
    fun `should serialize and deserialize CallToolResult correctly`() {
        val toolResult = CallToolResult(
            content = listOf(
                TextContent(text = "Operation completed"),
                ImageContent(data = "aW1hZ2U=", mimeType = "image/png"),
            ),
            isError = false,
        )

        val json = McpJson.encodeToString(toolResult)
        val decoded = McpJson.decodeFromString<CallToolResult>(json)

        assertEquals(2, decoded.content.size)
        assertIs<TextContent>(decoded.content[0])
        assertIs<ImageContent>(decoded.content[1])
        assertEquals(false, decoded.isError)
    }

    // CompleteRequest Tests
    @Test
    fun `should validate CompleteRequest with prompt reference`() {
        val request = CompleteRequest(
            ref = PromptReference(name = "greeting"),
            argument = CompleteRequest.Argument(name = "name", value = "A"),
        )

        assertEquals("completion/complete", request.method.value)
        assertIs<PromptReference>(request.ref)
        val promptRef = request.ref
        assertEquals("greeting", promptRef.name)
        assertEquals("name", request.argument.name)
        assertEquals("A", request.argument.value)
    }

    @Test
    fun `should validate CompleteRequest with resource reference`() {
        val request = CompleteRequest(
            ref = ResourceTemplateReference(uri = "github://repos/{owner}/{repo}"),
            argument = CompleteRequest.Argument(name = "repo", value = "t"),
        )

        assertEquals("completion/complete", request.method.value)
        assertIs<ResourceTemplateReference>(request.ref)
        val resourceRef = request.ref
        assertEquals("github://repos/{owner}/{repo}", resourceRef.uri)
        assertEquals("repo", request.argument.name)
        assertEquals("t", request.argument.value)
    }

    @Test
    fun `should serialize and deserialize CompleteRequest correctly`() {
        val request = CompleteRequest(
            ref = PromptReference(name = "test"),
            argument = CompleteRequest.Argument(name = "arg", value = ""),
        )

        val json = McpJson.encodeToString(request)
        val decoded = McpJson.decodeFromString<CompleteRequest>(json)

        assertEquals("completion/complete", decoded.method.value)
        assertIs<PromptReference>(decoded.ref)
        val promptRef = decoded.ref
        assertEquals("test", promptRef.name)
        assertEquals("arg", decoded.argument.name)
        assertEquals("", decoded.argument.value)
    }

    @Test
    fun `should validate CompleteRequest with complex URIs`() {
        val request = CompleteRequest(
            ref = ResourceTemplateReference(uri = "api://v1/{tenant}/{resource}/{id}"),
            argument = CompleteRequest.Argument(name = "id", value = "123"),
        )

        val resourceRef = request.ref as ResourceTemplateReference
        assertEquals("api://v1/{tenant}/{resource}/{id}", resourceRef.uri)
        assertEquals("id", request.argument.name)
        assertEquals("123", request.argument.value)
    }
}
