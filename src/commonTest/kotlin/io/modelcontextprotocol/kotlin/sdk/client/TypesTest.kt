package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.Request
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlin.test.Test

class TypesTest {

    @Test
    fun testRequestResult() {
        val message =
            "{\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"tools\":{\"listChanged\":true},\"resources\":{}},\"serverInfo\":{\"name\":\"jetbrains/proxy\",\"version\":\"0.1.0\"}},\"jsonrpc\":\"2.0\",\"id\":1}"
        McpJson.decodeFromString<JSONRPCMessage>(message)
    }

    @Test
    fun testRequestError() {
        val message =
            "{\"method\":\"initialize\", \"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"experimental\":{},\"sampling\":{}},\"clientInfo\":{\"name\":\"test client\",\"version\":\"1.0\"},\"_meta\":{}}"
        McpJson.decodeFromString<Request>(message)
    }

    @Test
    fun testJSONRPCMessage() {
        val line = "{\"result\":{\"content\":[{\"type\":\"text\"}],\"isError\":false},\"jsonrpc\":\"2.0\",\"id\":4}"
        McpJson.decodeFromString<JSONRPCMessage>(line)
    }

    @Test
    fun testJSONRPCMessageWithStringId() {
        val line = """
            {
              "jsonrpc": "2.0",
              "method": "initialize",
              "id": "ebf9f64a-0",
              "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": {
                  "name": "mcp-java-client",
                  "version": "0.2.0"
                }
              }
            }
        """.trimIndent()
        McpJson.decodeFromString<JSONRPCMessage>(line)
    }
}
