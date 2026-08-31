package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.InternalMcpApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalMcpApi::class, InternalMcpApi::class)
class WireTest {

    private val serverInfo = Implementation(name = "wire-server", version = "3.1.0")

    private fun encoded(result: RequestResult): JsonObject =
        McpJson.encodeToJsonElement<RequestResult>(result).jsonObject

    @Test
    fun `the handshake wire should carry nothing the request-scoped lifecycle added`() {
        val result = ListToolsResult(tools = emptyList(), ttlMs = 500, cacheScope = CacheScope.Public)

        val wire = encodeOutboundResult(ProtocolEra.Legacy, encoded(result), serverInfo)

        wire.toString() shouldEqualJson """{"tools": []}"""
    }

    @Test
    fun `stripping should leave everything else untouched`() {
        val result = ListToolsResult(
            tools = emptyList(),
            nextCursor = "cursor-9",
            meta = ResultMeta(buildJsonObject { put("page", 2) }),
        )

        val wire = encodeOutboundResult(ProtocolEra.Legacy, encoded(result), serverInfo)

        wire.toString() shouldEqualJson """
            {
              "tools": [],
              "nextCursor": "cursor-9",
              "_meta": {"page": 2}
            }
        """.trimIndent()
    }

    @Test
    fun `the handshake wire should never carry a server identity`() {
        // Legacy peers learned who the server is from the handshake, and never expected it here.
        val wire = encodeOutboundResult(ProtocolEra.Legacy, encoded(EmptyResult()), serverInfo)

        assertNull(wire["_meta"])
    }

    @Test
    fun `a request-scoped result should identify the server that produced it`() {
        val wire = encodeOutboundResult(ProtocolEra.Modern, encoded(EmptyResult()), serverInfo)

        ResultMeta(wire["_meta"]!!.jsonObject).serverInfo shouldBe serverInfo
    }

    @Test
    fun `an identity the handler wrote should outrank the automatic one`() {
        val authored = Implementation(name = "authored", version = "9.9.9")
        val result = CallToolResult(content = emptyList(), meta = null.withServerInfo(authored))

        val wire = encodeOutboundResult(ProtocolEra.Modern, encoded(result), serverInfo)

        ResultMeta(wire["_meta"]!!.jsonObject).serverInfo shouldBe authored
    }

    @Test
    fun `a server configured not to identify itself should stamp nothing`() {
        val wire = encodeOutboundResult(ProtocolEra.Modern, encoded(EmptyResult()), serverInfo = null)

        assertNull(wire["_meta"])
    }

    @Test
    fun `stamping should preserve unrelated result metadata`() {
        val result = CallToolResult(
            content = emptyList(),
            meta = ResultMeta(buildJsonObject { put("com.example/trace", "abc") }),
        )

        val wire = encodeOutboundResult(ProtocolEra.Modern, encoded(result), serverInfo)

        val meta = ResultMeta(wire["_meta"]!!.jsonObject)
        meta.serverInfo shouldBe serverInfo
        meta["com.example/trace"].toString() shouldBe "\"abc\""
    }

    @Test
    fun `a request-scoped result should keep the fields the lifecycle added`() {
        val result = ListToolsResult(tools = emptyList(), ttlMs = 500, cacheScope = CacheScope.Public)

        val wire = encodeOutboundResult(ProtocolEra.Modern, encoded(result), serverInfo = null)

        wire.toString() shouldEqualJson """
            {
              "tools": [],
              "resultType": "complete",
              "ttlMs": 500,
              "cacheScope": "public"
            }
        """.trimIndent()
    }

    @Test
    fun `a rendered result should read back as the result it was rendered from`() {
        // What a transport that serializes does for free, and what Protocol does for one that does not.
        val result = ListToolsResult(tools = emptyList(), nextCursor = "cursor-1")

        val wire = WireResult(encodeOutboundResult(ProtocolEra.Legacy, encoded(result), serverInfo = null))
        val readBack = McpJson.decodeFromString<RequestResult>(McpJson.encodeToString(wire))

        assertIs<ListToolsResult>(readBack)
        readBack shouldBe result
    }

    @Test
    fun `a rendered result should serialize as the object it holds and nothing more`() {
        val wire = WireResult(buildJsonObject { put("tools", McpJson.encodeToJsonElement(emptyList<Tool>())) })

        McpJson.encodeToString<RequestResult>(wire) shouldEqualJson """{"tools": []}"""
        assertNull(wire.meta)
    }

    @Test
    fun `a retired error code should never reach the wire`() {
        @Suppress("DEPRECATION")
        ProtocolEra.entries.forEach { era ->
            outboundErrorCode(era, RPCError.ErrorCode.RESOURCE_NOT_FOUND) shouldBe
                RPCError.ErrorCode.INVALID_PARAMS
        }
    }

    @Test
    fun `url elicitation should survive only where url elicitation does`() {
        @Suppress("DEPRECATION")
        val urlElicitationRequired = RPCError.ErrorCode.URL_ELICITATION_REQUIRED

        outboundErrorCode(ProtocolEra.Legacy, urlElicitationRequired) shouldBe urlElicitationRequired
        outboundErrorCode(ProtocolEra.Modern, urlElicitationRequired) shouldBe
            RPCError.ErrorCode.INTERNAL_ERROR
    }

    @Test
    fun `every other error code should pass through unchanged`() {
        val untouched = listOf(
            RPCError.ErrorCode.INVALID_PARAMS,
            RPCError.ErrorCode.INTERNAL_ERROR,
            RPCError.ErrorCode.METHOD_NOT_FOUND,
            RPCError.ErrorCode.HEADER_MISMATCH,
            RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY,
            RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION,
            RPCError.ErrorCode.CONNECTION_CLOSED,
        )

        ProtocolEra.entries.forEach { era ->
            untouched.forEach { code -> outboundErrorCode(era, code) shouldBe code }
        }
    }
}
