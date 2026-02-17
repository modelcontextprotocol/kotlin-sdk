package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InitializeTest {

    @Test
    fun `should serialize InitializeRequest with capabilities and meta`() {
        val request = InitializeRequest(
            InitializeRequestParams(
                protocolVersion = "2024-11-05",
                capabilities = ClientCapabilities(
                    sampling = ClientCapabilities.sampling,
                    roots = ClientCapabilities.Roots(listChanged = true),
                    elicitation = ClientCapabilities.elicitation,
                    experimental = buildJsonObject {
                        put(
                            "workspace-sync",
                            buildJsonObject { put("enabled", true) },
                        )
                    },
                ),
                clientInfo = Implementation(
                    name = "dev-client",
                    version = "1.2.3",
                    title = "Dev Client",
                    icons = listOf(Icon(src = "https://example.com/icon.png")),
                ),
                meta = RequestMeta(
                    buildJsonObject { put("progressToken", "init-42") },
                ),
            ),
        )

        verifySerialization(
            request,
            McpJson,
            """
            {
              "method": "initialize",
              "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {
                  "sampling": {},
                  "roots": {
                    "listChanged": true
                  },
                  "elicitation": {},
                  "experimental": {
                    "workspace-sync": {
                      "enabled": true
                    }
                  }
                },
                "clientInfo": {
                  "name": "dev-client",
                  "version": "1.2.3",
                  "title": "Dev Client",
                  "icons": [
                    {"src": "https://example.com/icon.png"}
                  ]
                },
                "_meta": {
                  "progressToken": "init-42"
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize InitializeRequest from JSON`() {
        val json = """
            {
              "method": "initialize",
              "params": {
                "protocolVersion": "2025-03-26",
                "capabilities": {
                  "sampling": {},
                  "roots": {
                    "listChanged": false
                  },
                  "experimental": {
                    "custom-cap": {
                      "enabled": true
                    }
                  }
                },
                "clientInfo": {
                  "name": "sdk-client",
                  "version": "2.1.0",
                  "title": "SDK Client"
                },
                "_meta": {
                  "progressToken": 99
                }
              }
            }
        """.trimIndent()

        val request = verifyDeserialization<InitializeRequest>(McpJson, json)
        assertEquals(Method.Defined.Initialize, request.method)

        val params = request.params
        assertEquals("2025-03-26", params.protocolVersion)
        assertEquals("sdk-client", params.clientInfo.name)
        assertEquals("2.1.0", params.clientInfo.version)
        assertEquals("SDK Client", params.clientInfo.title)
        assertEquals(ProgressToken(99), params.meta?.progressToken)

        val capabilities = params.capabilities
        assertNotNull(capabilities.sampling)
        assertEquals(false, capabilities.roots?.listChanged)
        val experimental = capabilities.experimental
        assertNotNull(experimental)
        val custom = experimental["custom-cap"]?.jsonObject
        assertNotNull(custom)
        assertEquals(true, custom["enabled"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `should serialize InitializeResult with instructions`() {
        val result = InitializeResult(
            protocolVersion = "2024-11-05",
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
                resources = ServerCapabilities.Resources(
                    listChanged = true,
                    subscribe = true,
                ),
                prompts = ServerCapabilities.Prompts(listChanged = false),
                logging = ServerCapabilities.Logging,
                completions = buildJsonObject { put("defaultCount", 5) },
            ),
            serverInfo = Implementation(
                name = "demo-server",
                version = "5.0.0",
                websiteUrl = "https://example.com/server",
            ),
            instructions = "Call the `read` tool to fetch files.",
            meta = buildJsonObject { put("issuedAt", "2025-01-12T15:00:58Z") },
        )

        verifySerialization(
            result,
            McpJson,
            """
            {
              "protocolVersion": "2024-11-05",
              "capabilities": {
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
                "completions": {
                  "defaultCount": 5
                }
              },
              "serverInfo": {
                "name": "demo-server",
                "version": "5.0.0",
                "websiteUrl": "https://example.com/server"
              },
              "instructions": "Call the `read` tool to fetch files.",
              "_meta": {
                "issuedAt": "2025-01-12T15:00:58Z"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize InitializeResult from JSON`() {
        val json = """
            {
              "protocolVersion": "2025-03-26",
              "capabilities": {
                "tools": {},
                "resources": {
                  "listChanged": true
                }
              },
              "serverInfo": {
                "name": "result-server",
                "version": "4.0.0"
              }
            }
        """.trimIndent()

        val result = verifyDeserialization<InitializeResult>(McpJson, json)

        assertEquals("2025-03-26", result.protocolVersion)
        assertEquals("result-server", result.serverInfo.name)
        assertEquals("4.0.0", result.serverInfo.version)
        assertNull(result.instructions)
        assertNull(result.meta)

        val capabilities = result.capabilities
        assertNotNull(capabilities.tools)
        assertEquals(true, capabilities.resources?.listChanged)
        assertNull(capabilities.resources?.subscribe)
        assertNull(capabilities.prompts)
        assertNull(capabilities.logging)
    }
}
