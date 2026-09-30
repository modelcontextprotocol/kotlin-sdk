package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
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
        val jsonRpc = verifyDeserialization<JSONRPCRequest>(
            McpJson,
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
        val jsonRpc = verifyDeserialization<JSONRPCNotification>(
            McpJson,
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
    fun `should serialize JSONRPCResponse with result`() {
        val response = JSONRPCResponse(
            id = RequestId("call-1"),
            result = EmptyResult(
                meta = buildJsonObject { put("durationMs", 15) },
            ),
        )

        verifySerialization<JSONRPCMessage>(
            response,
            McpJson,
            """
            {
              "id": "call-1",
              "result": {
                "_meta": {
                  "durationMs": 15
                }
              },
              "jsonrpc": "2.0"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize JSONRPCResponse with EmptyResult - null meta must be omitted`() {
        val response = JSONRPCResponse(
            id = RequestId("call-2"),
            result = EmptyResult(), // meta = null by default
        )

        verifySerialization(
            response,
            McpJson,
            """
            {
              "id": "call-2",
              "result": {},
              "jsonrpc": "2.0"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should decode JSONRPCMessage as request`() {
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

        val message = verifyDeserialization<JSONRPCMessage>(McpJson, json)
        val request = assertIs<JSONRPCRequest>(message)
        assertEquals("sampling/create", request.method)
        val params = request.params?.jsonObject
        assertNotNull(params)
        assertEquals("gpt", params["model"]?.jsonPrimitive?.content)
        assertEquals("Hello", params["prompt"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should decode JSONRPCMessage as error response`() {
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

        val message = verifyDeserialization<JSONRPCMessage>(McpJson, json)
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

        verifySerialization(
            message,
            McpJson,
            """
            {
              "method": "notifications/log",
              "params": {
                "message": "Polymorphic"
              },
              "jsonrpc": "2.0"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should create JSONRPCRequest with string and numeric ID`() {
        JSONRPCRequest(id = "req-42", method = "ping").id shouldBe RequestId("req-42")
        JSONRPCRequest(id = 42, method = "ping").id shouldBe RequestId(42)
    }

    @Test
    fun `should deserialize JSONRPCEmptyMessage`() {
        val json = """
            {
              "jsonrpc": "2.0"
            }
        """.trimIndent()

        val message = McpJson.decodeFromString<JSONRPCMessage>(json)
        message shouldBeSameInstanceAs JSONRPCEmptyMessage
    }

    @Test
    fun `should round-trip every request type through the polymorphic Request serializer`() {
        val meta = RequestMeta(buildJsonObject { put("progressToken", "t") })
        val metaJson = """"_meta":{"progressToken":"t"}"""
        val cases: List<Pair<Request, String>> = listOf(
            PingRequest() to """{"method":"ping"}""",
            SetLevelRequest(SetLevelRequestParams(LoggingLevel.Info)) to
                """{"method":"logging/setLevel","params":{"level":"info"}}""",
            ListPromptsRequest() to """{"method":"prompts/list"}""",
            GetPromptRequest(GetPromptRequestParams(name = "p")) to
                """{"method":"prompts/get","params":{"name":"p"}}""",
            ListToolsRequest(PaginatedRequestParams(cursor = "c", meta = meta)) to
                """{"method":"tools/list","params":{"cursor":"c",$metaJson}}""",
            CallToolRequest(CallToolRequestParams(name = "t")) to
                """{"method":"tools/call","params":{"name":"t"}}""",
            ListResourcesRequest(PaginatedRequestParams(cursor = "c")) to
                """{"method":"resources/list","params":{"cursor":"c"}}""",
            ListResourceTemplatesRequest(PaginatedRequestParams(cursor = "c")) to
                """{"method":"resources/templates/list","params":{"cursor":"c"}}""",
            ReadResourceRequest(ReadResourceRequestParams(uri = "file:///a", meta = meta)) to
                """{"method":"resources/read","params":{"uri":"file:///a",$metaJson}}""",
            SubscribeRequest(SubscribeRequestParams(uri = "file:///a", meta = meta)) to
                """{"method":"resources/subscribe","params":{"uri":"file:///a",$metaJson}}""",
            UnsubscribeRequest(UnsubscribeRequestParams(uri = "file:///a", meta = meta)) to
                """{"method":"resources/unsubscribe","params":{"uri":"file:///a",$metaJson}}""",
            ListTasksRequest(PaginatedRequestParams(cursor = "c")) to
                """{"method":"tasks/list","params":{"cursor":"c"}}""",
            GetTaskRequest(GetTaskRequestParams(taskId = "t1", meta = meta)) to
                """{"method":"tasks/get","params":{"taskId":"t1",$metaJson}}""",
            GetTaskPayloadRequest(GetTaskPayloadRequestParams(taskId = "t1", meta = meta)) to
                """{"method":"tasks/result","params":{"taskId":"t1",$metaJson}}""",
            CancelTaskRequest(CancelTaskRequestParams(taskId = "t1", meta = meta)) to
                """{"method":"tasks/cancel","params":{"taskId":"t1",$metaJson}}""",
            ListRootsRequest() to """{"method":"roots/list"}""",
            CreateMessageRequest(CreateMessageRequestParams(maxTokens = 1, messages = emptyList())) to
                """{"method":"sampling/createMessage","params":{"maxTokens":1,"messages":[]}}""",
        )

        cases.forEach { (request, json) ->
            withClue(json) { verifySerialization<Request>(request, McpJson, json) }
        }
    }
}
