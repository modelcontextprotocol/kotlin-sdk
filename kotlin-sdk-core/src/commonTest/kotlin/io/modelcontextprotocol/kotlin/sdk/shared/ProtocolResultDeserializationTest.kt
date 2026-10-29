package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.CreateTaskResult
import io.modelcontextprotocol.kotlin.sdk.types.CustomRequest
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.GetTaskPayloadRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetTaskPayloadRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.GetTaskPayloadResult
import io.modelcontextprotocol.kotlin.sdk.types.GetTaskRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetTaskRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.GetTaskResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import io.modelcontextprotocol.kotlin.sdk.types.TaskMetadata
import io.modelcontextprotocol.kotlin.sdk.types.TaskStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains

/**
 * Regression tests for https://github.com/modelcontextprotocol/kotlin-sdk/issues/601:
 * an incoming response must be deserialized according to the original request's method,
 * not by guessing the runtime type from the JSON shape of the result.
 */
class ProtocolResultDeserializationTest {
    private lateinit var protocol: TestProtocol
    private lateinit var transport: RecordingTransport

    @BeforeTest
    fun setUp() {
        protocol = TestProtocol()
        transport = RecordingTransport()
    }

    /**
     * Decodes a raw wire response exactly like the production transports do
     * (`McpJson.decodeFromString<JSONRPCMessage>(...)`), so the test exercises the real
     * polymorphic wire decoding plus the production request/response correlation path.
     */
    private fun wireResponse(id: RequestId, result: JsonObject): JSONRPCMessage {
        val wire = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", McpJson.encodeToJsonElement(id))
            put("result", result)
        }
        return McpJson.decodeFromJsonElement<JSONRPCMessage>(wire)
    }

    private val callToolPayload: JsonObject = buildJsonObject {
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", "task output")
                    },
                )
            },
        )
        put("isError", false)
    }

    private val taskStatePayload: JsonObject = buildJsonObject {
        put("taskId", "task-42")
        put("status", "completed")
        put("createdAt", "2026-01-01T00:00:00Z")
        put("lastUpdatedAt", "2026-01-01T00:01:00Z")
    }

    @Test
    fun `should decode tasks result response as GetTaskPayloadResult even when payload matches another shape`() =
        runTest {
            protocol.connect(transport)

            val inFlight = async {
                protocol.request<GetTaskPayloadResult>(
                    GetTaskPayloadRequest(GetTaskPayloadRequestParams(taskId = "task-42")),
                )
            }
            val sent = transport.awaitRequest()

            // A tasks/result response for a task-augmented tools/call carries a CallToolResult-shaped
            // payload; shape-based decoding resolves it as CallToolResult instead of GetTaskPayloadResult.
            transport.deliver(wireResponse(sent.id, callToolPayload))

            val result = inFlight.await()
            result.json shouldBe callToolPayload
        }

    @Test
    fun `should correlate tasks get and tasks result responses whose payloads share the same shape`() = runTest {
        protocol.connect(transport)

        val taskGet = async {
            protocol.request<GetTaskResult>(GetTaskRequest(GetTaskRequestParams(taskId = "task-42")))
        }
        val taskPayload = async {
            protocol.request<GetTaskPayloadResult>(
                GetTaskPayloadRequest(GetTaskPayloadRequestParams(taskId = "task-42")),
            )
        }
        val getSent = transport.awaitRequest()
        val payloadSent = transport.awaitRequest()

        // Respond in reverse order; both responses carry the exact same GetTaskResult-shaped object,
        // which is a valid tasks/result payload (e.g. the payload of a task-augmented tasks/cancel).
        transport.deliver(wireResponse(payloadSent.id, taskStatePayload))
        transport.deliver(wireResponse(getSent.id, taskStatePayload))

        val payload = taskPayload.await()
        payload.json shouldBe taskStatePayload

        val get = taskGet.await()
        get.taskId shouldBe "task-42"
        get.status shouldBe TaskStatus.Completed
    }

    @Test
    fun `should decode tasks result response whose payload matches no known result shape`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            protocol.request<GetTaskPayloadResult>(
                GetTaskPayloadRequest(GetTaskPayloadRequestParams(taskId = "task-42")),
            )
        }
        val sent = transport.awaitRequest()

        val unknownPayload = buildJsonObject { put("customField", "custom-value") }
        transport.deliver(wireResponse(sent.id, unknownPayload))

        val result = inFlight.await()
        result.json shouldBe unknownPayload
    }

    @Test
    fun `should preserve the raw tasks result when copying a decoded response`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            protocol.request<GetTaskPayloadResult>(
                GetTaskPayloadRequest(GetTaskPayloadRequestParams(taskId = "task-42")),
            )
        }
        val sent = transport.awaitRequest()
        val payload = buildJsonObject {
            callToolPayload.forEach { (key, value) -> put(key, value) }
            put("vendorData", buildJsonObject { put("sequence", 7) })
        }
        val response = wireResponse(RequestId("server-side-id"), payload).shouldBeInstanceOf<JSONRPCResponse>()

        transport.deliver(response.copy(id = sent.id))

        inFlight.await().json shouldBe payload
    }

    @Test
    fun `should use the replacement result when copying a decoded response`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            protocol.request<CallToolResult>(CallToolRequest(CallToolRequestParams(name = "echo")))
        }
        val sent = transport.awaitRequest()
        val replacementPayload = buildJsonObject {
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", "replacement")
                        },
                    )
                },
            )
            put("isError", false)
        }
        val response = wireResponse(RequestId("server-side-id"), callToolPayload).shouldBeInstanceOf<JSONRPCResponse>()
        val replacement = wireResponse(sent.id, replacementPayload).shouldBeInstanceOf<JSONRPCResponse>().result

        transport.deliver(response.copy(id = sent.id, result = replacement))

        McpJson.encodeToJsonElement(inFlight.await()) shouldBe replacementPayload
    }

    @Test
    fun `should keep wire data out of public response value semantics`() {
        val id = RequestId("req-1")
        val response = wireResponse(id, callToolPayload).shouldBeInstanceOf<JSONRPCResponse>()
        val constructed = JSONRPCResponse(id, response.result)

        response shouldBe constructed
        response.hashCode() shouldBe constructed.hashCode()
        response.toString() shouldBe constructed.toString()
        McpJson.encodeToJsonElement(response) shouldBe McpJson.encodeToJsonElement(constructed)

        val copiedId = RequestId("req-2")
        val (actualId, actualResult) = response.copy(id = copiedId)
        actualId shouldBe copiedId
        actualResult shouldBe response.result
    }

    @Test
    fun `should decode empty result for ping`() = runTest {
        protocol.connect(transport)

        val inFlight = async { protocol.request<EmptyResult>(PingRequest()) }
        val sent = transport.awaitRequest()

        transport.deliver(wireResponse(sent.id, buildJsonObject { }))

        inFlight.await()
    }

    @Test
    fun `should decode call tool result`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            protocol.request<CallToolResult>(CallToolRequest(CallToolRequestParams(name = "echo")))
        }
        val sent = transport.awaitRequest()

        transport.deliver(wireResponse(sent.id, callToolPayload))

        val result = inFlight.await()
        result.isError shouldBe false
    }

    @Test
    fun `should report the serialization error for malformed call tool content`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            runCatching {
                protocol.request<CallToolResult>(CallToolRequest(CallToolRequestParams(name = "echo"))).content
            }
        }
        val sent = transport.awaitRequest()
        val malformedPayload = buildJsonObject {
            put("content", buildJsonArray { add(buildJsonObject { put("type", "text") }) })
        }

        transport.deliver(wireResponse(sent.id, malformedPayload))

        val error = inFlight.await().exceptionOrNull().shouldBeInstanceOf<SerializationException>()
        assertContains(error.message.orEmpty(), "text")
    }

    @Test
    fun `should report the shape decoding error for a custom method result`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            runCatching {
                protocol.request<RequestResult>(CustomRequest(Method.Custom("custom/echo"), params = null))
            }
        }
        val sent = transport.awaitRequest()
        val malformedPayload = buildJsonObject {
            put("content", buildJsonArray { add(buildJsonObject { put("type", "text") }) })
        }

        transport.deliver(wireResponse(sent.id, malformedPayload))

        val error = inFlight.await().exceptionOrNull().shouldBeInstanceOf<SerializationException>()
        assertContains(error.message.orEmpty(), "text")
    }

    @Test
    fun `should reject an unrecognized result shape for a custom method`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            runCatching {
                protocol.request<RequestResult>(CustomRequest(Method.Custom("custom/echo"), params = null))
            }
        }
        val sent = transport.awaitRequest()
        transport.deliver(wireResponse(sent.id, buildJsonObject { put("customField", "custom-value") }))

        val error = inFlight.await().exceptionOrNull().shouldBeInstanceOf<SerializationException>()
        assertContains(error.message.orEmpty(), "Cannot determine RequestResult type")
    }

    @Test
    fun `should retain shape-based decoding for valid custom method results`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            protocol.request<CallToolResult>(CustomRequest(Method.Custom("custom/echo"), params = null))
        }
        val sent = transport.awaitRequest()
        transport.deliver(wireResponse(sent.id, callToolPayload))

        McpJson.encodeToJsonElement(inFlight.await()) shouldBe callToolPayload
    }

    @Test
    fun `should retain programmatically constructed custom method results`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            protocol.request<GetTaskPayloadResult>(CustomRequest(Method.Custom("custom/echo"), params = null))
        }
        val sent = transport.awaitRequest()
        val payload = buildJsonObject { put("customField", "custom-value") }
        transport.deliver(JSONRPCResponse(sent.id, GetTaskPayloadResult(payload)))

        inFlight.await().json shouldBe payload
    }

    @Test
    fun `should report the serialization error for an invalid task status`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            runCatching {
                protocol.request<GetTaskResult>(GetTaskRequest(GetTaskRequestParams(taskId = "task-42"))).status
            }
        }
        val sent = transport.awaitRequest()
        val malformedPayload = buildJsonObject {
            taskStatePayload.forEach { (key, value) -> put(key, value) }
            put("status", "bogus-status")
        }

        transport.deliver(wireResponse(sent.id, malformedPayload))

        val error = inFlight.await().exceptionOrNull().shouldBeInstanceOf<SerializationException>()
        assertContains(error.message.orEmpty(), "bogus-status")
    }

    @Test
    fun `should reject a task result for a request without task augmentation`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            runCatching {
                protocol.request<CallToolResult>(CallToolRequest(CallToolRequestParams(name = "echo"))).content
            }
        }
        val sent = transport.awaitRequest()

        transport.deliver(wireResponse(sent.id, buildJsonObject { put("task", taskStatePayload) }))

        val error = inFlight.await().exceptionOrNull().shouldBeInstanceOf<SerializationException>()
        assertContains(error.message.orEmpty(), "content")
    }

    @Test
    fun `should decode task-augmented call tool response as CreateTaskResult`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            protocol.request<CreateTaskResult>(
                CallToolRequest(
                    CallToolRequestParams(name = "slow-tool", arguments = null, task = TaskMetadata()),
                ),
            )
        }
        val sent = transport.awaitRequest()

        transport.deliver(
            wireResponse(
                sent.id,
                buildJsonObject {
                    put(
                        "task",
                        buildJsonObject {
                            put("taskId", "task-7")
                            put("status", "working")
                            put("createdAt", "2026-01-01T00:00:00Z")
                            put("lastUpdatedAt", "2026-01-01T00:00:00Z")
                        },
                    )
                },
            ),
        )

        val result = inFlight.await()
        result.task.taskId shouldBe "task-7"
        result.task.status shouldBe TaskStatus.Working
    }

    @Test
    fun `should decode task envelopes with extensions matching other result shapes`() = runTest {
        protocol.connect(transport)
        val task = buildJsonObject {
            taskStatePayload.forEach { (key, value) -> put(key, value) }
            put("status", "working")
            put("ttl", 60_000)
        }
        val extensions = listOf(buildJsonObject { put("action", "accept") }, callToolPayload)

        for (extension in extensions) {
            val inFlight = async {
                protocol.request<CreateTaskResult>(
                    CallToolRequest(CallToolRequestParams(name = "slow-tool", task = TaskMetadata())),
                )
            }
            val sent = transport.awaitRequest()
            val payload = buildJsonObject {
                put("task", task)
                extension.forEach { (key, value) -> put(key, value) }
            }

            transport.deliver(wireResponse(sent.id, payload))

            val result = inFlight.await()
            result.task.taskId shouldBe "task-42"
            result.task.status shouldBe TaskStatus.Working
        }
    }

    @Test
    fun `should accept a normal result when task augmentation is ignored`() = runTest {
        protocol.connect(transport)

        val inFlight = async {
            protocol.request<CallToolResult>(
                CallToolRequest(CallToolRequestParams(name = "echo", task = TaskMetadata())),
            )
        }
        val sent = transport.awaitRequest()

        transport.deliver(wireResponse(sent.id, callToolPayload))

        inFlight.await().isError shouldBe false
    }

    @Test
    fun `should keep shape-decoded result on the message for direct transport consumers`() {
        val message = wireResponse(
            RequestId("req-1"),
            buildJsonObject {
                put(
                    "tools",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("name", "echo")
                                put(
                                    "inputSchema",
                                    buildJsonObject { put("type", "object") },
                                )
                            },
                        )
                    },
                )
            },
        )

        message.shouldBeInstanceOf<JSONRPCResponse>()
        message.result.shouldBeInstanceOf<ListToolsResult>()
    }
}
