package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

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

        notification.params.data shouldBe JsonPrimitive("Service started successfully")
    }
}
