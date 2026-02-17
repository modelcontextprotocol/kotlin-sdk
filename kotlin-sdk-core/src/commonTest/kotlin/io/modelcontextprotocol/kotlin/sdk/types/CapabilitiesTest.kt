package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerializationRoundTrip
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CapabilitiesTest {

    @Test
    fun `should serialize Implementation with minimal fields`() {
        val implementation = Implementation(
            name = "test-server",
            version = "1.0.0",
        )
        verifySerialization(
            implementation,
            McpJson,
            """
            {
              "name": "test-server",
              "version": "1.0.0"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize Implementation with all fields`() {
        val implementation = Implementation(
            name = "test-server",
            version = "1.0.0",
            title = "Test Server",
            websiteUrl = "https://example.com",
            icons = listOf(
                Icon(src = "https://example.com/icon.png"),
            ),
        )
        verifySerialization(
            implementation,
            McpJson,
            """
            {
              "name": "test-server",
              "version": "1.0.0",
              "title": "Test Server",
              "websiteUrl": "https://example.com",
              "icons": [
                {"src": "https://example.com/icon.png"}
              ]
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize Implementation from JSON`() {
        val json = """
            {
              "name": "test-server",
              "version": "2.1.3-beta",
              "title": "Test Server",
              "websiteUrl": "https://example.com"
            }
        """.trimIndent()

        val implementation = verifyDeserialization<Implementation>(McpJson, json)

        assertEquals("test-server", implementation.name)
        assertEquals("2.1.3-beta", implementation.version)
        assertEquals("Test Server", implementation.title)
        assertEquals("https://example.com", implementation.websiteUrl)
    }

    @Test
    fun `should deserialize Implementation with minimal fields from JSON`() {
        val json = """
            {
              "name": "minimal-server",
              "version": "0.1.0"
            }
        """.trimIndent()

        val implementation = verifyDeserialization<Implementation>(McpJson, json)

        assertEquals("minimal-server", implementation.name)
        assertEquals("0.1.0", implementation.version)
        assertNull(implementation.title)
        assertNull(implementation.websiteUrl)
        assertNull(implementation.icons)
    }

    @Test
    fun `should serialize and deserialize Implementation round trip`() {
        val original = Implementation(
            name = "round-trip-test",
            version = "1.2.3",
            title = "Round Trip Test",
            websiteUrl = "https://test.com",
        )

        verifySerializationRoundTrip(original, McpJson)
    }

    // ClientCapabilities tests
    @Test
    fun `should serialize empty ClientCapabilities`() {
        val capabilities = ClientCapabilities()
        val json = McpJson.encodeToString(capabilities)

        json shouldEqualJson "{}"
    }

    @Test
    fun `should serialize ClientCapabilities with sampling`() {
        val capabilities = ClientCapabilities(
            sampling = ClientCapabilities.sampling,
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "sampling": {}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ClientCapabilities with roots without listChanged`() {
        val capabilities = ClientCapabilities(
            roots = ClientCapabilities.Roots(),
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "roots": {}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ClientCapabilities with roots with listChanged`() {
        val capabilities = ClientCapabilities(
            roots = ClientCapabilities.Roots(listChanged = true),
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "roots": {
                "listChanged": true
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ClientCapabilities with elicitation`() {
        val capabilities = ClientCapabilities(
            elicitation = ClientCapabilities.elicitation,
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "elicitation": {}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ClientCapabilities with experimental`() {
        val experimental = buildJsonObject {
            put(
                "customFeature",
                buildJsonObject {
                    put("enabled", true)
                },
            )
        }
        val capabilities = ClientCapabilities(
            experimental = experimental,
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "experimental": {
                "customFeature": {
                  "enabled": true
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ClientCapabilities with all fields`() {
        val experimental = buildJsonObject {
            put("feature1", buildJsonObject { put("enabled", true) })
        }
        val capabilities = ClientCapabilities(
            sampling = ClientCapabilities.sampling,
            roots = ClientCapabilities.Roots(listChanged = true),
            elicitation = ClientCapabilities.elicitation,
            experimental = experimental,
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "sampling": {},
              "roots": {
                "listChanged": true
              },
              "elicitation": {},
              "experimental": {
                "feature1": {
                  "enabled": true
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize ClientCapabilities from JSON`() {
        val json = """
            {
              "sampling": {},
              "roots": {
                "listChanged": true
              },
              "elicitation": {}
            }
        """.trimIndent()

        val capabilities = verifyDeserialization<ClientCapabilities>(McpJson, json)

        assertEquals(EmptyJsonObject, capabilities.sampling)
        assertEquals(true, capabilities.roots?.listChanged)
        assertEquals(EmptyJsonObject, capabilities.elicitation)
    }

    @Test
    fun `should deserialize empty ClientCapabilities from JSON`() {
        val json = "{}"

        val capabilities = verifyDeserialization<ClientCapabilities>(McpJson, json)

        assertNull(capabilities.sampling)
        assertNull(capabilities.roots)
        assertNull(capabilities.elicitation)
        assertNull(capabilities.experimental)
    }

    @Test
    fun `should serialize and deserialize ClientCapabilities round trip`() {
        val original = ClientCapabilities(
            sampling = ClientCapabilities.sampling,
            roots = ClientCapabilities.Roots(listChanged = false),
            elicitation = ClientCapabilities.elicitation,
        )

        verifySerializationRoundTrip(original, McpJson)
    }

    // ServerCapabilities tests
    @Test
    fun `should serialize empty ServerCapabilities`() {
        val capabilities = ServerCapabilities()
        val json = McpJson.encodeToString(capabilities)

        json shouldEqualJson "{}"
    }

    @Test
    fun `should serialize ServerCapabilities with tools without listChanged`() {
        val capabilities = ServerCapabilities(
            tools = ServerCapabilities.Tools(),
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "tools": {}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ServerCapabilities with tools with listChanged`() {
        val capabilities = ServerCapabilities(
            tools = ServerCapabilities.Tools(listChanged = true),
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "tools": {
                "listChanged": true
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ServerCapabilities with resources without options`() {
        val capabilities = ServerCapabilities(
            resources = ServerCapabilities.Resources(),
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "resources": {}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ServerCapabilities with resources with listChanged`() {
        val capabilities = ServerCapabilities(
            resources = ServerCapabilities.Resources(listChanged = true),
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "resources": {
                "listChanged": true
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ServerCapabilities with resources with subscribe`() {
        val capabilities = ServerCapabilities(
            resources = ServerCapabilities.Resources(subscribe = true),
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "resources": {
                "subscribe": true
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ServerCapabilities with resources with both options`() {
        val capabilities = ServerCapabilities(
            resources = ServerCapabilities.Resources(
                listChanged = true,
                subscribe = false,
            ),
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "resources": {
                "listChanged": true,
                "subscribe": false
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ServerCapabilities with prompts without listChanged`() {
        val capabilities = ServerCapabilities(
            prompts = ServerCapabilities.Prompts(),
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "prompts": {}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ServerCapabilities with prompts with listChanged`() {
        val capabilities = ServerCapabilities(
            prompts = ServerCapabilities.Prompts(listChanged = false),
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "prompts": {
                "listChanged": false
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ServerCapabilities with logging`() {
        val capabilities = ServerCapabilities(
            logging = ServerCapabilities.Logging,
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "logging": {}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ServerCapabilities with completions`() {
        val capabilities = ServerCapabilities(
            completions = ServerCapabilities.Completions,
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "completions": {}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ServerCapabilities with experimental`() {
        val experimental = buildJsonObject {
            put(
                "customCapability",
                buildJsonObject {
                    put("version", "1.0")
                },
            )
        }
        val capabilities = ServerCapabilities(
            experimental = experimental,
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "experimental": {
                "customCapability": {
                  "version": "1.0"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize ServerCapabilities with all fields`() {
        val experimental = buildJsonObject {
            put("feature1", buildJsonObject { put("enabled", true) })
        }
        val capabilities = ServerCapabilities(
            tools = ServerCapabilities.Tools(listChanged = true),
            resources = ServerCapabilities.Resources(
                listChanged = true,
                subscribe = true,
            ),
            prompts = ServerCapabilities.Prompts(listChanged = false),
            logging = ServerCapabilities.Logging,
            completions = ServerCapabilities.Completions,
            experimental = experimental,
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "tools": {
                "listChanged": true
              },
              "resources": {
                "listChanged": true,
                "subscribe": true
              },
              "prompts": {
                "listChanged": false
              },
              "logging": {},
              "completions": {},
              "experimental": {
                "feature1": {
                  "enabled": true
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize ServerCapabilities from JSON`() {
        val json = """
            {
              "tools": {
                "listChanged": true
              },
              "resources": {
                "listChanged": false,
                "subscribe": true
              },
              "prompts": {
                "listChanged": true
              },
              "logging": {},
              "completions": {}
            }
        """.trimIndent()

        val capabilities = verifyDeserialization<ServerCapabilities>(McpJson, json)

        assertEquals(true, capabilities.tools?.listChanged)
        assertEquals(false, capabilities.resources?.listChanged)
        assertEquals(true, capabilities.resources?.subscribe)
        assertEquals(true, capabilities.prompts?.listChanged)
        assertEquals(EmptyJsonObject, capabilities.logging)
        assertEquals(EmptyJsonObject, capabilities.completions)
    }

    @Test
    fun `should deserialize empty ServerCapabilities from JSON`() {
        val json = "{}"

        val capabilities = verifyDeserialization<ServerCapabilities>(McpJson, json)

        assertNull(capabilities.tools)
        assertNull(capabilities.resources)
        assertNull(capabilities.prompts)
        assertNull(capabilities.logging)
        assertNull(capabilities.completions)
        assertNull(capabilities.experimental)
    }

    @Test
    fun `should serialize and deserialize ServerCapabilities round trip`() {
        val original = ServerCapabilities(
            tools = ServerCapabilities.Tools(listChanged = true),
            resources = ServerCapabilities.Resources(
                listChanged = false,
                subscribe = true,
            ),
            prompts = ServerCapabilities.Prompts(listChanged = true),
            logging = ServerCapabilities.Logging,
        )

        verifySerializationRoundTrip(original, McpJson)
    }

    // Additional nested type tests
    @Test
    fun `should deserialize ClientCapabilities Roots with null listChanged`() {
        val json = """
            {
              "roots": {}
            }
        """.trimIndent()

        val capabilities = verifyDeserialization<ClientCapabilities>(McpJson, json)

        assertNull(capabilities.roots?.listChanged)
    }

    @Test
    fun `should deserialize ServerCapabilities nested types with null values`() {
        val json = """
            {
              "tools": {},
              "resources": {},
              "prompts": {}
            }
        """.trimIndent()

        val capabilities = verifyDeserialization<ServerCapabilities>(McpJson, json)

        assertNull(capabilities.tools?.listChanged)
        assertNull(capabilities.resources?.listChanged)
        assertNull(capabilities.resources?.subscribe)
        assertNull(capabilities.prompts?.listChanged)
    }

    @Test
    fun `should handle ClientCapabilities with additionalProperties in sampling`() {
        val json = """
            {
              "sampling": {
                "customProperty": "customValue"
              }
            }
        """.trimIndent()

        val capabilities = verifyDeserialization<ClientCapabilities>(McpJson, json)

        // Should not fail - additionalProperties are allowed
        assertEquals("customValue", capabilities.sampling?.get("customProperty")?.toString()?.trim('"'))
    }

    @Test
    fun `should handle ServerCapabilities with additionalProperties in logging`() {
        val json = """
            {
              "logging": {
                "level": "debug"
              }
            }
        """.trimIndent()

        val capabilities = verifyDeserialization<ServerCapabilities>(McpJson, json)

        // Should not fail - additionalProperties are allowed
        assertEquals("debug", capabilities.logging?.get("level")?.toString()?.trim('"'))
    }
}
