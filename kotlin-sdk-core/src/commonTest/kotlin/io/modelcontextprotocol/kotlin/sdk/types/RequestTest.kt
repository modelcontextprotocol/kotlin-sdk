package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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

    @Test
    fun `should expose progress token from RequestMeta`() {
        RequestMeta(buildJsonObject { put("progressToken", "sync-1") }).progressToken shouldBe ProgressToken("sync-1")
        RequestMeta(buildJsonObject { put("progressToken", 7) }).progressToken shouldBe ProgressToken(7)
        RequestMeta(buildJsonObject { put("progressToken", EmptyJsonObject) }).progressToken.shouldBeNull()
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
    fun `should round-trip unknown method as CustomRequest`() {
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

        val custom = McpJson.decodeFromString<Request>(json).shouldBeInstanceOf<CustomRequest>()

        custom.method shouldBe Method.Custom("extensions/customAction")
        custom.params?.meta?.progressToken shouldBe ProgressToken("custom-1")
        McpJson.encodeToString<Request>(custom) shouldEqualJson json
    }
}
