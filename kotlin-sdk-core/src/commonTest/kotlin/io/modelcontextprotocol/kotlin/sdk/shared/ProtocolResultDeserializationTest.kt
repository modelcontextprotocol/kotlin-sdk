package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.CreateTaskResult
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
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.TaskMetadata
import io.modelcontextprotocol.kotlin.sdk.types.TaskStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test

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
