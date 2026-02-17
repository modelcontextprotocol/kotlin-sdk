package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RootsTest {

    @Test
    fun `should serialize Root with minimal fields`() {
        val root = Root(uri = "file:///workspace/project")

        verifySerialization(
            root,
            McpJson,
            """
            {
              "uri": "file:///workspace/project"
            }
            """.trimIndent(),
        )
    }

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
    fun `should deserialize Root with validation`() {
        val json = """
            {
              "uri": "file:///workspace/code",
              "name": "Code",
              "_meta": {
                "default": true
              }
            }
        """.trimIndent()

        val root = verifyDeserialization<Root>(McpJson, json)

        assertEquals("file:///workspace/code", root.uri)
        assertEquals("Code", root.name)
        assertEquals(true, root.meta?.get("default")?.jsonPrimitive?.boolean)
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
    fun `should serialize ListRootsRequest without params`() {
        val request = ListRootsRequest()

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "roots/list"
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

        verifySerialization(
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
    fun `should deserialize ListRootsResult`() {
        val json = """
            {
              "roots": [
                {
                  "uri": "file:///workspace/code",
                  "name": "Code"
                }
              ],
              "_meta": {
                "updatedBy": "client-1"
              }
            }
        """.trimIndent()

        val result = verifyDeserialization<ListRootsResult>(McpJson, json)

        val roots = result.roots
        assertEquals(1, roots.size)
        val root = roots.first()
        assertEquals("file:///workspace/code", root.uri)
        assertEquals("Code", root.name)
        val meta = result.meta
        assertNotNull(meta)
        assertEquals("client-1", meta["updatedBy"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should reject non file URI roots`() {
        val json = """
            {
              "uri": "https://example.com/root"
            }
        """.trimIndent()

        val exception = runCatching { McpJson.decodeFromString<Root>(json) }.exceptionOrNull()
        assertNotNull(exception)
        assertEquals(true, exception.message?.contains("Root URI must start with 'file://'"))
    }
}
