package io.modelcontextprotocol.kotlin.sdk

import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.CompleteRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ContentTypes
import io.modelcontextprotocol.kotlin.sdk.types.ReferenceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class OldSchemaTypesTest {

    @Test
    fun `should have correct latest protocol version`() {
        assertNotEquals("", LATEST_PROTOCOL_VERSION)
        assertEquals("2025-06-18", LATEST_PROTOCOL_VERSION)
    }

    @Test
    fun `should have correct supported protocol versions`() {
        assertIs<List<String>>(SUPPORTED_PROTOCOL_VERSIONS)
        assertTrue(SUPPORTED_PROTOCOL_VERSIONS.contains(LATEST_PROTOCOL_VERSION))
        assertTrue(SUPPORTED_PROTOCOL_VERSIONS.contains("2024-11-05"))
        assertEquals(3, SUPPORTED_PROTOCOL_VERSIONS.size)
    }

    @Test
    fun `should validate JSONRPC version constant`() {
        assertEquals("2.0", JSONRPC_VERSION)
    }

    // Reference Tests
    @Test
    fun `should validate ResourceTemplateReference`() {
        val resourceRef = ResourceTemplateReference(uri = "file:///path/to/file.txt")

        assertEquals(ReferenceType.ResourceTemplate, resourceRef.type)
        assertEquals("file:///path/to/file.txt", resourceRef.uri)
    }

    @Test
    fun `should serialize and deserialize ResourceTemplateReference correctly`() {
        val resourceRef = ResourceTemplateReference(uri = "https://example.com/resource")

        val json = McpJson.encodeToString<Reference>(resourceRef)
        val decoded = McpJson.decodeFromString<Reference>(json)

        assertIs<ResourceTemplateReference>(decoded)
        assertEquals(ReferenceType.ResourceTemplate, decoded.type)
        assertEquals("https://example.com/resource", decoded.uri)
    }

    @Test
    fun `should validate PromptReference`() {
        val promptRef = PromptReference(name = "greeting")

        assertEquals(ReferenceType.Prompt, promptRef.type)
        assertEquals("greeting", promptRef.name)
    }

    @Test
    fun `should serialize and deserialize PromptReference correctly`() {
        val promptRef = PromptReference(name = "test-prompt")

        val json = McpJson.encodeToString<Reference>(promptRef)
        val decoded = McpJson.decodeFromString<Reference>(json)

        assertIs<PromptReference>(decoded)
        assertEquals(ReferenceType.Prompt, decoded.type)
        assertEquals("test-prompt", decoded.name)
    }

    // PromptMessageContent Tests
    @Test
    fun `should validate text content`() {
        val textContent = TextContent(text = "Hello, world!")

        assertEquals(ContentTypes.TEXT, textContent.type)
        assertEquals("Hello, world!", textContent.text)
    }

    @Test
    fun `should serialize and deserialize text content correctly`() {
        val textContent = TextContent(text = "Test message")

        val json = McpJson.encodeToString<PromptMessageContent>(textContent)
        val decoded = McpJson.decodeFromString<PromptMessageContent>(json)

        assertIs<TextContent>(decoded)
        assertEquals(ContentTypes.TEXT, decoded.type)
        assertEquals("Test message", decoded.text)
    }

    @Test
    fun `should validate image content`() {
        val imageContent = ImageContent(
            data = "aGVsbG8=", // base64 encoded "hello"
            mimeType = "image/png",
        )

        assertEquals(ContentTypes.IMAGE, imageContent.type)
        assertEquals("aGVsbG8=", imageContent.data)
        assertEquals("image/png", imageContent.mimeType)
    }

    @Test
    fun `should serialize and deserialize image content correctly`() {
        val imageContent = ImageContent(
            data = "dGVzdA==", // base64 encoded "test"
            mimeType = "image/jpeg",
        )

        val json = McpJson.encodeToString<PromptMessageContent>(imageContent)
        val decoded = McpJson.decodeFromString<PromptMessageContent>(json)

        assertIs<ImageContent>(decoded)
        assertEquals(ContentTypes.IMAGE, decoded.type)
        assertEquals("dGVzdA==", decoded.data)
        assertEquals("image/jpeg", decoded.mimeType)
    }

    @Test
    fun `should validate audio content`() {
        val audioContent = AudioContent(
            data = "aGVsbG8=", // base64 encoded "hello"
            mimeType = "audio/mp3",
        )

        assertEquals(ContentTypes.AUDIO, audioContent.type)
        assertEquals("aGVsbG8=", audioContent.data)
        assertEquals("audio/mp3", audioContent.mimeType)
    }

    @Test
    fun `should serialize and deserialize audio content correctly`() {
        val audioContent = AudioContent(
            data = "YXVkaW8=", // base64 encoded "audio"
            mimeType = "audio/wav",
        )

        val json = McpJson.encodeToString<PromptMessageContent>(audioContent)
        val decoded = McpJson.decodeFromString<PromptMessageContent>(json)

        assertIs<AudioContent>(decoded)
        assertEquals(ContentTypes.AUDIO, decoded.type)
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

        assertEquals(ContentTypes.EMBEDDED_RESOURCE, embeddedResource.type)
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

        val json = McpJson.encodeToString<PromptMessageContent>(embeddedResource)
        val decoded = McpJson.decodeFromString<PromptMessageContent>(json)

        assertIs<EmbeddedResource>(decoded)
        assertEquals(ContentTypes.EMBEDDED_RESOURCE, decoded.type)
        assertIs<BlobResourceContents>(decoded.resource)
        val decodedBlob = decoded.resource
        assertEquals("YmluYXJ5ZGF0YQ==", decodedBlob.blob)
        assertEquals("file:///path/to/binary.dat", decodedBlob.uri)
        assertEquals("application/octet-stream", decodedBlob.mimeType)
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
        assertEquals(ContentTypes.TEXT, promptMessage.content.type)
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
        assertEquals(ContentTypes.EMBEDDED_RESOURCE, promptMessage.content.type)
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
            lastModified = "2025-06-18T00:00:00Z",
            priority = 0.5,
        )

        val json = McpJson.encodeToString(annotations)
        val decoded = McpJson.decodeFromString<Annotations>(json)

        assertEquals(listOf(Role.assistant), decoded.audience)
        assertEquals("2025-06-18T00:00:00Z", decoded.lastModified)
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
        assertEquals(ContentTypes.TEXT, toolResult.content[0].type)
        assertEquals(ContentTypes.EMBEDDED_RESOURCE, toolResult.content[1].type)
        assertEquals(ContentTypes.EMBEDDED_RESOURCE, toolResult.content[2].type)
        assertEquals(null, toolResult.isError)
    }

    @Test
    fun `should validate empty content array with default`() {
        val toolResult = CallToolResult(content = emptyList())

        assertEquals(0, toolResult.content.size)
        assertEquals(null, toolResult.isError)
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
        val decoded = McpJson.decodeFromString<io.modelcontextprotocol.kotlin.sdk.types.CallToolResult>(json)

        assertEquals(2, decoded.content.size)
        assertIs<TextContent>(decoded.content[0])
        assertIs<ImageContent>(decoded.content[1])
        assertEquals(false, decoded.isError)
    }

    // CompleteRequest Tests
    @Test
    fun `should validate CompleteRequest with prompt reference`() {
        val request = CompleteRequest(
            CompleteRequestParams(
                ref = PromptReference(name = "greeting"),
                argument = CompleteRequest.Argument(name = "name", value = "A"),
            ),
        )

        assertEquals("completion/complete", request.method.value)
        assertIs<PromptReference>(request.params.ref)
        val promptRef = request.params.ref
        assertEquals("greeting", promptRef.name)
        assertEquals("name", request.params.argument.name)
        assertEquals("A", request.params.argument.value)
    }

    @Test
    fun `should validate CompleteRequest with resource reference`() {
        val request = CompleteRequest(
            CompleteRequestParams(
                ref = ResourceTemplateReference(uri = "github://repos/{owner}/{repo}"),
                argument = CompleteRequest.Argument(name = "repo", value = "t"),
            ),
        )

        assertEquals("completion/complete", request.method.value)
        assertIs<ResourceTemplateReference>(request.params.ref)
        val resourceRef = request.params.ref
        assertEquals("github://repos/{owner}/{repo}", resourceRef.uri)
        assertEquals("repo", request.params.argument.name)
        assertEquals("t", request.params.argument.value)
    }

    @Test
    fun `should serialize and deserialize CompleteRequest correctly`() {
        val request = CompleteRequest(
            CompleteRequestParams(
                ref = PromptReference(name = "test"),
                argument = CompleteRequest.Argument(name = "arg", value = ""),
            ),
        )

        val json = McpJson.encodeToString(request)
        val decoded = McpJson.decodeFromString<io.modelcontextprotocol.kotlin.sdk.types.CompleteRequest>(json)

        assertEquals("completion/complete", decoded.method.value)
        assertIs<PromptReference>(decoded.params.ref)
        val promptRef = decoded.params.ref
        assertEquals("test", promptRef.name)
        assertEquals("arg", decoded.params.argument.name)
        assertEquals("", decoded.params.argument.value)
    }

    @Test
    fun `should validate CompleteRequest with complex URIs`() {
        val request = CompleteRequest(
            CompleteRequestParams(
                ref = ResourceTemplateReference(uri = "api://v1/{tenant}/{resource}/{id}"),
                argument = CompleteRequest.Argument(name = "id", value = "123"),
            ),
        )

        val resourceRef = request.params.ref as ResourceTemplateReference
        assertEquals("api://v1/{tenant}/{resource}/{id}", resourceRef.uri)
        assertEquals("id", request.params.argument.name)
        assertEquals("123", request.params.argument.value)
    }

    // InitializeResult Tests
    @Test
    fun `should create InitializeResult with default instructions`() {
        val serverInfo = Implementation(name = "test-server", version = "1.0.0")
        val result = InitializeResult(
            capabilities = ServerCapabilities(),
            serverInfo = serverInfo,
        )

        assertEquals(LATEST_PROTOCOL_VERSION, result.protocolVersion)
        assertEquals(serverInfo, result.serverInfo)
        assertEquals(null, result.instructions)
    }

    @Test
    fun `should create InitializeResult with custom instructions`() {
        val serverInfo = Implementation(name = "test-server", version = "1.0.0")
        val instructions = "Use this server to perform calculations. Call the 'add' tool to add numbers."
        val result = InitializeResult(
            capabilities = ServerCapabilities(),
            serverInfo = serverInfo,
            instructions = instructions,
        )

        assertEquals(LATEST_PROTOCOL_VERSION, result.protocolVersion)
        assertEquals(serverInfo, result.serverInfo)
        assertEquals(instructions, result.instructions)
    }

    @Test
    fun `should serialize and deserialize InitializeResult with instructions`() {
        val serverInfo = Implementation(name = "test-server", version = "1.0.0")
        val instructions = "This server provides file system access. Use the 'read' tool to read files."
        val result = InitializeResult(
            capabilities = ServerCapabilities(),
            serverInfo = serverInfo,
            instructions = instructions,
        )

        val json = McpJson.encodeToString(result)
        val decoded = McpJson.decodeFromString<InitializeResult>(json)

        assertEquals(LATEST_PROTOCOL_VERSION, decoded.protocolVersion)
        assertEquals(serverInfo, decoded.serverInfo)
        assertEquals(instructions, decoded.instructions)
    }

    @Test
    fun `should serialize and deserialize InitializeResult without instructions`() {
        val serverInfo = Implementation(name = "test-server", version = "1.0.0")
        val result = InitializeResult(
            capabilities = ServerCapabilities(),
            serverInfo = serverInfo,
        )

        val json = McpJson.encodeToString(result)
        val decoded = McpJson.decodeFromString<InitializeResult>(json)

        assertEquals(LATEST_PROTOCOL_VERSION, decoded.protocolVersion)
        assertEquals(serverInfo, decoded.serverInfo)
        assertEquals(null, decoded.instructions)
    }
}
