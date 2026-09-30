package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

class InitializeTest {

    @Test
    fun `should serialize InitializeRequest with capabilities and meta`() {
        val request = InitializeRequest(
            InitializeRequestParams(
                protocolVersion = "2024-11-05",
                capabilities = ClientCapabilities(
                    sampling = ClientCapabilities.Sampling(),
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

        verifySerialization<ServerResult>(
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
}
