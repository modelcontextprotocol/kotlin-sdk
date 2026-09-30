package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

class PingRequestTest {

    @Test
    fun `should serialize PingRequest with meta`() {
        val request = PingRequest(
            BaseRequestParams(
                meta = RequestMeta(
                    buildJsonObject { put("progressToken", "ping-42") },
                ),
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "ping",
              "params": {
                "_meta": {
                  "progressToken": "ping-42"
                }
              }
            }
            """.trimIndent(),
        )
    }
}
