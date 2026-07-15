package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestTest {

    @OptIn(ExperimentalMcpApi::class)
    @Suppress("DEPRECATION")
    @Test
    fun `should decode typed request metadata without discarding extensions`() {
        val request = McpJson.decodeFromString<Request>(
            """
            {
              "method": "tools/list",
              "params": {
                "_meta": {
                  "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                  "io.modelcontextprotocol/clientInfo": {
                    "name": "wire-client",
                    "version": "1.2.3"
                  },
                  "io.modelcontextprotocol/clientCapabilities": {
                    "sampling": {},
                    "com.example/custom-capability": {"enabled": true}
                  },
                  "io.modelcontextprotocol/logLevel": "warning",
                  "com.example/traceId": "trace-123"
                }
              }
            }
            """.trimIndent(),
        )

        val listTools = assertIs<ListToolsRequest>(request)
        val meta = assertNotNull(listTools.params?.meta)
        meta.protocolVersion shouldBe "2026-07-28"
        meta.clientInfo shouldBe Implementation(name = "wire-client", version = "1.2.3")
        assertNotNull(meta.clientCapabilities?.sampling)
        meta.logLevel shouldBe LoggingLevel.Warning
        meta["com.example/traceId"]?.jsonPrimitive?.content shouldBe "trace-123"
        assertNotNull(meta.json[RequestMetaKeys.CLIENT_CAPABILITIES]?.let { it as? JsonObject })
    }

    @OptIn(ExperimentalMcpApi::class)
    @Test
    fun `empty client capabilities should mean no optional capabilities`() {
        val json = Json.parseToJsonElement(
            """{"${RequestMetaKeys.CLIENT_CAPABILITIES}": {}}""",
        ) as JsonObject

        RequestMeta(json).clientCapabilities shouldBe ClientCapabilities()
    }

    @OptIn(ExperimentalMcpApi::class)
    @Suppress("DEPRECATION")
    @Test
    fun `typed request metadata should reject malformed fields`() {
        val malformedValues = listOf(
            RequestMetaKeys.PROTOCOL_VERSION to "{}",
            RequestMetaKeys.PROTOCOL_VERSION to "null",
            RequestMetaKeys.CLIENT_INFO to "\"not-an-implementation\"",
            RequestMetaKeys.CLIENT_INFO to "{\"name\":\"missing-version\"}",
            RequestMetaKeys.CLIENT_INFO to "null",
            RequestMetaKeys.CLIENT_CAPABILITIES to "\"not-capabilities\"",
            RequestMetaKeys.CLIENT_CAPABILITIES to "[]",
            RequestMetaKeys.CLIENT_CAPABILITIES to "null",
            RequestMetaKeys.LOG_LEVEL to "\"verbose\"",
            RequestMetaKeys.LOG_LEVEL to "7",
            RequestMetaKeys.LOG_LEVEL to "null",
        )

        malformedValues.forEach { (key, value) ->
            val json = Json.parseToJsonElement("""{"$key":$value}""") as JsonObject
            val meta = RequestMeta(json)

            val failure = assertFailsWith<SerializationException> {
                when (key) {
                    RequestMetaKeys.PROTOCOL_VERSION -> meta.protocolVersion
                    RequestMetaKeys.CLIENT_INFO -> meta.clientInfo
                    RequestMetaKeys.CLIENT_CAPABILITIES -> meta.clientCapabilities
                    else -> meta.logLevel
                }
            }
            assertTrue(failure.message.orEmpty().contains(key))
        }
    }

    @OptIn(ExperimentalMcpApi::class)
    @Test
    fun `legacy requests should not gain request scoped metadata`() {
        val request = ListToolsRequest()

        McpJson.encodeToString<Request>(request) shouldEqualJson """
            {
              "method": "tools/list"
            }
        """.trimIndent()
        assertNull(request.params?.meta?.protocolVersion)
    }

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

        verifySerialization(
            params,
            McpJson,
            """
            {
              "_meta": {
                "progressToken": "base-42",
                "origin": "test-suite"
              }
            }
            """.trimIndent(),
        )
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

        val params = verifyDeserialization<BaseRequestParams>(McpJson, json)

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

        verifySerialization(
            params,
            McpJson,
            """
            {
              "cursor": "cursor-1",
              "_meta": {
                "progressToken": "page-req"
              }
            }
            """.trimIndent(),
        )
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

        val params = verifyDeserialization<PaginatedRequestParams>(McpJson, json)

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
        custom.params?.meta?.json
            ?.get("progressToken")
            ?.jsonPrimitive?.content shouldBe "custom-1"
    }

    @Test
    fun `should serialize EmptyResult with meta`() {
        val result = EmptyResult(
            meta = buildJsonObject {
                put("processedBy", "worker-1")
            },
        )

        verifySerialization(
            result,
            McpJson,
            """
            {
              "_meta": {
                "processedBy": "worker-1"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize EmptyResult without meta`() {
        val json = "{}"

        val result = verifyDeserialization<EmptyResult>(McpJson, json)

        assertNull(result.meta)
    }
}
