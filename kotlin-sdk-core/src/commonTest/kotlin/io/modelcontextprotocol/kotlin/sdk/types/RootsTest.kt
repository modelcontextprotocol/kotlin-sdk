package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

class RootsTest {

    @Test
    fun `should serialize Root with name and meta`() {
        val root = Root(
            uri = "file:///workspace/docs",
            name = "Docs",
            meta = buildJsonObject { put("writable", true) },
        )

        verifySerialization(
            root,
            McpJson,
            """
            {
              "uri": "file:///workspace/docs",
              "name": "Docs",
              "_meta": {
                "writable": true
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ListRootsRequest with meta`() {
        val request = ListRootsRequest(
            BaseRequestParams(
                meta = RequestMeta(
                    buildJsonObject { put("progressToken", "roots-list-1") },
                ),
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "roots/list",
              "params": {
                "_meta": {
                  "progressToken": "roots-list-1"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ListRootsResult`() {
        val result = ListRootsResult(
            roots = listOf(
                Root(uri = "file:///workspace/project", name = "Project"),
                Root(uri = "file:///workspace/docs", name = "Docs"),
            ),
            meta = buildJsonObject { put("issuedAt", "2025-01-12T15:00:58Z") },
        )

        verifySerialization<ClientResult>(
            result,
            McpJson,
            """
            {
              "roots": [
                {"uri": "file:///workspace/project", "name": "Project"},
                {"uri": "file:///workspace/docs", "name": "Docs"}
              ],
              "_meta": {
                "issuedAt": "2025-01-12T15:00:58Z"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should reject non file URI roots`() {
        shouldThrow<IllegalArgumentException> {
            McpJson.decodeFromString<Root>("""{"uri": "https://example.com/root"}""")
        }.message shouldContain "Root URI must start with 'file://'"
    }
}
