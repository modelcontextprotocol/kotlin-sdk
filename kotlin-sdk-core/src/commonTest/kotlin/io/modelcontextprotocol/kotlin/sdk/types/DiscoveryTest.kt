package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
        assertEquals("2026-07-28", discover.params.meta.toEnvelope().protocolVersion)
        assertEquals("test-client", discover.params.meta.toEnvelope().clientInfo?.name)
        assertEquals(ClientCapabilities(), discover.params.meta.toEnvelope().clientCapabilities)
    }

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
        assertEquals("2026-07-28", discover.params.meta.toEnvelope().protocolVersion)
        assertEquals(ClientCapabilities(), discover.params.meta.toEnvelope().clientCapabilities)
        assertNull(discover.params.meta.toEnvelope().clientInfo)
        assertNotNull(discover.params.meta["com.example/traceId"])
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
    fun `should reject discovery requests without request metadata`() {
        val failure = assertFails {
            McpJson.decodeFromString<Request>("""{"method": "server/discover", "params": {}}""")
        }

        assertTrue(
            failure.message.orEmpty().contains("_meta"),
            "Expected failure to identify the missing _meta, but was: $failure",
        )
    }

    @Test
    fun `should decode a discovery request whose envelope is incomplete and report it on read`() {
        val incompleteEnvelopes = listOf(
            "{}" to PROTOCOL_VERSION_META_KEY,
            """
                {
                  "$PROTOCOL_VERSION_META_KEY": "2026-07-28",
                  "$CLIENT_INFO_META_KEY": {
                    "name": "test-client",
                    "version": "1.0.0"
                  }
                }
            """.trimIndent() to CLIENT_CAPABILITIES_META_KEY,
        )

        incompleteEnvelopes.forEach { (meta, missingField) ->
            val request = McpJson.decodeFromString<Request>(
                """{"method": "server/discover", "params": {"_meta": $meta}}""",
            )

            val discover = assertIs<DiscoverRequest>(request)
            assertNull(discover.params.meta.toEnvelopeOrNull())
            val failure = assertFailsWith<SerializationException> { discover.params.meta.toEnvelope() }
            assertTrue(
                failure.message.orEmpty().contains(missingField),
                "Expected failure to identify missing field $missingField, but was: $failure",
            )
        }
    }

    @Test
    fun `should serialize every required discovery result field`() {
        val result = DiscoverResult(
            supportedVersions = listOf("2026-07-28"),
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
            instructions = "Use tools intentionally.",
        )

        McpJson.encodeToString<ServerResult>(result) shouldEqualJson """
            {
              "supportedVersions": ["2026-07-28"],
              "capabilities": {
                "tools": {"listChanged": true}
              },
              "instructions": "Use tools intentionally.",
              "resultType": "complete",
              "ttlMs": 0,
              "cacheScope": "private"
            }
        """.trimIndent()
    }

    @Test
    fun `should encode optional server info in result metadata`() {
        val result = DiscoverResult(
            supportedVersions = listOf("2026-07-28"),
            capabilities = ServerCapabilities(),
            meta = Json.parseToJsonElement(
                """
                {
                  "io.modelcontextprotocol/serverInfo": {
                    "name": "test-server",
                    "version": "2.0.0"
                  },
                  "com.example/source": "edge"
                }
                """.trimIndent(),
            ).jsonObject.let(::ResultMeta),
        )

        McpJson.encodeToString<ServerResult>(result) shouldEqualJson """
            {
              "supportedVersions": ["2026-07-28"],
              "capabilities": {},
              "resultType": "complete",
              "ttlMs": 0,
              "cacheScope": "private",
              "_meta": {
                "io.modelcontextprotocol/serverInfo": {
                  "name": "test-server",
                  "version": "2.0.0"
                },
                "com.example/source": "edge"
              }
            }
        """.trimIndent()
        assertEquals(
            Json.parseToJsonElement("""{"name":"test-server","version":"2.0.0"}"""),
            result.meta?.get("io.modelcontextprotocol/serverInfo"),
        )
    }

    @Test
    fun `should read absent cache fields as the conservative defaults`() {
        // The cache hints and resultType carry construction defaults, so a receiver has to tolerate
        // a peer that omits them rather than rejecting the whole result: the default reading —
        // immediately stale, private, complete — is the safe one either way.
        val terse = listOf(
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
            """
            {
              "supportedVersions": ["2026-07-28"],
              "capabilities": {}
            }
            """.trimIndent(),
        )

        terse.forEach { wire ->
            val result = assertIs<DiscoverResult>(McpJson.decodeFromString<ServerResult>(wire))

            assertEquals(0, result.ttlMs)
            assertEquals(CacheScope.Private, result.cacheScope)
            assertEquals(COMPLETE_RESULT_TYPE, result.resultType)
        }
    }

    @Test
    fun `should deserialize discovery results polymorphically`() {
        val wire =
            """
            {
              "supportedVersions": ["2026-07-28"],
              "capabilities": {},
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
        assertEquals(250, discover.ttlMs)
        assertEquals(CacheScope.Public, discover.cacheScope)
        assertEquals(
            Json.parseToJsonElement("""{"name":"server","version":"1"}"""),
            discover.meta?.get("io.modelcontextprotocol/serverInfo"),
        )
        assertNotNull(discover.meta?.get("com.example/source"))
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
