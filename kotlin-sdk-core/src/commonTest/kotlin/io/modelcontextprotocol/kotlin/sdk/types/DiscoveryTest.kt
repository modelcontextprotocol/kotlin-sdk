package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalMcpApi::class)
class DiscoveryTest {

    @Test
    fun `should decode discovery request without optional client info`() {
        val request = McpJson.decodeFromString<Request>(
            """
            {
              "method": "server/discover",
              "params": {
                "_meta": {
                  "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                  "io.modelcontextprotocol/clientCapabilities": {},
                  "com.example/traceId": "trace-123"
                }
              }
            }
            """.trimIndent(),
        )

        val discover = assertIs<DiscoverRequest>(request)
        assertEquals("2026-07-28", discover.meta.protocolVersion)
        assertEquals(ClientCapabilities(), discover.meta.clientCapabilities)
        assertNull(discover.meta.clientInfo)
        assertNotNull(discover.meta["com.example/traceId"])
        McpJson.encodeToString<Request>(discover) shouldEqualJson """
            {
              "method": "server/discover",
              "params": {
                "_meta": {
                  "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                  "io.modelcontextprotocol/clientCapabilities": {},
                  "com.example/traceId": "trace-123"
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should reject discovery requests without required request metadata`() {
        val invalidParams = listOf(
            "{}" to "_meta",
            """{"_meta": {}}""" to RequestMetaKeys.PROTOCOL_VERSION,
            """
                {
                  "_meta": {
                    "${RequestMetaKeys.PROTOCOL_VERSION}": "2026-07-28",
                    "${RequestMetaKeys.CLIENT_INFO}": {
                      "name": "test-client",
                      "version": "1.0.0"
                    }
                  }
                }
            """.trimIndent() to RequestMetaKeys.CLIENT_CAPABILITIES,
        )

        invalidParams.forEach { (params, missingField) ->
            val failure = assertFails {
                McpJson.decodeFromString<Request>(
                    """{"method": "server/discover", "params": $params}""",
                )
            }

            assertTrue(
                failure.message.orEmpty().contains(missingField),
                "Expected failure to identify missing field $missingField, but was: $failure",
            )
        }
    }

    @Test
    fun `should encode required defaults independently of McpJson`() {
        val result = DiscoverResult(
            supportedVersions = listOf("2026-07-28"),
            capabilities = ServerCapabilities(),
        )

        Json.encodeToString(result) shouldEqualJson """
            {
              "supportedVersions": ["2026-07-28"],
              "capabilities": {},
              "resultType": "complete",
              "ttlMs": 0,
              "cacheScope": "private"
            }
        """.trimIndent()
    }

    @Test
    fun `should reject discovery results without required cache fields`() {
        val invalidResults = listOf(
            """
            {
              "supportedVersions": ["2026-07-28"],
              "capabilities": {},
              "cacheScope": "private"
            }
            """.trimIndent(),
            """
            {
              "supportedVersions": ["2026-07-28"],
              "capabilities": {},
              "ttlMs": 0
            }
            """.trimIndent(),
        )

        invalidResults.forEach { wire ->
            assertFails { McpJson.decodeFromString<DiscoverResult>(wire) }
            assertFails { McpJson.decodeFromString<ServerResult>(wire) }
        }
    }

    @Test
    fun `should use complete when a discovery result omits resultType`() {
        val result = McpJson.decodeFromString<ServerResult>(
            """
            {
              "supportedVersions": ["2026-07-28"],
              "capabilities": {},
              "ttlMs": 0,
              "cacheScope": "private"
            }
            """.trimIndent(),
        )

        assertEquals(COMPLETE_RESULT_TYPE, assertIs<DiscoverResult>(result).resultType)
    }

    @Test
    fun `should deserialize discovery results polymorphically`() {
        val wire =
            """
            {
              "supportedVersions": ["2026-07-28"],
              "capabilities": {},
              "instructions": "Use tools intentionally.",
              "resultType": "complete",
              "ttlMs": 250,
              "cacheScope": "public",
              "_meta": {
                "io.modelcontextprotocol/serverInfo": {
                  "name": "server",
                  "version": "1"
                },
                "com.example/source": "edge"
              }
            }
            """.trimIndent()
        val result = McpJson.decodeFromString<ServerResult>(wire)

        val discover = assertIs<DiscoverResult>(result)
        McpJson.encodeToString<ServerResult>(discover) shouldEqualJson wire
    }

    @Test
    fun `should not treat supportedVersions alone as a discovery discriminator`() {
        val result = McpJson.decodeFromString<ServerResult>(
            """
            {
              "tools": [],
              "supportedVersions": ["extension-value"]
            }
            """.trimIndent(),
        )

        assertIs<ListToolsResult>(result)
    }

    @Test
    fun `should not treat an initialize extension as a discovery result`() {
        val result = McpJson.decodeFromString<ServerResult>(
            """
            {
              "protocolVersion": "2025-06-18",
              "capabilities": {},
              "serverInfo": {
                "name": "test-server",
                "version": "1.0.0"
              },
              "supportedVersions": ["extension-value"]
            }
            """.trimIndent(),
        )

        assertIs<InitializeResult>(result)
    }

    @Test
    fun `should reject a negative discovery ttl`() {
        assertFailsWith<IllegalArgumentException> {
            DiscoverResult(
                supportedVersions = listOf("2026-07-28"),
                capabilities = ServerCapabilities(),
                ttlMs = -1,
            )
        }
    }

    @Test
    fun `should serialize unsupported protocol version data`() {
        verifySerialization(
            UnsupportedProtocolVersionData(supported = listOf("2026-07-28", "2025-11-25"), requested = "2099-01-01"),
            McpJson,
            """{"supported": ["2026-07-28", "2025-11-25"], "requested": "2099-01-01"}""",
        )
    }
}
