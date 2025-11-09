package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
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

        val json = McpJson.encodeToString(notification)

        json shouldEqualJson """
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
        """.trimIndent()
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

        val notification = McpJson.decodeFromString<CancelledNotification>(json)
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

        val json = McpJson.encodeToString(notification)

        json shouldEqualJson """
            {
              "method": "notifications/initialized",
              "params": {
                "_meta": {
                  "readyAt": "2025-01-12T15:00:58Z"
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize InitializedNotification without params`() {
        val json = """
            {
              "method": "notifications/initialized"
            }
        """.trimIndent()

        val notification = McpJson.decodeFromString<InitializedNotification>(json)

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

        val json = McpJson.encodeToString(notification)

        json shouldEqualJson """
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
        """.trimIndent()
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

        val notification = McpJson.decodeFromString<ProgressNotification>(json)
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

        val json = McpJson.encodeToString(notification)

        json shouldEqualJson """
            {
              "method": "notifications/resources/updated",
              "params": {
                "uri": "file:///workspace/README.md",
                "_meta": {
                  "checksum": "abcd1234"
                }
              }
            }
        """.trimIndent()
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

        val notification = McpJson.decodeFromString<ResourceUpdatedNotification>(json)
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

        val json = McpJson.encodeToString(notification)

        json shouldEqualJson """
            {
              "method": "notifications/prompts/list_changed",
              "params": {
                "_meta": {
                  "reason": "catalog-updated"
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize PromptListChangedNotification without params`() {
        val json = """
            {
              "method": "notifications/prompts/list_changed"
            }
        """.trimIndent()

        val notification = McpJson.decodeFromString<PromptListChangedNotification>(json)

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

        val json = McpJson.encodeToString(notification)

        json shouldEqualJson """
            {
              "method": "notifications/resources/list_changed",
              "params": {
                "_meta": {
                  "reason": "subscription-updated"
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize RootsListChangedNotification with meta`() {
        val notification = RootsListChangedNotification(
            BaseNotificationParams(
                meta = buildJsonObject { put("reason", "workspace-moved") },
            ),
        )

        val json = McpJson.encodeToString(notification)

        json shouldEqualJson """
            {
              "method": "notifications/roots/list_changed",
              "params": {
                "_meta": {
                  "reason": "workspace-moved"
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize ToolListChangedNotification with meta`() {
        val notification = ToolListChangedNotification(
            BaseNotificationParams(
                meta = buildJsonObject { put("reason", "tool-added") },
            ),
        )

        val json = McpJson.encodeToString(notification)

        json shouldEqualJson """
            {
              "method": "notifications/tools/list_changed",
              "params": {
                "_meta": {
                  "reason": "tool-added"
                }
              }
            }
        """.trimIndent()
    }
}
