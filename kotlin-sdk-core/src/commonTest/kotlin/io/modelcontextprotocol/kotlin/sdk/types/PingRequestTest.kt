package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class PingRequestTest {

    @Test
    fun `should serialize PingRequest without params`() {
        val request = PingRequest()

        val json = McpJson.encodeToString(request)

        json shouldEqualJson """
            {
              "method": "ping"
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize PingRequest with meta`() {
        val request = PingRequest(
            BaseRequestParams(
                meta = RequestMeta(
                    buildJsonObject { put("progressToken", "ping-42") },
                ),
            ),
        )

        val json = McpJson.encodeToString(request)

        json shouldEqualJson """
            {
              "method": "ping",
              "params": {
                "_meta": {
                  "progressToken": "ping-42"
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should convert PingRequest to JSONRPCRequest`() {
        val request = PingRequest(
            BaseRequestParams(
                meta = RequestMeta(
                    buildJsonObject { put("progressToken", 99) },
                ),
            ),
        )

        val jsonRpc = request.toJSON()

        assertEquals("ping", jsonRpc.method)
        val params = assertNotNull(jsonRpc.params).jsonObject
        val meta = params["_meta"]?.jsonObject
        assertNotNull(meta)
        assertEquals(99, meta["progressToken"]?.jsonPrimitive?.int)
    }

    @Test
    fun `should deserialize JSONRPCRequest to PingRequest`() {
        val json = """
            {
              "id": "ping-1",
              "method": "ping",
              "jsonrpc": "2.0",
              "params": {
                "_meta": {
                  "progressToken": "pong-1"
                }
              }
            }
        """.trimIndent()

        val jsonRpc = McpJson.decodeFromString<JSONRPCRequest>(json)
        val request = jsonRpc.fromJSON()

        val pingRequest = assertIs<PingRequest>(request)
        val meta = pingRequest.params?.meta?.json
        assertNotNull(meta)
        assertEquals("pong-1", meta["progressToken"]?.jsonPrimitive?.content)
    }
}
