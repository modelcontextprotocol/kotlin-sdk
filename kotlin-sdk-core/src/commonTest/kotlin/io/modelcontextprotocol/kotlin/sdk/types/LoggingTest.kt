package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoggingTest {

    @Test
    fun `should serialize LoggingLevel to expected schema values`() {
        assertEquals("\"debug\"", McpJson.encodeToString(LoggingLevel.Debug))
        assertEquals("\"info\"", McpJson.encodeToString(LoggingLevel.Info))
        assertEquals("\"notice\"", McpJson.encodeToString(LoggingLevel.Notice))
        assertEquals("\"warning\"", McpJson.encodeToString(LoggingLevel.Warning))
        assertEquals("\"error\"", McpJson.encodeToString(LoggingLevel.Error))
        assertEquals("\"critical\"", McpJson.encodeToString(LoggingLevel.Critical))
        assertEquals("\"alert\"", McpJson.encodeToString(LoggingLevel.Alert))
        assertEquals("\"emergency\"", McpJson.encodeToString(LoggingLevel.Emergency))
    }

    @Test
    fun `should deserialize LoggingLevel from schema values`() {
        assertEquals(LoggingLevel.Debug, McpJson.decodeFromString<LoggingLevel>("\"debug\""))
        assertEquals(LoggingLevel.Info, McpJson.decodeFromString<LoggingLevel>("\"info\""))
        assertEquals(LoggingLevel.Notice, McpJson.decodeFromString<LoggingLevel>("\"notice\""))
        assertEquals(LoggingLevel.Warning, McpJson.decodeFromString<LoggingLevel>("\"warning\""))
        assertEquals(LoggingLevel.Error, McpJson.decodeFromString<LoggingLevel>("\"error\""))
        assertEquals(LoggingLevel.Critical, McpJson.decodeFromString<LoggingLevel>("\"critical\""))
        assertEquals(LoggingLevel.Alert, McpJson.decodeFromString<LoggingLevel>("\"alert\""))
        assertEquals(LoggingLevel.Emergency, McpJson.decodeFromString<LoggingLevel>("\"emergency\""))
    }

    @Test
    fun `should serialize SetLevelRequest with meta`() {
        val request = SetLevelRequest(
            SetLevelRequestParams(
                level = LoggingLevel.Warning,
                meta = RequestMeta(
                    buildJsonObject { put("progressToken", "log-42") },
                ),
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "logging/setLevel",
              "params": {
                "level": "warning",
                "_meta": {
                  "progressToken": "log-42"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize SetLevelRequest with numeric progress token`() {
        val json = """
            {
              "method": "logging/setLevel",
              "params": {
                "level": "alert",
                "_meta": {
                  "progressToken": 1001
                }
              }
            }
        """.trimIndent()

        val request = verifyDeserialization<SetLevelRequest>(McpJson, json)
        assertEquals(Method.Defined.LoggingSetLevel, request.method)
        assertEquals(LoggingLevel.Alert, request.params.level)
        assertEquals(ProgressToken(1001), request.params.meta?.progressToken)
    }

    @Test
    fun `should serialize LoggingMessageNotification with logger`() {
        val notification = LoggingMessageNotification(
            LoggingMessageNotificationParams(
                level = LoggingLevel.Error,
                data = buildJsonObject { put("message", "Disk space critically low") },
                logger = "infra.monitor",
                meta = buildJsonObject { put("source", "node-1") },
            ),
        )

        verifySerialization(
            notification,
            McpJson,
            """
            {
              "method": "notifications/message",
              "params": {
                "level": "error",
                "data": {
                  "message": "Disk space critically low"
                },
                "logger": "infra.monitor",
                "_meta": {
                  "source": "node-1"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize LoggingMessageNotification with text data`() {
        val json = """
            {
              "method": "notifications/message",
              "params": {
                "level": "info",
                "data": "Service started successfully"
              }
            }
        """.trimIndent()

        val notification = verifyDeserialization<LoggingMessageNotification>(McpJson, json)
        assertEquals(Method.Defined.NotificationsMessage, notification.method)

        val params = notification.params
        assertEquals(LoggingLevel.Info, params.level)
        assertEquals("Service started successfully", params.data.jsonPrimitive.content)
        assertNull(params.logger)
        assertNull(params.meta)
    }

    @Test
    fun `should deserialize LoggingMessageNotification with structured data`() {
        val json = """
            {
              "method": "notifications/message",
              "params": {
                "level": "critical",
                "data": {
                  "code": "DB_CONN_FAIL",
                  "retry": false
                },
                "logger": "database.monitor",
                "_meta": {
                  "requestId": "req-77"
                }
              }
            }
        """.trimIndent()

        val notification = verifyDeserialization<LoggingMessageNotification>(McpJson, json)
        val params = notification.params
        assertEquals(LoggingLevel.Critical, params.level)
        val data = params.data.jsonObject
        assertEquals("DB_CONN_FAIL", data["code"]?.jsonPrimitive?.content)
        assertEquals(false, data["retry"]?.jsonPrimitive?.boolean)
        assertEquals("database.monitor", params.logger)
        val meta = params.meta
        assertEquals("req-77", meta?.get("requestId")?.jsonPrimitive?.content)
    }
}
