package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class JsonRpcTest {

    @Test
    fun `should convert Request to JSONRPCRequest`() {
        val request = ListToolsRequest(
            PaginatedRequestParams(
                cursor = "page-2",
                meta = RequestMeta(
                    buildJsonObject {
                        put("progressToken", "token-123")
                    },
                ),
            ),
        )

        val jsonRpc = request.toJSON()

        assertEquals(Method.Defined.ToolsList.value, jsonRpc.method)
        val params = jsonRpc.params?.jsonObject
        assertNotNull(params)
        assertEquals("page-2", params["cursor"]?.jsonPrimitive?.content)
        val meta = params["_meta"]?.jsonObject
        assertNotNull(meta)
        assertEquals("token-123", meta["progressToken"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should convert JSONRPCRequest to Request`() {
        // language=json
        val jsonRpc = McpJson.decodeFromString<JSONRPCRequest>(
            """
            {
              "id": 17,
              "method": "tools/list",
              "params": {
                "cursor": "page-5",
                "_meta": {
                  "progressToken": 42
                }
              },
              "jsonrpc": "2.0"
            }
            """.trimIndent(),
        )

        val request = jsonRpc.fromJSON()
        val listToolsRequest = assertIs<ListToolsRequest>(request)
        val decodedParams = assertNotNull(listToolsRequest.params)
        assertEquals("page-5", decodedParams.cursor)
        val meta = decodedParams.meta?.json
        assertNotNull(meta)
        assertEquals(42, meta["progressToken"]?.jsonPrimitive?.int)
    }

    @Test
    fun `should convert Notification to JSONRPCNotification`() {
        val notification = LoggingMessageNotification(
            LoggingMessageNotificationParams(
                level = LoggingLevel.Warning,
                data = buildJsonObject { put("message", "Disk space low") },
                logger = "disk-monitor",
                meta = buildJsonObject { put("requestId", "req-99") },
            ),
        )

        val json = McpJson.encodeToString(notification.toJSON())

        json shouldEqualJson """
            {
              "method": "notifications/message",
              "params": {
                "level": "warning",
                "data": {
                  "message": "Disk space low"
                },
                "logger": "disk-monitor",
                "_meta": {
                  "requestId": "req-99"
                }
              },
              "jsonrpc": "2.0"
            }
        """.trimIndent()
    }

    @Test
    fun `should convert JSONRPCNotification to Notification`() {
        // language=json
        val jsonRpc = McpJson.decodeFromString<JSONRPCNotification>(
            """
            {
              "method": "notifications/message",
              "params": {
                "level": "error",
                "data": {
                  "lines": {
                    "count": 3
                  }
                },
                "logger": "pipeline",
                "_meta": {
                  "traceIds": ["abc"]
                }
              },
              "jsonrpc": "2.0"
            }
            """.trimIndent(),
        )

        val notification = jsonRpc.fromJSON()
        val messageNotification = assertIs<LoggingMessageNotification>(notification)
        val decodedParams = messageNotification.params
        assertEquals(LoggingLevel.Error, decodedParams.level)
        val data = decodedParams.data.jsonObject
        assertEquals(3, data["lines"]?.jsonObject?.get("count")?.jsonPrimitive?.int)
        assertEquals("pipeline", decodedParams.logger)
        val meta = decodedParams.meta
        assertNotNull(meta)
        val traceIds = meta["traceIds"]?.jsonArray
        assertNotNull(traceIds)
        assertEquals("abc", traceIds.first().jsonPrimitive.content)
    }

    @Test
    fun `should serialize JSONRPCRequest with params`() {
        val request = JSONRPCRequest(
            id = RequestId("req-1"),
            method = "tools/list",
            params = buildJsonObject {
                put("cursor", "abc")
                put("includeInactive", true)
            },
        )

        val json = McpJson.encodeToString(request)

        json shouldEqualJson """
            {
              "id": "req-1",
              "method": "tools/list",
              "params": {
                "cursor": "abc",
                "includeInactive": true
              },
              "jsonrpc": "2.0"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize JSONRPCRequest with numeric id`() {
        // language=json
        val json = """
            {
              "id": 42,
              "method": "resources/read",
              "params": {
                "uri": "file:///tmp/readme.md"
              },
              "jsonrpc": "2.0"
            }
        """.trimIndent()

        val request = McpJson.decodeFromString<JSONRPCRequest>(json)

        val id = request.id
        assertIs<RequestId.NumberId>(id)
        assertEquals(42L, id.value)
        assertEquals("resources/read", request.method)
        val params = request.params?.jsonObject
        assertNotNull(params)
        assertEquals("file:///tmp/readme.md", params["uri"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should serialize JSONRPCNotification with params`() {
        val notification = JSONRPCNotification(
            method = "notifications/log",
            params = buildJsonObject {
                put("level", "info")
                put("message", "Completed operation")
            },
        )

        val json = McpJson.encodeToString(notification)

        json shouldEqualJson """
            {
              "method": "notifications/log",
              "params": {
                "level": "info",
                "message": "Completed operation"
              },
              "jsonrpc": "2.0"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize JSONRPCNotification`() {
        // language=json
        val json = """
            {
              "method": "notifications/progress",
              "params": {
                "progress": 50,
                "total": 100
              },
              "jsonrpc": "2.0"
            }
        """.trimIndent()

        val notification = McpJson.decodeFromString<JSONRPCNotification>(json)
        assertEquals("notifications/progress", notification.method)
        val params = notification.params?.jsonObject
        assertNotNull(params)
        assertEquals(50, params["progress"]?.jsonPrimitive?.int)
        assertEquals(100, params["total"]?.jsonPrimitive?.int)
    }

    @Test
    fun `should serialize JSONRPCResponse with result`() {
        val response = JSONRPCResponse(
            id = RequestId("call-1"),
            result = EmptyResult(
                meta = buildJsonObject { put("durationMs", 15) },
            ),
        )

        val json = McpJson.encodeToString(response)

        json shouldEqualJson """
            {
              "id": "call-1",
              "result": {
                "_meta": {
                  "durationMs": 15
                }
              },
              "jsonrpc": "2.0"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize JSONRPCResponse with EmptyResult`() {
        // language=json
        val json = """
            {
              "id": 7,
              "jsonrpc": "2.0",
              "result": {
                "_meta": {
                  "cached": true
                }
              }
            }
        """.trimIndent()

        val response = McpJson.decodeFromString<JSONRPCResponse>(json)
        val result = response.result
        assertIs<EmptyResult>(result)
        assertEquals(RequestId.NumberId(7L), response.id)
        val meta = result.meta
        assertNotNull(meta)
        assertEquals(true, meta["cached"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `should serialize JSONRPCError`() {
        val error = JSONRPCError(
            id = RequestId(99),
            error = RPCError(
                code = RPCError.ErrorCode.METHOD_NOT_FOUND,
                message = "Method not found",
                data = buildJsonObject { put("method", "tools/unknown") },
            ),
        )

        val json = McpJson.encodeToString(error)

        json shouldEqualJson """
            {
              "id": 99,
              "error": {
                "code": -32601,
                "message": "Method not found",
                "data": {
                  "method": "tools/unknown"
                }
              },
              "jsonrpc": "2.0"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize JSONRPCError`() {
        // language=json
        val json = """
            {
              "id": "req-404",
              "jsonrpc": "2.0",
              "error": {
                "code": -32602,
                "message": "Invalid params",
                "data": {
                  "field": "limit",
                  "reason": "must be positive"
                }
              }
            }
        """.trimIndent()

        val error = McpJson.decodeFromString<JSONRPCError>(json)
        assertEquals(RequestId("req-404"), error.id)
        assertEquals(RPCError.ErrorCode.INVALID_PARAMS, error.error.code)
        assertEquals("Invalid params", error.error.message)

        val data = error.error.data?.jsonObject
        assertNotNull(data)
        assertEquals("limit", data["field"]?.jsonPrimitive?.content)
        assertEquals("must be positive", data["reason"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should decode JSONRPCMessage as request`() {
        // language=json
        val json = """
            {
              "id": "msg-1",
              "method": "sampling/create",
              "params": {
                "model": "gpt",
                "prompt": "Hello"
              },
              "jsonrpc": "2.0"
            }
        """.trimIndent()

        val message = McpJson.decodeFromString<JSONRPCMessage>(json)
        val request = assertIs<JSONRPCRequest>(message)
        assertEquals("sampling/create", request.method)
        val params = request.params?.jsonObject
        assertNotNull(params)
        assertEquals("gpt", params["model"]?.jsonPrimitive?.content)
        assertEquals("Hello", params["prompt"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should decode JSONRPCMessage as error response`() {
        // language=json
        val json = """
            {
              "id": 123,
              "jsonrpc": "2.0",
              "error": {
                "code": -32001,
                "message": "Request timeout",
                "data": {
                  "timeoutMs": 1000
                }
              }
            }
        """.trimIndent()

        val message = McpJson.decodeFromString<JSONRPCMessage>(json)
        val error = assertIs<JSONRPCError>(message)
        assertEquals(RPCError.ErrorCode.REQUEST_TIMEOUT, error.error.code)
        val data = error.error.data?.jsonObject
        assertNotNull(data)
        assertEquals(1000, data["timeoutMs"]?.jsonPrimitive?.int)
    }

    @Test
    fun `should encode JSONRPCMessage polymorphically`() {
        val message: JSONRPCMessage = JSONRPCNotification(
            method = "notifications/log",
            params = buildJsonObject { put("message", "Polymorphic") },
        )

        val json = McpJson.encodeToString(message)

        json shouldEqualJson """
            {
              "method": "notifications/log",
              "params": {
                "message": "Polymorphic"
              },
              "jsonrpc": "2.0"
            }
        """.trimIndent()
    }

    @Test
    fun `JSONRPCMessage should throw on non-object JSON`() {
        val exception = shouldThrow<SerializationException> {
            McpJson.decodeFromString<JSONRPCMessage>("[\"just a string\"]")
        }

        exception.message shouldBe "Invalid response. JsonObject expected, got: JsonArray"
    }

    @Test
    fun `should create JSONRPCRequest with string ID`() {
        val params = buildJsonObject {
            put("foo", "bar")
        }
        val request = JSONRPCRequest(
            id = "req-42",
            method = "notifications/log",
            params = params,
        )
        request.id shouldBe RequestId("req-42")
        request.method shouldBe "notifications/log"
        request.params shouldBeSameInstanceAs params
    }

    @Test
    fun `should create JSONRPCRequest with numeric ID`() {
        val params = buildJsonObject {
            put("foo", "bar")
        }
        val request = JSONRPCRequest(
            id = 42,
            method = "notifications/log",
            params = params,
        )
        request.id shouldBe RequestId(42)
        request.method shouldBe "notifications/log"
        request.params shouldBeSameInstanceAs params
    }
}
