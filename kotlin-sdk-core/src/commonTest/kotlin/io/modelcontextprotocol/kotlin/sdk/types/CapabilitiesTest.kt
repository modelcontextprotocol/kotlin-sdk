package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CapabilitiesTest {

    @Test
    fun `should serialize ClientCapabilities with all fields`() {
        val capabilities = ClientCapabilities(
            sampling = ClientCapabilities.Sampling(context = EmptyJsonObject, tools = EmptyJsonObject),
            roots = ClientCapabilities.Roots(listChanged = true),
            elicitation = ClientCapabilities.Elicitation(form = EmptyJsonObject, url = EmptyJsonObject),
            tasks = ClientCapabilities.Tasks(
                list = EmptyJsonObject,
                cancel = EmptyJsonObject,
                requests = ClientCapabilities.Tasks.Requests(
                    sampling = ClientCapabilities.Tasks.Requests.Sampling(createMessage = EmptyJsonObject),
                    elicitation = ClientCapabilities.Tasks.Requests.Elicitation(create = EmptyJsonObject),
                ),
            ),
            experimental = buildJsonObject { put("feature1", buildJsonObject { put("enabled", true) }) },
            extensions = mapOf("io.modelcontextprotocol/ui" to EmptyJsonObject),
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "sampling": {"context": {}, "tools": {}},
              "roots": {"listChanged": true},
              "elicitation": {"form": {}, "url": {}},
              "tasks": {
                "list": {},
                "cancel": {},
                "requests": {
                  "sampling": {"createMessage": {}},
                  "elicitation": {"create": {}}
                }
              },
              "experimental": {"feature1": {"enabled": true}},
              "extensions": {"io.modelcontextprotocol/ui": {}}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize empty ClientCapabilities from JSON`() {
        val json = "{}"

        val capabilities = verifyDeserialization<ClientCapabilities>(McpJson, json)

        assertNull(capabilities.sampling)
        assertNull(capabilities.roots)
        assertNull(capabilities.elicitation)
        assertNull(capabilities.experimental)
        assertNull(capabilities.extensions)
    }

    @Test
    fun `should serialize ServerCapabilities with all fields`() {
        val capabilities = ServerCapabilities(
            tools = ServerCapabilities.Tools(listChanged = true),
            resources = ServerCapabilities.Resources(listChanged = true, subscribe = true),
            prompts = ServerCapabilities.Prompts(listChanged = false),
            logging = ServerCapabilities.Logging,
            completions = ServerCapabilities.Completions,
            tasks = ServerCapabilities.Tasks(
                list = EmptyJsonObject,
                cancel = EmptyJsonObject,
                requests = ServerCapabilities.Tasks.Requests(
                    tools = ServerCapabilities.Tasks.Requests.Tools(call = EmptyJsonObject),
                ),
            ),
            experimental = buildJsonObject { put("feature1", buildJsonObject { put("enabled", true) }) },
            extensions = mapOf("io.modelcontextprotocol/ui" to EmptyJsonObject),
        )
        verifySerialization(
            capabilities,
            McpJson,
            """
            {
              "tools": {"listChanged": true},
              "resources": {"listChanged": true, "subscribe": true},
              "prompts": {"listChanged": false},
              "logging": {},
              "completions": {},
              "tasks": {
                "list": {},
                "cancel": {},
                "requests": {"tools": {"call": {}}}
              },
              "experimental": {"feature1": {"enabled": true}},
              "extensions": {"io.modelcontextprotocol/ui": {}}
            }
            """.trimIndent(),
        )
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
        assertNull(capabilities.extensions)
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

        // Sampling is now a typed struct; unknown fields are ignored on deserialization
        val capabilities = McpJson.decodeFromString<ClientCapabilities>(json)

        // Should not fail - additionalProperties are allowed (unknown fields are ignored by Sampling)
        assertNotNull(capabilities.sampling)
    }

    @Test
    fun `supportsUrl should be true only when url mode is declared`() {
        val undeclared: ClientCapabilities.Elicitation? = null

        undeclared.supportsUrl shouldBe false
        ClientCapabilities.Elicitation().supportsUrl shouldBe false
        ClientCapabilities.Elicitation(form = EmptyJsonObject).supportsUrl shouldBe false
        ClientCapabilities.Elicitation(url = EmptyJsonObject).supportsUrl shouldBe true
        ClientCapabilities.Elicitation(form = EmptyJsonObject, url = EmptyJsonObject).supportsUrl shouldBe true
    }
}
