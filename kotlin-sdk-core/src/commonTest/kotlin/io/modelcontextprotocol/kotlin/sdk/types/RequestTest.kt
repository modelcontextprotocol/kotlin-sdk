package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RequestTest {

    @OptIn(ExperimentalMcpApi::class)
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
        val envelope = meta.toEnvelope()
        envelope.protocolVersion shouldBe "2026-07-28"
        envelope.clientInfo shouldBe Implementation(name = "wire-client", version = "1.2.3")
        assertNotNull(envelope.clientCapabilities.sampling)
        envelope.logLevel shouldBe LoggingLevel.Warning
        meta["com.example/traceId"]?.jsonPrimitive?.content shouldBe "trace-123"
        assertNotNull(meta.json[CLIENT_CAPABILITIES_META_KEY]?.let { it as? JsonObject })
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
