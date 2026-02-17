package io.modelcontextprotocol.kotlin.sdk.models

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlin.test.Test

class ProgressNotificationsTest {

    /**
     * https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/progress#progress-flow
     */
    @Test
    fun `Read ProgressNotifications with string token`() {
        //language=json
        val json = """
            {
              "jsonrpc": "2.0",
              "method": "notifications/progress",
              "params": {
                "progressToken": "abc123",
                "progress": 50,
                "total": 100,
                "message": "Reticulating splines..."
              }
            }
        """.trimIndent()

        val result = McpJson.decodeFromString<ProgressNotification>(json)

        result shouldBe ProgressNotification(
            params = ProgressNotificationParams(
                progressToken = RequestId.StringId("abc123"),
                progress = 50.0,
                message = "Reticulating splines...",
                total = 100.0,
            ),
        )
    }

    /**
     * https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/progress#progress-flow
     */
    @Test
    fun `Read ProgressNotifications with integer token`() {
        //language=json
        val json = """
            {
              "jsonrpc": "2.0",
              "method": "notifications/progress",
              "params": {
                "progressToken": 100500,
                "progress": 50,
                "total": 100,
                "message": "Reticulating splines..."
              }
            }
        """.trimIndent()

        val result = McpJson.decodeFromString<ProgressNotification>(json)
        result shouldBe ProgressNotification(
            params = ProgressNotificationParams(
                progressToken = RequestId.NumberId(100500),
                progress = 50.0,
                message = "Reticulating splines...",
                total = 100.0,
            ),
        )
    }
}
