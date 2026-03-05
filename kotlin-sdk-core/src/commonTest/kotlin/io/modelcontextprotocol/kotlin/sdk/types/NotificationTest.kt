package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationTest {

    @Test
    fun `should serialize CancelledNotification with reason and meta`() {
        val notification = CancelledNotification(
            CancelledNotificationParams(
                requestId = RequestId("req-1"),
                reason = "User requested cancellation",
                meta = buildJsonObject { put("source", "client") },
            ),
        )

        verifySerialization(
            notification,
            McpJson,
            """
            {
              "method": "notifications/cancelled",
              "params": {
                "requestId": "req-1",
                "reason": "User requested cancellation",
                "_meta": {
                  "source": "client"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize CancelledNotification with numeric request id`() {
        val json = """
            {
              "method": "notifications/cancelled",
              "params": {
                "requestId": 42,
                "reason": "Timeout reached"
              }
            }
        """.trimIndent()

        val notification = verifyDeserialization<CancelledNotification>(McpJson, json)
        val params = notification.params

        assertEquals(Method.Defined.NotificationsCancelled, notification.method)
        assertEquals(RequestId(42), params.requestId)
        assertEquals("Timeout reached", params.reason)
        assertNull(params.meta)
    }

    @Test
    fun `should serialize InitializedNotification with meta`() {
        val notification = InitializedNotification(
            BaseNotificationParams(
                meta = buildJsonObject { put("readyAt", "2025-01-12T15:00:58Z") },
            ),
        )

        verifySerialization(
            notification,
            McpJson,
            """
            {
              "method": "notifications/initialized",
              "params": {
                "_meta": {
                  "readyAt": "2025-01-12T15:00:58Z"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize InitializedNotification without params`() {
        val json = """
            {
              "method": "notifications/initialized"
            }
        """.trimIndent()

        val notification = verifyDeserialization<InitializedNotification>(McpJson, json)

        assertEquals(Method.Defined.NotificationsInitialized, notification.method)
        assertNull(notification.params)
    }

    @Test
    fun `should serialize ProgressNotification with all fields`() {
        val notification = ProgressNotification(
            ProgressNotificationParams(
                progressToken = ProgressToken("task-42"),
                progress = 0.6,
                total = 1.0,
                message = "Syncing repository",
                meta = buildJsonObject { put("stage", "download") },
            ),
        )

        verifySerialization(
            notification,
            McpJson,
            """
            {
              "method": "notifications/progress",
              "params": {
                "progressToken": "task-42",
                "progress": 0.6,
                "total": 1.0,
                "message": "Syncing repository",
                "_meta": {
                  "stage": "download"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize ProgressNotification with numeric token`() {
        val json = """
            {
              "method": "notifications/progress",
              "params": {
                "progressToken": 7,
                "progress": 0.25
              }
            }
        """.trimIndent()

        val notification = verifyDeserialization<ProgressNotification>(McpJson, json)
        val params = notification.params

        assertEquals(Method.Defined.NotificationsProgress, notification.method)
        assertEquals(ProgressToken(7), params.progressToken)
        assertEquals(0.25, params.progress)
        assertNull(params.total)
        assertNull(params.message)
        assertNull(params.meta)
    }

    @Test
    fun `should serialize ResourceUpdatedNotification with meta`() {
        val notification = ResourceUpdatedNotification(
            ResourceUpdatedNotificationParams(
                uri = "file:///workspace/README.md",
                meta = buildJsonObject { put("checksum", "abcd1234") },
            ),
        )

        verifySerialization(
            notification,
            McpJson,
            """
            {
              "method": "notifications/resources/updated",
              "params": {
                "uri": "file:///workspace/README.md",
                "_meta": {
                  "checksum": "abcd1234"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize ResourceUpdatedNotification`() {
        val json = """
            {
              "method": "notifications/resources/updated",
              "params": {
                "uri": "file:///docs/guide.md",
                "_meta": {
                  "etag": "W/\"42\""
                }
              }
            }
        """.trimIndent()

        val notification = verifyDeserialization<ResourceUpdatedNotification>(McpJson, json)
        val params = notification.params

        assertEquals(Method.Defined.NotificationsResourcesUpdated, notification.method)
        assertEquals("file:///docs/guide.md", params.uri)
        assertEquals("W/\"42\"", params.meta?.get("etag")?.jsonPrimitive?.content)
    }

    @Test
    fun `should serialize PromptListChangedNotification with meta`() {
        val notification = PromptListChangedNotification(
            BaseNotificationParams(
                meta = buildJsonObject { put("reason", "catalog-updated") },
            ),
        )

        verifySerialization(
            notification,
            McpJson,
            """
            {
              "method": "notifications/prompts/list_changed",
              "params": {
                "_meta": {
                  "reason": "catalog-updated"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize PromptListChangedNotification without params`() {
        val json = """
            {
              "method": "notifications/prompts/list_changed"
            }
        """.trimIndent()

        val notification = verifyDeserialization<PromptListChangedNotification>(McpJson, json)

        assertEquals(Method.Defined.NotificationsPromptsListChanged, notification.method)
        assertNull(notification.params)
    }

    @Test
    fun `should serialize ResourceListChangedNotification with meta`() {
        val notification = ResourceListChangedNotification(
            BaseNotificationParams(
                meta = buildJsonObject { put("reason", "subscription-updated") },
            ),
        )

        verifySerialization(
            notification,
            McpJson,
            """
            {
              "method": "notifications/resources/list_changed",
              "params": {
                "_meta": {
                  "reason": "subscription-updated"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize RootsListChangedNotification with meta`() {
        val notification = RootsListChangedNotification(
            BaseNotificationParams(
                meta = buildJsonObject { put("reason", "workspace-moved") },
            ),
        )

        verifySerialization(
            notification,
            McpJson,
            """
            {
              "method": "notifications/roots/list_changed",
              "params": {
                "_meta": {
                  "reason": "workspace-moved"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ToolListChangedNotification with meta`() {
        val notification = ToolListChangedNotification(
            BaseNotificationParams(
                meta = buildJsonObject { put("reason", "tool-added") },
            ),
        )

        verifySerialization(
            notification,
            McpJson,
            """
            {
              "method": "notifications/tools/list_changed",
              "params": {
                "_meta": {
                  "reason": "tool-added"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize TaskStatusNotification with all fields`() {
        val notification = TaskStatusNotification(
            TaskStatusNotificationParams(
                taskId = "task-1",
                status = TaskStatus.Working,
                statusMessage = "Processing data",
                createdAt = "2025-01-01T00:00:00Z",
                lastUpdatedAt = "2025-01-01T00:01:00Z",
                ttl = 60000.0,
                pollInterval = 5000.0,
                meta = buildJsonObject { put("source", "worker-1") },
            ),
        )

        verifySerialization(
            notification,
            McpJson,
            """
            {
              "method": "notifications/tasks/status",
              "params": {
                "taskId": "task-1",
                "status": "working",
                "statusMessage": "Processing data",
                "createdAt": "2025-01-01T00:00:00Z",
                "lastUpdatedAt": "2025-01-01T00:01:00Z",
                "ttl": 60000.0,
                "pollInterval": 5000.0,
                "_meta": {
                  "source": "worker-1"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize TaskStatusNotification with minimal fields`() {
        val notification = TaskStatusNotification(
            TaskStatusNotificationParams(
                taskId = "task-2",
                status = TaskStatus.Completed,
                createdAt = "2025-01-01T00:00:00Z",
                lastUpdatedAt = "2025-01-01T00:02:00Z",
                ttl = null,
            ),
        )

        verifySerialization(
            notification,
            McpJson,
            """
            {
              "method": "notifications/tasks/status",
              "params": {
                "taskId": "task-2",
                "status": "completed",
                "createdAt": "2025-01-01T00:00:00Z",
                "lastUpdatedAt": "2025-01-01T00:02:00Z"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize TaskStatusNotification`() {
        val json = """
            {
              "method": "notifications/tasks/status",
              "params": {
                "taskId": "task-3",
                "status": "failed",
                "statusMessage": "Connection lost",
                "createdAt": "2025-01-01T00:00:00Z",
                "lastUpdatedAt": "2025-01-01T00:03:00Z",
                "ttl": 30000.0,
                "pollInterval": 1000.0
              }
            }
        """.trimIndent()

        val notification = verifyDeserialization<TaskStatusNotification>(McpJson, json)
        val params = notification.params!!

        assertEquals(Method.Defined.NotificationsTasksStatus, notification.method)
        assertEquals("task-3", params.taskId)
        assertEquals(TaskStatus.Failed, params.status)
        assertEquals("Connection lost", params.statusMessage)
        assertEquals("2025-01-01T00:00:00Z", params.createdAt)
        assertEquals("2025-01-01T00:03:00Z", params.lastUpdatedAt)
        assertEquals(30000.0, params.ttl)
        assertEquals(1000.0, params.pollInterval)
        assertNull(params.meta)
    }

    @Test
    fun `should deserialize TaskStatusNotification without params`() {
        val json = """
            {
              "method": "notifications/tasks/status"
            }
        """.trimIndent()

        val notification = verifyDeserialization<TaskStatusNotification>(McpJson, json)

        assertEquals(Method.Defined.NotificationsTasksStatus, notification.method)
        assertNull(notification.params)
    }
}
