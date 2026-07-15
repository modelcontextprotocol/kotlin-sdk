package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@OptIn(ExperimentalMcpApi::class)
class DiscoveryTest {

    @Test
    fun `should decode server discover request from its wire shape`() {
        val request = McpJson.decodeFromString<Request>(
            """
            {
              "method": "server/discover",
              "params": {
                "_meta": {
                  "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                  "io.modelcontextprotocol/clientInfo": {
                    "name": "test-client",
                    "version": "1.0.0"
                  },
                  "io.modelcontextprotocol/clientCapabilities": {}
                }
              }
            }
            """.trimIndent(),
        )

        val discover = assertIs<DiscoverRequest>(request)
        assertEquals(Method.Defined.ServerDiscover, discover.method)
        assertEquals("2026-07-28", discover.meta?.protocolVersion)
        assertEquals("test-client", discover.meta?.clientInfo?.name)
        assertEquals(ClientCapabilities(), discover.meta?.clientCapabilities)
    }

    @Test
    fun `should serialize every required discovery result field`() {
        val result = DiscoverResult(
            supportedVersions = listOf("2026-07-28"),
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
            serverInfo = Implementation(name = "test-server", version = "2.0.0"),
            instructions = "Use tools intentionally.",
        )

        McpJson.encodeToString<ServerResult>(result) shouldEqualJson """
            {
              "supportedVersions": ["2026-07-28"],
              "capabilities": {
                "tools": {"listChanged": true}
              },
              "serverInfo": {
                "name": "test-server",
                "version": "2.0.0"
              },
              "instructions": "Use tools intentionally.",
              "resultType": "complete",
              "ttlMs": 0,
              "cacheScope": "private"
            }
        """.trimIndent()
    }

    @Test
    fun `should encode required defaults independently of McpJson`() {
        val result = DiscoverResult(
            supportedVersions = listOf("2026-07-28"),
            capabilities = ServerCapabilities(),
            serverInfo = Implementation(name = "test-server", version = "2.0.0"),
        )

        Json.encodeToString(result) shouldEqualJson """
            {
              "supportedVersions": ["2026-07-28"],
              "capabilities": {},
              "serverInfo": {
                "name": "test-server",
                "version": "2.0.0"
              },
              "resultType": "complete",
              "ttlMs": 0,
              "cacheScope": "private"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize discovery results polymorphically`() {
        val result = McpJson.decodeFromString<ServerResult>(
            """
            {
              "supportedVersions": ["2026-07-28"],
              "capabilities": {},
              "serverInfo": {"name": "server", "version": "1"},
              "resultType": "complete",
              "ttlMs": 250,
              "cacheScope": "public",
              "_meta": {"com.example/source": "edge"}
            }
            """.trimIndent(),
        )

        val discover = assertIs<DiscoverResult>(result)
        assertEquals(250, discover.ttlMs)
        assertEquals(CacheScope.Public, discover.cacheScope)
        assertNotNull(discover.meta?.get("com.example/source"))
    }

    @Test
    fun `should reject a negative discovery ttl`() {
        assertFailsWith<IllegalArgumentException> {
            DiscoverResult(
                supportedVersions = listOf("2026-07-28"),
                capabilities = ServerCapabilities(),
                serverInfo = Implementation(name = "server", version = "1"),
                ttlMs = -1,
            )
        }
    }

    @Test
    fun `unsupported version data should round trip without losing versions`() {
        val data = UnsupportedProtocolVersionData(
            supported = listOf("2026-07-28", "2025-11-25"),
            requested = "2099-01-01",
        )

        val encoded = McpJson.encodeToString(data)
        assertEquals(data, McpJson.decodeFromString(encoded))
        assertEquals(-32022, RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION)
    }
}
