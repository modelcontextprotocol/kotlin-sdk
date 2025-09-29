package io.modelcontextprotocol.kotlin.sdk

import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RequestSerializerTest {

    // Client Result Tests
    @Test
    fun `should deserialize CreateMessageResult polymorphically`() {
        val json = """{
            "model": "test-model",
            "role": "assistant", 
            "content": {
                "type": "text",
                "text": "Hello"
            },
            "stopReason": "endTurn"
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<CreateMessageResult>(decoded)
        assertEquals("test-model", decoded.model)
        assertEquals(Role.ASSISTANT, decoded.role)
        assertEquals(StopReason.EndTurn, decoded.stopReason)
    }

    @Test
    fun `should deserialize ListRootsResult polymorphically`() {
        val json = """{
            "roots": [
                {
                    "uri": "file:///test",
                    "name": "Test Root"
                }
            ]
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<ListRootsResult>(decoded)
        assertEquals(1, decoded.roots.size)
        assertEquals("file:///test", decoded.roots[0].uri)
        assertEquals("Test Root", decoded.roots[0].name)
    }

    @Test
    fun `should deserialize CreateElicitationResult polymorphically`() {
        val json = """{
            "action": "accept",
            "content": {
                "timezone": "Europe/Amsterdam"
            }
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<CreateElicitationResult>(decoded)
        assertEquals(CreateElicitationResult.Action.accept, decoded.action)
    }

    // Server Result Tests
    @Test
    fun `should deserialize ListToolsResult polymorphically`() {
        val json = """{
            "tools": [
                {
                    "name": "test-tool",
                    "description": "A test tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {}
                    }
                }
            ]
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<ListToolsResult>(decoded)
        assertEquals(1, decoded.tools.size)
        assertEquals("test-tool", decoded.tools[0].name)
        assertEquals("A test tool", decoded.tools[0].description)
    }

    @Test
    fun `should deserialize ListResourcesResult polymorphically`() {
        val json = """{
            "resources": [
                {
                    "uri": "file:///test.txt",
                    "name": "test.txt",
                    "mimeType": "text/plain"
                }
            ]
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<ListResourcesResult>(decoded)
        assertEquals(1, decoded.resources.size)
        assertEquals("file:///test.txt", decoded.resources[0].uri)
        assertEquals("test.txt", decoded.resources[0].name)
        assertEquals("text/plain", decoded.resources[0].mimeType)
    }

    @Test
    fun `should deserialize ListResourceTemplatesResult polymorphically`() {
        val json = """{
            "resourceTemplates": [
                {
                    "uriTemplate": "file:///templates/{name}",
                    "name": "template",
                    "mimeType": "text/plain"
                }
            ]
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<ListResourceTemplatesResult>(decoded)
        assertEquals(1, decoded.resourceTemplates.size)
        assertEquals("file:///templates/{name}", decoded.resourceTemplates[0].uriTemplate)
        assertEquals("template", decoded.resourceTemplates[0].name)
        assertEquals("text/plain", decoded.resourceTemplates[0].mimeType)
    }

    @Test
    fun `should deserialize ListPromptsResult polymorphically`() {
        val json = """{
            "prompts": [
                {
                    "name": "test-prompt",
                    "description": "A test prompt"
                }
            ]
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<ListPromptsResult>(decoded)
        assertEquals(1, decoded.prompts.size)
        assertEquals("test-prompt", decoded.prompts[0].name)
        assertEquals("A test prompt", decoded.prompts[0].description)
    }

    @Test
    fun `should deserialize InitializeResult polymorphically`() {
        val json = """{
            "capabilities": {
                "logging": {},
                "prompts": {
                    "listChanged": true
                },
                "resources": {
                    "subscribe": true,
                    "listChanged": true
                },
                "tools": {
                    "listChanged": true
                }
            },
            "protocolVersion": "2024-11-05",
            "serverInfo": {
                "name": "Test Server",
                "version": "1.0.0"
            }
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<InitializeResult>(decoded)
        assertEquals("2024-11-05", decoded.protocolVersion)
        assertEquals("Test Server", decoded.serverInfo.name)
        assertEquals("1.0.0", decoded.serverInfo.version)
    }

    @Test
    fun `should deserialize GetPromptResult polymorphically`() {
        val json = """{
            "description": "A test prompt",
            "messages": [
                {
                    "role": "user",
                    "content": {
                        "type": "text",
                        "text": "Hello"
                    }
                }
            ]
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<GetPromptResult>(decoded)
        assertEquals("A test prompt", decoded.description)
        assertEquals(1, decoded.messages.size)
        assertEquals(Role.USER, decoded.messages[0].role)
    }

    @Test
    fun `should deserialize CompleteResult polymorphically`() {
        val json = """{
            "completion": {
                "values": ["option1", "option2"],
                "total": 2,
                "hasMore": false
            }
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<CompleteResult>(decoded)
        assertEquals(2, decoded.completion.values.size)
        assertEquals("option1", decoded.completion.values[0])
        assertEquals("option2", decoded.completion.values[1])
        assertEquals(2, decoded.completion.total)
        assertEquals(false, decoded.completion.hasMore)
    }

    @Test
    fun `should deserialize ReadResourceResult polymorphically`() {
        val json = """{
            "contents": [
                {
                    "uri": "file:///test.txt",
                    "mimeType": "text/plain",
                    "text": "Hello World"
                }
            ]
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<ReadResourceResult>(decoded)
        assertEquals(1, decoded.contents.size)
        assertIs<TextResourceContents>(decoded.contents[0])
        val textContent = decoded.contents[0] as TextResourceContents
        assertEquals("file:///test.txt", textContent.uri)
        assertEquals("text/plain", textContent.mimeType)
        assertEquals("Hello World", textContent.text)
    }

    @Test
    fun `should deserialize CallToolResult polymorphically`() {
        val json = """{
            "content": [
                {
                    "type": "text",
                    "text": "Tool result"
                }
            ],
            "isError": false
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<CallToolResult>(decoded)
        assertEquals(1, decoded.content.size)
        assertIs<TextContent>(decoded.content[0])
        assertEquals("Tool result", (decoded.content[0] as TextContent).text)
        assertEquals(false, decoded.isError)
    }

    @Test
    fun `should deserialize CompatibilityCallToolResult polymorphically`() {
        val json = """{
            "toolResult": {"result": "Legacy tool result"},
            "content": [],
            "isError": false
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<CompatibilityCallToolResult>(decoded)
        assertEquals(buildJsonObject { put("result", "Legacy tool result") }, decoded.toolResult)
    }

    // Fallback Test
    @Test
    fun `should deserialize EmptyRequestResult for unknown result type`() {
        val json = """{"unknownField": "value"}"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<EmptyRequestResult>(decoded)
        assertEquals(EmptyJsonObject, decoded._meta)
    }

    @Test
    fun `should handle empty JSON object`() {
        val json = """{}"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<EmptyRequestResult>(decoded)
    }

    // Priority Test - Client results should take precedence over server results
    @Test
    fun `should prioritize client results over server results when both match`() {
        // This JSON could potentially match both CreateMessageResult (client) and CallToolResult (server)
        // but CreateMessageResult should be selected first due to the order
        val json = """{
            "model": "test-model",
            "role": "assistant",
            "content": {
                "type": "text", 
                "text": "Test message"
            },
            "stopReason": "endTurn"
        }"""

        val decoded = McpJson.decodeFromString<RequestResult>(json)

        assertIs<CreateMessageResult>(decoded)
        assertEquals("test-model", decoded.model)
    }
}
