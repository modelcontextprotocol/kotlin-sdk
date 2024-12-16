package client

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.Request
import org.junit.jupiter.api.Test
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson

class TypesTest {

    @Test
    fun testRequestResult() {
        val message = "{\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"tools\":{\"listChanged\":true},\"resources\":{}},\"serverInfo\":{\"name\":\"jetbrains/proxy\",\"version\":\"0.1.0\"}},\"jsonrpc\":\"2.0\",\"id\":1}"
        McpJson.decodeFromString<JSONRPCMessage>(message)
    }

    @Test
    fun testRequestError() {
        val message = "{\"method\":\"initialize\", \"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"experimental\":{},\"sampling\":{}},\"clientInfo\":{\"name\":\"test client\",\"version\":\"1.0\"},\"_meta\":{}}"
        McpJson.decodeFromString<Request>(message)
    }

    @Test
    fun testJSONRPCMessage() {
        val line = "{\"result\":{\"content\":[{\"type\":\"text\"}],\"isError\":false},\"jsonrpc\":\"2.0\",\"id\":4}"
        McpJson.decodeFromString<JSONRPCMessage>(line)
    }
}