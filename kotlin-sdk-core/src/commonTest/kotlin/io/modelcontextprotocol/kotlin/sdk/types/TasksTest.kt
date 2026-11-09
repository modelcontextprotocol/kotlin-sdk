package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

class TasksTest {

    @Test
    fun `should serialize Task with all fields`() {
        val task = Task(
            taskId = "task-2",
            status = TaskStatus.Completed,
            statusMessage = "Processing complete",
            createdAt = "2025-01-01T00:00:00Z",
            lastUpdatedAt = "2025-01-01T00:01:00Z",
            ttl = 60000,
            pollInterval = 5000,
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
              "ttl": 60000,
              "pollInterval": 5000
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize all TaskStatus values`() {
        verifySerialization(TaskStatus.Working, McpJson, "\"working\"")
        verifySerialization(TaskStatus.InputRequired, McpJson, "\"input_required\"")
        verifySerialization(TaskStatus.Completed, McpJson, "\"completed\"")
        verifySerialization(TaskStatus.Failed, McpJson, "\"failed\"")
        verifySerialization(TaskStatus.Cancelled, McpJson, "\"cancelled\"")
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

    @Test
    fun `should return null relatedTask when key is absent`() {
        val meta = RequestMeta(
            buildJsonObject { put("progressToken", "pt-1") },
        )

        meta.relatedTask.shouldBeNull()
    }

    @Test
    fun `should serialize CreateTaskResult with meta`() {
        val result = CreateTaskResult(
            task = Task(
                taskId = "task-1",
                status = TaskStatus.Working,
                createdAt = "2025-01-01T00:00:00Z",
                lastUpdatedAt = "2025-01-01T00:00:00Z",
                ttl = 60000,
            ),
            meta = buildJsonObject { put("trace", "abc") },
        )
        val json = """
            {
              "task": {
                "taskId": "task-1",
                "status": "working",
                "createdAt": "2025-01-01T00:00:00Z",
                "lastUpdatedAt": "2025-01-01T00:00:00Z",
                "ttl": 60000
              },
              "_meta": {
                "trace": "abc"
              }
            }
        """.trimIndent()

        verifySerialization<ClientResult>(result, McpJson, json)
        verifySerialization<ServerResult>(result, McpJson, json)
    }

    @Test
    fun `should serialize GetTaskResult with all fields`() {
        val result = GetTaskResult(
            taskId = "task-20",
            status = TaskStatus.Completed,
            statusMessage = "Done",
            createdAt = "2025-01-01T00:00:00Z",
            lastUpdatedAt = "2025-01-01T00:05:00Z",
            ttl = 300000,
            pollInterval = 10000,
            meta = buildJsonObject { put("server", "main") },
        )
        val json = """
            {
              "taskId": "task-20",
              "status": "completed",
              "statusMessage": "Done",
              "createdAt": "2025-01-01T00:00:00Z",
              "lastUpdatedAt": "2025-01-01T00:05:00Z",
              "ttl": 300000,
              "pollInterval": 10000,
              "_meta": {
                "server": "main"
              }
            }
        """.trimIndent()

        verifySerialization<ClientResult>(result, McpJson, json)
        verifySerialization<ServerResult>(result, McpJson, json)
    }

    @Test
    fun `should serialize GetTaskResult with minimal fields`() {
        val result = GetTaskResult(
            taskId = "task-21",
            status = TaskStatus.Working,
            createdAt = "2025-01-01T00:00:00Z",
            lastUpdatedAt = "2025-01-01T00:00:00Z",
            ttl = null,
        )
        val json = """
            {
              "taskId": "task-21",
              "status": "working",
              "createdAt": "2025-01-01T00:00:00Z",
              "lastUpdatedAt": "2025-01-01T00:00:00Z",
              "ttl": null
            }
        """.trimIndent()

        verifySerialization<ClientResult>(result, McpJson, json)
        verifySerialization<ServerResult>(result, McpJson, json)
    }

    @Test
    fun `should decode Task without ttl as unlimited`() {
        val task = McpJson.decodeFromString<Task>(
            """
            {
              "taskId": "task-3",
              "status": "working",
              "createdAt": "2025-01-01T00:00:00Z",
              "lastUpdatedAt": "2025-01-01T00:00:00Z"
            }
            """.trimIndent(),
        )

        task.ttl.shouldBeNull()
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
                    ttl = 60000,
                ),
            ),
            nextCursor = "cursor-abc",
        )
        val json = """
            {
              "tasks": [
                {
                  "taskId": "task-1",
                  "status": "working",
                  "createdAt": "2025-01-01T00:00:00Z",
                  "lastUpdatedAt": "2025-01-01T00:00:00Z",
                  "ttl": null
                },
                {
                  "taskId": "task-2",
                  "status": "completed",
                  "createdAt": "2025-01-01T00:00:00Z",
                  "lastUpdatedAt": "2025-01-01T00:01:00Z",
                  "ttl": 60000
                }
              ],
              "nextCursor": "cursor-abc"
            }
        """.trimIndent()

        verifySerialization<ClientResult>(result, McpJson, json)
        verifySerialization<ServerResult>(result, McpJson, json)
    }
}
