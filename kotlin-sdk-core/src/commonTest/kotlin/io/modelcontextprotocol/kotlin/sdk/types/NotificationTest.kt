package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
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

        verifySerialization<Notification>(
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

        val notification = verifyDeserialization<Notification>(McpJson, json)

        notification.shouldBeInstanceOf<CancelledNotification>().params.requestId shouldBe RequestId(42)
    }

    @Test
    fun `should round-trip notifications through the polymorphic Notification serializer`() {
        val params = BaseNotificationParams(buildJsonObject { put("source", "test") })

        fun withParams(method: String) = """{"method": "$method", "params": {"_meta": {"source": "test"}}}"""

        listOf<Pair<Notification, String>>(
            InitializedNotification() to """{"method": "notifications/initialized"}""",
            InitializedNotification(params) to withParams("notifications/initialized"),
            PromptListChangedNotification(params) to withParams("notifications/prompts/list_changed"),
            ResourceListChangedNotification(params) to withParams("notifications/resources/list_changed"),
            RootsListChangedNotification(params) to withParams("notifications/roots/list_changed"),
            ToolListChangedNotification(params) to withParams("notifications/tools/list_changed"),
            CustomNotification(Method.Custom("com.example/event"), params) to withParams("com.example/event"),
        ).forEach { (notification, json) ->
            withClue(json) {
                verifySerialization(notification, McpJson, json)
            }
        }
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

        verifySerialization<Notification>(
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

        val notification = verifyDeserialization<Notification>(McpJson, json)

        notification.shouldBeInstanceOf<ProgressNotification>().params.progressToken shouldBe ProgressToken(7)
    }

    @Test
    fun `should serialize ResourceUpdatedNotification with meta`() {
        val notification = ResourceUpdatedNotification(
            ResourceUpdatedNotificationParams(
                uri = "file:///workspace/README.md",
                meta = buildJsonObject { put("checksum", "abcd1234") },
            ),
        )

        verifySerialization<Notification>(
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
    fun `should serialize ElicitationCompleteNotification with meta`() {
        val notification = ElicitationCompleteNotification(
            ElicitationCompleteNotificationParams(
                elicitationId = "elicit-42",
                meta = buildJsonObject { put("source", "oauth-flow") },
            ),
        )

        verifySerialization<Notification>(
            notification,
            McpJson,
            """
            {
              "method": "notifications/elicitation/complete",
              "params": {
                "elicitationId": "elicit-42",
                "_meta": {
                  "source": "oauth-flow"
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
                ttl = 60000,
                pollInterval = 5000,
                meta = buildJsonObject { put("source", "worker-1") },
            ),
        )

        verifySerialization<Notification>(
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
                "ttl": 60000,
                "pollInterval": 5000,
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
