package io.modelcontextprotocol.kotlin.sdk

import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TypesUtilTest {

    // ErrorCode Serializer Tests
    @Test
    fun `should serialize and deserialize ErrorCode Defined correctly`() {
        val errorCode: ErrorCode = ErrorCode.Defined.InvalidRequest

        val json = McpJson.encodeToString(errorCode)
        val decoded = McpJson.decodeFromString<ErrorCode>(json)

        assertEquals(ErrorCode.Defined.InvalidRequest, decoded)
        assertEquals(-32600, decoded.code)
    }

    @Test
    fun `should serialize and deserialize ErrorCode Unknown correctly`() {
        val errorCode: ErrorCode = ErrorCode.Unknown(1001)

        val json = McpJson.encodeToString(errorCode)
        val decoded = McpJson.decodeFromString<ErrorCode>(json)

        assertIs<ErrorCode.Unknown>(decoded)
        assertEquals(1001, decoded.code)
    }

    // Method Serializer Tests
    @Test
    fun `should serialize and deserialize Method Defined correctly`() {
        val method: Method = Method.Defined.Initialize

        val json = McpJson.encodeToString(method)
        val decoded = McpJson.decodeFromString<Method>(json)

        assertEquals(Method.Defined.Initialize, decoded)
        assertEquals("initialize", decoded.value)
    }

    @Test
    fun `should serialize and deserialize Method Custom correctly`() {
        val method: Method = Method.Custom("custom/method")

        val json = McpJson.encodeToString(method)
        val decoded = McpJson.decodeFromString<Method>(json)

        assertIs<Method.Custom>(decoded)
        assertEquals("custom/method", decoded.value)
    }

    // StopReason Serializer Tests
    @Test
    fun `should serialize and deserialize StopReason EndTurn correctly`() {
        val stopReason: StopReason = StopReason.EndTurn

        val json = McpJson.encodeToString(stopReason)
        val decoded = McpJson.decodeFromString<StopReason>(json)

        assertEquals(StopReason.EndTurn, decoded)
        assertEquals("endTurn", decoded.value)
    }

    @Test
    fun `should serialize and deserialize StopReason Other correctly`() {
        val stopReason: StopReason = StopReason.Other("custom_reason")

        val json = McpJson.encodeToString(stopReason)
        val decoded = McpJson.decodeFromString<StopReason>(json)

        assertIs<StopReason.Other>(decoded)
        assertEquals("custom_reason", decoded.value)
    }

    // Reference Polymorphic Serializer Tests
    @Test
    fun `should deserialize ResourceTemplateReference polymorphically`() {
        val json = """{"type": "ref/resource", "uri": "file:///test.txt"}"""

        val decoded = McpJson.decodeFromString<Reference>(json)

        assertIs<ResourceTemplateReference>(decoded)
        assertEquals("ref/resource", decoded.type)
        assertEquals("file:///test.txt", decoded.uri)
    }

    @Test
    fun `should deserialize PromptReference polymorphically`() {
        val json = """{"type": "ref/prompt", "name": "test-prompt"}"""

        val decoded = McpJson.decodeFromString<Reference>(json)

        assertIs<PromptReference>(decoded)
        assertEquals("ref/prompt", decoded.type)
        assertEquals("test-prompt", decoded.name)
    }

    @Test
    fun `should deserialize UnknownReference for invalid type`() {
        val json = """{"type": "unknown_ref", "data": "test"}"""

        val decoded = McpJson.decodeFromString<Reference>(json)

        assertIs<UnknownReference>(decoded)
        assertEquals("unknown_ref", decoded.type)
    }

    // PromptMessageContent Polymorphic Serializer Tests
    @Test
    fun `should deserialize TextContent polymorphically`() {
        val json = """{"type": "text", "text": "Hello world"}"""

        val decoded = McpJson.decodeFromString<PromptMessageContent>(json)

        assertIs<TextContent>(decoded)
        assertEquals("text", decoded.type)
        assertEquals("Hello world", decoded.text)
    }

    @Test
    fun `should deserialize ImageContent polymorphically`() {
        val json = """{"type": "image", "data": "aW1hZ2U=", "mimeType": "image/png"}"""

        val decoded = McpJson.decodeFromString<PromptMessageContent>(json)

        assertIs<ImageContent>(decoded)
        assertEquals("image", decoded.type)
        assertEquals("aW1hZ2U=", decoded.data)
        assertEquals("image/png", decoded.mimeType)
    }

    @Test
    fun `should deserialize AudioContent polymorphically`() {
        val json = """{"type": "audio", "data": "YXVkaW8=", "mimeType": "audio/mp3"}"""

        val decoded = McpJson.decodeFromString<PromptMessageContent>(json)

        assertIs<AudioContent>(decoded)
        assertEquals("audio", decoded.type)
        assertEquals("YXVkaW8=", decoded.data)
        assertEquals("audio/mp3", decoded.mimeType)
    }

    @Test
    fun `should deserialize EmbeddedResource polymorphically`() {
        val json =
            """{"type": "resource", "resource": {"uri": "file:///test.txt", "mimeType": "text/plain", "text": "content"}}"""

        val decoded = McpJson.decodeFromString<PromptMessageContent>(json)

        assertIs<EmbeddedResource>(decoded)
        assertEquals("resource", decoded.type)
        assertIs<TextResourceContents>(decoded.resource)
        val textResource = decoded.resource
        assertEquals("file:///test.txt", textResource.uri)
        assertEquals("content", textResource.text)
    }

    // ResourceContents Polymorphic Serializer Tests
    @Test
    fun `should deserialize TextResourceContents polymorphically`() {
        val json = """{"uri": "file:///test.txt", "mimeType": "text/plain", "text": "file content"}"""

        val decoded = McpJson.decodeFromString<ResourceContents>(json)

        assertIs<TextResourceContents>(decoded)
        assertEquals("file:///test.txt", decoded.uri)
        assertEquals("file content", decoded.text)
        assertEquals("text/plain", decoded.mimeType)
    }

    @Test
    fun `should deserialize BlobResourceContents polymorphically`() {
        val json = """{"uri": "file:///binary.dat", "mimeType": "application/octet-stream", "blob": "YmluYXJ5"}"""

        val decoded = McpJson.decodeFromString<ResourceContents>(json)

        assertIs<BlobResourceContents>(decoded)
        assertEquals("file:///binary.dat", decoded.uri)
        assertEquals("YmluYXJ5", decoded.blob)
        assertEquals("application/octet-stream", decoded.mimeType)
    }

    @Test
    fun `should deserialize UnknownResourceContents for missing fields`() {
        val json = """{"uri": "file:///unknown.dat", "mimeType": "unknown/type"}"""

        val decoded = McpJson.decodeFromString<ResourceContents>(json)

        assertIs<UnknownResourceContents>(decoded)
        assertEquals("file:///unknown.dat", decoded.uri)
        assertEquals("unknown/type", decoded.mimeType)
    }

    // RequestId Serializer Tests
    @Test
    fun `should serialize and deserialize RequestId StringId correctly`() {
        val requestId: RequestId = RequestId.StringId("test-id")

        val json = McpJson.encodeToString(requestId)
        val decoded = McpJson.decodeFromString<RequestId>(json)

        assertIs<RequestId.StringId>(decoded)
        assertEquals("test-id", decoded.value)
    }

    @Test
    fun `should serialize and deserialize RequestId NumberId correctly`() {
        val requestId: RequestId = RequestId.NumberId(42L)

        val json = McpJson.encodeToString(requestId)
        val decoded = McpJson.decodeFromString<RequestId>(json)

        assertIs<RequestId.NumberId>(decoded)
        assertEquals(42L, decoded.value)
    }

    // Utility Functions Tests
    @Test
    fun `should create CallToolResult ok correctly`() {
        val result = CallToolResult.ok("Success message")

        assertEquals(listOf(TextContent("Success message")), result.content)
        assertEquals(false, result.isError)
        assertEquals(EmptyJsonObject, result._meta)
    }

    @Test
    fun `should create CallToolResult error correctly`() {
        val result = CallToolResult.error("Error message")

        assertEquals(listOf(TextContent("Error message")), result.content)
        assertEquals(true, result.isError)
        assertEquals(EmptyJsonObject, result._meta)
    }

    @Test
    fun `should create CallToolResult with custom meta`() {
        val meta = buildJsonObject { put("custom", "value") }
        val result = CallToolResult.ok("Success", meta)

        assertEquals(listOf(TextContent("Success")), result.content)
        assertEquals(false, result.isError)
        assertEquals(meta, result._meta)
    }
}
