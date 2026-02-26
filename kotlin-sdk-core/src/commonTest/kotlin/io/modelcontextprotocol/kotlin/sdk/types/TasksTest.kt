package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
        assertIs<TaskFields>(task)
        assertEquals("task-3", task.taskId)
        assertEquals(TaskStatus.Failed, task.status)
        assertEquals("Connection lost", task.statusMessage)
        assertEquals("2025-01-01T00:00:00Z", task.createdAt)
        assertEquals("2025-01-01T00:02:00Z", task.lastUpdatedAt)
        assertEquals(30000.0, task.ttl)
        assertEquals(1000.0, task.pollInterval)
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
    fun `RELATED_TASK_META_KEY should have correct value`() {
        assertEquals("io.modelcontextprotocol/related-task", RELATED_TASK_META_KEY)
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
        assertEquals("task-5", result.task.taskId)
        assertEquals(TaskStatus.InputRequired, result.task.status)
        assertEquals("Need more data", result.task.statusMessage)
        assertEquals(2000.0, result.task.pollInterval)
        assertNull(result.task.ttl)
        assertNull(result.meta)
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
        assertEquals(Method.Defined.TasksGet, request.method)
        assertEquals("task-11", request.taskId)
        assertNull(request.meta)
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
        assertIs<TaskFields>(result)
        assertEquals("task-21", result.taskId)
        assertEquals(TaskStatus.Working, result.status)
        assertNull(result.statusMessage)
        assertNull(result.ttl)
        assertNull(result.pollInterval)
        assertNull(result.meta)
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
        assertEquals(Method.Defined.TasksResult, request.method)
        assertEquals("task-31", request.taskId)
        assertNull(request.meta)
    }

    // ========================================================================
    // GetTaskPayloadResult
    // ========================================================================

    @Test
    fun `should serialize GetTaskPayloadResult with meta`() {
        val result = GetTaskPayloadResult(
            meta = buildJsonObject { put("origin", "task-30") },
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
        val result = GetTaskPayloadResult()

        verifySerialization(
            result,
            McpJson,
            "{}",
        )
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
        assertEquals(Method.Defined.TasksList, request.method)
        assertEquals("next-page", request.cursor)
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
        assertEquals(1, result.tasks.size)
        assertEquals("task-99", result.tasks[0].taskId)
        assertEquals(TaskStatus.Cancelled, result.tasks[0].status)
        assertEquals("User cancelled", result.tasks[0].statusMessage)
        assertNull(result.tasks[0].ttl)
        assertNull(result.nextCursor)
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
        assertEquals(Method.Defined.TasksCancel, request.method)
        assertEquals("task-41", request.taskId)
        assertNull(request.meta)
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
        assertIs<TaskFields>(result)
        assertEquals("task-42", result.taskId)
        assertEquals(TaskStatus.Cancelled, result.status)
        assertNull(result.statusMessage)
        assertEquals(120000.0, result.ttl)
        assertEquals(3000.0, result.pollInterval)
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
        assertEquals("task-100", task.taskId)
        assertEquals(TaskStatus.Working, task.status)
        assertNull(task.ttl)
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
        assertNotNull(related)
        assertEquals("task-50", related.taskId)
    }

    @Test
    fun `should return null relatedTask when key is absent`() {
        val meta = RequestMeta(
            buildJsonObject { put("progressToken", "pt-1") },
        )

        assertNull(meta.relatedTask)
    }
}
