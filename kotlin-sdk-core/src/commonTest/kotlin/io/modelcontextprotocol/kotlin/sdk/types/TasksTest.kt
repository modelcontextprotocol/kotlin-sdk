package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

class TasksTest {

    // ========================================================================
    // Task
    // ========================================================================

    @Test
    fun `should serialize Task with minimal fields`() {
        val task = Task(
            taskId = "task-1",
            status = TaskStatus.Working,
            createdAt = "2025-01-01T00:00:00Z",
            lastUpdatedAt = "2025-01-01T00:00:00Z",
            ttl = null,
        )

        verifySerialization(
            task,
            McpJson,
            """
            {
              "taskId": "task-1",
              "status": "working",
              "createdAt": "2025-01-01T00:00:00Z",
              "lastUpdatedAt": "2025-01-01T00:00:00Z"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize Task with all fields`() {
        val task = Task(
            taskId = "task-2",
            status = TaskStatus.Completed,
            statusMessage = "Processing complete",
            createdAt = "2025-01-01T00:00:00Z",
            lastUpdatedAt = "2025-01-01T00:01:00Z",
            ttl = 60000.0,
            pollInterval = 5000.0,
        )

        verifySerialization(
            task,
            McpJson,
            """
            {
              "taskId": "task-2",
              "status": "completed",
              "statusMessage": "Processing complete",
              "createdAt": "2025-01-01T00:00:00Z",
              "lastUpdatedAt": "2025-01-01T00:01:00Z",
              "ttl": 60000.0,
              "pollInterval": 5000.0
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize Task and verify TaskFields contract`() {
        val json = """
            {
              "taskId": "task-3",
              "status": "failed",
              "statusMessage": "Connection lost",
              "createdAt": "2025-01-01T00:00:00Z",
              "lastUpdatedAt": "2025-01-01T00:02:00Z",
              "ttl": 30000.0,
              "pollInterval": 1000.0
            }
        """.trimIndent()

        val task = verifyDeserialization<Task>(McpJson, json)
        task.shouldBeInstanceOf<TaskFields>()
        task.taskId shouldBe "task-3"
        task.status shouldBe TaskStatus.Failed
        task.statusMessage shouldBe "Connection lost"
        task.createdAt shouldBe "2025-01-01T00:00:00Z"
        task.lastUpdatedAt shouldBe "2025-01-01T00:02:00Z"
        task.ttl shouldBe 30000.0
        task.pollInterval shouldBe 1000.0
    }

    // ========================================================================
    // TaskStatus
    // ========================================================================

    @Test
    fun `should serialize all TaskStatus values`() {
        verifySerialization(TaskStatus.Working, McpJson, "\"working\"")
        verifySerialization(TaskStatus.InputRequired, McpJson, "\"input_required\"")
        verifySerialization(TaskStatus.Completed, McpJson, "\"completed\"")
        verifySerialization(TaskStatus.Failed, McpJson, "\"failed\"")
        verifySerialization(TaskStatus.Cancelled, McpJson, "\"cancelled\"")
    }

    // ========================================================================
    // TaskMetadata
    // ========================================================================

    @Test
    fun `should serialize TaskMetadata with ttl`() {
        val metadata = TaskMetadata(ttl = 120000.0)

        verifySerialization(
            metadata,
            McpJson,
            """
            {
              "ttl": 120000.0
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize TaskMetadata without ttl`() {
        val metadata = TaskMetadata()

        verifySerialization(
            metadata,
            McpJson,
            "{}",
        )
    }

    // ========================================================================
    // RelatedTaskMetadata
    // ========================================================================

    @Test
    fun `should serialize RelatedTaskMetadata`() {
        val related = RelatedTaskMetadata(taskId = "task-42")

        verifySerialization(
            related,
            McpJson,
            """
            {
              "taskId": "task-42"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize RequestMeta with RelatedTaskMetadata`() {
        val json = """
            {
              "io.modelcontextprotocol/related-task": {
                "taskId": "786512e2-9e0d-44bd-8f29-789f320fe840"
              }
            }
        """.trimIndent()

        val meta = McpJson.decodeFromString<RequestMeta>(json)
        val related = meta.relatedTask
        related.shouldNotBeNull()
        related.taskId shouldBe "786512e2-9e0d-44bd-8f29-789f320fe840"
    }

    // ========================================================================
    // CreateTaskResult
    // ========================================================================

    @Test
    fun `should serialize CreateTaskResult`() {
        val result = CreateTaskResult(
            task = Task(
                taskId = "task-1",
                status = TaskStatus.Working,
                createdAt = "2025-01-01T00:00:00Z",
                lastUpdatedAt = "2025-01-01T00:00:00Z",
                ttl = null,
            ),
        )

        verifySerialization(
            result,
            McpJson,
            """
            {
              "task": {
                "taskId": "task-1",
                "status": "working",
                "createdAt": "2025-01-01T00:00:00Z",
                "lastUpdatedAt": "2025-01-01T00:00:00Z"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize CreateTaskResult with meta`() {
        val result = CreateTaskResult(
            task = Task(
                taskId = "task-1",
                status = TaskStatus.Working,
                createdAt = "2025-01-01T00:00:00Z",
                lastUpdatedAt = "2025-01-01T00:00:00Z",
                ttl = 60000.0,
            ),
            meta = buildJsonObject { put("trace", "abc") },
        )

        verifySerialization(
            result,
            McpJson,
            """
            {
              "task": {
                "taskId": "task-1",
                "status": "working",
                "createdAt": "2025-01-01T00:00:00Z",
                "lastUpdatedAt": "2025-01-01T00:00:00Z",
                "ttl": 60000.0
              },
              "_meta": {
                "trace": "abc"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize CreateTaskResult`() {
        val json = """
            {
              "task": {
                "taskId": "task-5",
                "status": "input_required",
                "statusMessage": "Need more data",
                "createdAt": "2025-01-01T00:00:00Z",
                "lastUpdatedAt": "2025-01-01T00:01:00Z",
                "pollInterval": 2000.0
              }
            }
        """.trimIndent()

        val result = verifyDeserialization<CreateTaskResult>(McpJson, json)
        result.task.taskId shouldBe "task-5"
        result.task.status shouldBe TaskStatus.InputRequired
        result.task.statusMessage shouldBe "Need more data"
        result.task.pollInterval shouldBe 2000.0
        result.task.ttl.shouldBeNull()
        result.meta.shouldBeNull()
    }

    // ========================================================================
    // GetTaskRequest
    // ========================================================================

    @Test
    fun `should serialize GetTaskRequest`() {
        val request = GetTaskRequest(
            GetTaskRequestParams(taskId = "task-10"),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "tasks/get",
              "params": {
                "taskId": "task-10"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize GetTaskRequest with meta`() {
        val request = GetTaskRequest(
            GetTaskRequestParams(
                taskId = "task-10",
                meta = RequestMeta(
                    buildJsonObject { put("progressToken", "pt-1") },
                ),
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "tasks/get",
              "params": {
                "taskId": "task-10",
                "_meta": {
                  "progressToken": "pt-1"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize GetTaskRequest`() {
        val json = """
            {
              "method": "tasks/get",
              "params": {
                "taskId": "task-11"
              }
            }
        """.trimIndent()

        val request = verifyDeserialization<GetTaskRequest>(McpJson, json)
        request.method shouldBe Method.Defined.TasksGet
        request.taskId shouldBe "task-11"
        request.meta.shouldBeNull()
    }

    // ========================================================================
    // GetTaskResult
    // ========================================================================

    @Test
    fun `should serialize GetTaskResult with all fields`() {
        val result = GetTaskResult(
            taskId = "task-20",
            status = TaskStatus.Completed,
            statusMessage = "Done",
            createdAt = "2025-01-01T00:00:00Z",
            lastUpdatedAt = "2025-01-01T00:05:00Z",
            ttl = 300000.0,
            pollInterval = 10000.0,
            meta = buildJsonObject { put("server", "main") },
        )

        verifySerialization(
            result,
            McpJson,
            """
            {
              "taskId": "task-20",
              "status": "completed",
              "statusMessage": "Done",
              "createdAt": "2025-01-01T00:00:00Z",
              "lastUpdatedAt": "2025-01-01T00:05:00Z",
              "ttl": 300000.0,
              "pollInterval": 10000.0,
              "_meta": {
                "server": "main"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize GetTaskResult and verify TaskFields contract`() {
        val json = """
            {
              "taskId": "task-21",
              "status": "working",
              "createdAt": "2025-01-01T00:00:00Z",
              "lastUpdatedAt": "2025-01-01T00:00:30Z"
            }
        """.trimIndent()

        val result = verifyDeserialization<GetTaskResult>(McpJson, json)
        result.shouldBeInstanceOf<TaskFields>()
        result.taskId shouldBe "task-21"
        result.status shouldBe TaskStatus.Working
        result.statusMessage.shouldBeNull()
        result.ttl.shouldBeNull()
        result.pollInterval.shouldBeNull()
        result.meta.shouldBeNull()
    }

    // ========================================================================
    // GetTaskPayloadRequest
    // ========================================================================

    @Test
    fun `should serialize GetTaskPayloadRequest`() {
        val request = GetTaskPayloadRequest(
            GetTaskPayloadRequestParams(taskId = "task-30"),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "tasks/result",
              "params": {
                "taskId": "task-30"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize GetTaskPayloadRequest`() {
        val json = """
            {
              "method": "tasks/result",
              "params": {
                "taskId": "task-31"
              }
            }
        """.trimIndent()

        val request = verifyDeserialization<GetTaskPayloadRequest>(McpJson, json)
        request.method shouldBe Method.Defined.TasksResult
        request.taskId shouldBe "task-31"
        request.meta.shouldBeNull()
    }

    // ========================================================================
    // GetTaskPayloadResult
    // ========================================================================

    @Test
    fun `should serialize GetTaskPayloadResult with meta`() {
        val result = GetTaskPayloadResult(
            buildJsonObject {
                put("_meta", buildJsonObject { put("origin", "task-30") })
            },
        )

        verifySerialization(
            result,
            McpJson,
            """
            {
              "_meta": {
                "origin": "task-30"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize GetTaskPayloadResult without meta`() {
        val result = GetTaskPayloadResult(buildJsonObject { })

        verifySerialization(result, McpJson, "{}")
    }

    @Test
    fun `should preserve arbitrary payload fields in GetTaskPayloadResult`() {
        val json = """
            {
              "_meta": {
                "origin": "task-42"
              },
              "content": [
                {
                  "type": "text",
                  "text": "hello"
                }
              ],
              "isError": false
            }
        """.trimIndent()

        val result = verifyDeserialization<GetTaskPayloadResult>(McpJson, json)
        result.meta.shouldNotBeNull()
        result.meta?.get("origin")?.jsonPrimitive?.content shouldBe "task-42"
        result["content"].shouldNotBeNull()
        result["isError"].shouldNotBeNull()
    }

    // ========================================================================
    // ListTasksRequest
    // ========================================================================

    @Test
    fun `should serialize ListTasksRequest without params`() {
        val request = ListTasksRequest()

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "tasks/list"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ListTasksRequest with cursor`() {
        val request = ListTasksRequest(
            params = PaginatedRequestParams(cursor = "page-2"),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "tasks/list",
              "params": {
                "cursor": "page-2"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize ListTasksRequest`() {
        val json = """
            {
              "method": "tasks/list",
              "params": {
                "cursor": "next-page"
              }
            }
        """.trimIndent()

        val request = verifyDeserialization<ListTasksRequest>(McpJson, json)
        request.method shouldBe Method.Defined.TasksList
        request.cursor shouldBe "next-page"
    }

    // ========================================================================
    // ListTasksResult
    // ========================================================================

    @Test
    fun `should serialize ListTasksResult with empty list`() {
        val result = ListTasksResult(tasks = emptyList())

        verifySerialization(
            result,
            McpJson,
            """
            {
              "tasks": []
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ListTasksResult with tasks and pagination`() {
        val result = ListTasksResult(
            tasks = listOf(
                Task(
                    taskId = "task-1",
                    status = TaskStatus.Working,
                    createdAt = "2025-01-01T00:00:00Z",
                    lastUpdatedAt = "2025-01-01T00:00:00Z",
                    ttl = null,
                ),
                Task(
                    taskId = "task-2",
                    status = TaskStatus.Completed,
                    createdAt = "2025-01-01T00:00:00Z",
                    lastUpdatedAt = "2025-01-01T00:01:00Z",
                    ttl = 60000.0,
                ),
            ),
            nextCursor = "cursor-abc",
        )

        verifySerialization(
            result,
            McpJson,
            """
            {
              "tasks": [
                {
                  "taskId": "task-1",
                  "status": "working",
                  "createdAt": "2025-01-01T00:00:00Z",
                  "lastUpdatedAt": "2025-01-01T00:00:00Z"
                },
                {
                  "taskId": "task-2",
                  "status": "completed",
                  "createdAt": "2025-01-01T00:00:00Z",
                  "lastUpdatedAt": "2025-01-01T00:01:00Z",
                  "ttl": 60000.0
                }
              ],
              "nextCursor": "cursor-abc"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize ListTasksResult`() {
        val json = """
            {
              "tasks": [
                {
                  "taskId": "task-99",
                  "status": "cancelled",
                  "statusMessage": "User cancelled",
                  "createdAt": "2025-01-01T00:00:00Z",
                  "lastUpdatedAt": "2025-01-01T00:03:00Z"
                }
              ]
            }
        """.trimIndent()

        val result = verifyDeserialization<ListTasksResult>(McpJson, json)
        result.tasks.size shouldBe 1
        result.tasks[0].taskId shouldBe "task-99"
        result.tasks[0].status shouldBe TaskStatus.Cancelled
        result.tasks[0].statusMessage shouldBe "User cancelled"
        result.tasks[0].ttl.shouldBeNull()
        result.nextCursor.shouldBeNull()
    }

    // ========================================================================
    // CancelTaskRequest
    // ========================================================================

    @Test
    fun `should serialize CancelTaskRequest`() {
        val request = CancelTaskRequest(
            CancelTaskRequestParams(taskId = "task-40"),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "tasks/cancel",
              "params": {
                "taskId": "task-40"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize CancelTaskRequest`() {
        val json = """
            {
              "method": "tasks/cancel",
              "params": {
                "taskId": "task-41"
              }
            }
        """.trimIndent()

        val request = verifyDeserialization<CancelTaskRequest>(McpJson, json)
        request.method shouldBe Method.Defined.TasksCancel
        request.taskId shouldBe "task-41"
        request.meta.shouldBeNull()
    }

    // ========================================================================
    // CancelTaskResult
    // ========================================================================

    @Test
    fun `should serialize CancelTaskResult`() {
        val result = CancelTaskResult(
            taskId = "task-40",
            status = TaskStatus.Cancelled,
            statusMessage = "Cancelled by user",
            createdAt = "2025-01-01T00:00:00Z",
            lastUpdatedAt = "2025-01-01T00:02:00Z",
            ttl = null,
        )

        verifySerialization(
            result,
            McpJson,
            """
            {
              "taskId": "task-40",
              "status": "cancelled",
              "statusMessage": "Cancelled by user",
              "createdAt": "2025-01-01T00:00:00Z",
              "lastUpdatedAt": "2025-01-01T00:02:00Z"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize CancelTaskResult and verify TaskFields contract`() {
        val json = """
            {
              "taskId": "task-42",
              "status": "cancelled",
              "createdAt": "2025-01-01T00:00:00Z",
              "lastUpdatedAt": "2025-01-01T00:04:00Z",
              "ttl": 120000.0,
              "pollInterval": 3000.0
            }
        """.trimIndent()

        val result = verifyDeserialization<CancelTaskResult>(McpJson, json)
        result.shouldBeInstanceOf<TaskFields>()
        result.taskId shouldBe "task-42"
        result.status shouldBe TaskStatus.Cancelled
        result.statusMessage.shouldBeNull()
        result.ttl shouldBe 120000.0
        result.pollInterval shouldBe 3000.0
    }

    // ========================================================================
    // Deserialization with unknown fields (ignoreUnknownKeys)
    // ========================================================================

    @Test
    fun `should deserialize Task ignoring unknown fields`() {
        val json = """
            {
              "taskId": "task-100",
              "status": "working",
              "createdAt": "2025-01-01T00:00:00Z",
              "lastUpdatedAt": "2025-01-01T00:00:00Z",
              "unknownField": "should be ignored"
            }
        """.trimIndent()

        val task = McpJson.decodeFromString<Task>(json)
        task.taskId shouldBe "task-100"
        task.status shouldBe TaskStatus.Working
        task.ttl.shouldBeNull()
    }

    // ========================================================================
    // RequestMeta.relatedTask accessor
    // ========================================================================

    @Test
    fun `should read relatedTask from RequestMeta`() {
        val meta = RequestMeta(
            buildJsonObject {
                put(
                    RELATED_TASK_META_KEY,
                    buildJsonObject { put("taskId", "task-50") },
                )
            },
        )

        val related = meta.relatedTask
        related.shouldNotBeNull()
        related.taskId shouldBe "task-50"
    }

    @Test
    fun `should return null relatedTask when key is absent`() {
        val meta = RequestMeta(
            buildJsonObject { put("progressToken", "pt-1") },
        )

        meta.relatedTask.shouldBeNull()
    }

    @Test
    fun `GetTaskResult cast to CancelTaskResult via generic does not throw due to type erasure`() {
        val result: RequestResult = GetTaskResult(
            taskId = "task-1",
            status = TaskStatus.Cancelled,
            createdAt = "2025-01-01T00:00:00Z",
            lastUpdatedAt = "2025-01-01T00:01:00Z",
            ttl = null,
        )

        // Simulates what Protocol.request<T>() does:
        // the cast goes through a generic type parameter, so JVM erases T to RequestResult
        fun <T : RequestResult> uncheckedCast(value: RequestResult): T {
            @Suppress("UNCHECKED_CAST")
            return value as T
        }

        val cancelResult: CancelTaskResult = uncheckedCast(result)

        cancelResult.taskId shouldBe "task-1"
        cancelResult.status shouldBe TaskStatus.Cancelled
    }
}
