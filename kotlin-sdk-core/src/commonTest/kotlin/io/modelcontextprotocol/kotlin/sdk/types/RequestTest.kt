package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RequestTest {

    @Test
    fun `should expose progress token from RequestMeta`() {
        val stringTokenMeta = RequestMeta(buildJsonObject { put("progressToken", "sync-1") })
        val numberTokenMeta = RequestMeta(buildJsonObject { put("progressToken", 7) })
        val customMeta = RequestMeta(
            buildJsonObject {
                put("progressToken", "ignored")
                put("source", "client")
            },
        )

        assertEquals(ProgressToken("sync-1"), stringTokenMeta.progressToken)
        assertEquals(ProgressToken(7), numberTokenMeta.progressToken)
        assertEquals("client", customMeta["source"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should serialize BaseRequestParams with meta`() {
        val params = BaseRequestParams(
            meta = RequestMeta(
                buildJsonObject {
                    put("progressToken", "base-42")
                    put("origin", "test-suite")
                },
            ),
        )

        val json = McpJson.encodeToString(params)

        json shouldEqualJson """
            {
              "_meta": {
                "progressToken": "base-42",
                "origin": "test-suite"
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize BaseRequestParams with numeric progress token`() {
        val json = """
            {
              "_meta": {
                "progressToken": 501,
                "latencyMs": 12.5
              }
            }
        """.trimIndent()

        val params = McpJson.decodeFromString<BaseRequestParams>(json)

        val meta = params.meta
        assertNotNull(meta)
        assertEquals(ProgressToken(501), meta.progressToken)
        assertEquals(12.5, meta["latencyMs"]?.jsonPrimitive?.double)
    }

    @Test
    fun `should serialize PaginatedRequestParams with cursor and meta`() {
        val params = PaginatedRequestParams(
            cursor = "cursor-1",
            meta = RequestMeta(buildJsonObject { put("progressToken", "page-req") }),
        )

        val json = McpJson.encodeToString(params)

        json shouldEqualJson """
            {
              "cursor": "cursor-1",
              "_meta": {
                "progressToken": "page-req"
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize PaginatedRequestParams`() {
        val json = """
            {
              "cursor": "cursor-2",
              "_meta": {
                "progressToken": 99
              }
            }
        """.trimIndent()

        val params = McpJson.decodeFromString<PaginatedRequestParams>(json)

        assertEquals("cursor-2", params.cursor)
        assertEquals(ProgressToken(99), params.meta?.progressToken)
    }

    @Test
    fun `should serialize CustomRequest with params`() {
        val request = CustomRequest(
            method = Method.Custom("workspace/sync"),
            params = BaseRequestParams(
                meta = RequestMeta(buildJsonObject { put("progressToken", "sync-req") }),
            ),
        )

        val json = McpJson.encodeToString<Request>(request)

        json shouldEqualJson """
            {
              "method": "workspace/sync",
              "params": {
                "_meta": {
                  "progressToken": "sync-req"
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize unknown method into CustomRequest`() {
        val json = """
            {
              "method": "extensions/customAction",
              "params": {
                "_meta": {
                  "progressToken": "custom-1"
                }
              }
            }
        """.trimIndent()

        val request = McpJson.decodeFromString<Request>(json)

        val custom = assertIs<CustomRequest>(request)
        val method = custom.method
        val customMethod = assertIs<Method.Custom>(method)
        assertEquals("extensions/customAction", customMethod.value)
        assertEquals("custom-1", custom.params?.meta?.json?.get("progressToken")?.jsonPrimitive?.content)
    }

    @Test
    fun `should serialize EmptyResult with meta`() {
        val result = EmptyResult(
            meta = buildJsonObject {
                put("processedBy", "worker-1")
            },
        )

        val json = McpJson.encodeToString(result)

        json shouldEqualJson """
            {
              "_meta": {
                "processedBy": "worker-1"
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize EmptyResult without meta`() {
        val json = "{}"

        val result = McpJson.decodeFromString<EmptyResult>(json)

        assertNull(result.meta)
    }
}
