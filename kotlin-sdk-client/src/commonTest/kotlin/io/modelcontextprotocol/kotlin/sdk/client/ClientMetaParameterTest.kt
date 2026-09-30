package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test

class ClientMetaParameterTest {

    private val mockTransport = MockTransport()

    private suspend fun connectedClient(): Client = Client(Implementation("test-client", "1.0.0")).apply {
        connect(mockTransport)
    }

    private fun sentMeta(): JsonElement? = mockTransport.lastRequest().params.shouldBeInstanceOf<JsonObject>()["_meta"]

    @Test
    fun `should accept valid meta keys`() = runTest {
        val keys = listOf(
            "simple-key",
            "api.example.com/version",
            "com.company.app/setting",
            "retry_count",
            "user.preference",
            "valid123",
            "multi.dot.name",
            "under_score",
            "hyphen-dash",
            "org.apache.kafka/consumer-config",
            "a/",
            "a1-b2/test",
            "long.domain.name.here/config",
            "x/a",
        )

        connectedClient().callTool("test-tool", emptyMap(), keys.associateWith { "value" })

        sentMeta()?.jsonObject?.keys shouldBe keys.toSet()
    }

    @Test
    fun `should send meta values as JSON in the _meta object`() = runTest {
        val meta = mapOf(
            "api.example.com/version" to "1.0",
            "retry_count" to 3,
            "user.preference" to true,
            "config" to mapOf("ports" to listOf(80, 443)),
        )

        connectedClient().callTool("test-tool", mapOf("arg" to "value"), meta)

        sentMeta() shouldBe buildJsonObject {
            put("api.example.com/version", "1.0")
            put("retry_count", 3)
            put("user.preference", true)
            putJsonObject("config") {
                putJsonArray("ports") {
                    add(80)
                    add(443)
                }
            }
        }
    }

    @Test
    fun `should send an empty _meta object when no meta is given`() = runTest {
        connectedClient().callTool("test-tool", mapOf("arg" to "value"))

        sentMeta() shouldBe EmptyJsonObject
    }

    @Test
    fun `should reject reserved meta key prefixes`() = runTest {
        val client = connectedClient()
        listOf(
            "mcp/internal",
            "modelcontextprotocol/config",
            "MCP/internal",
            "Mcp/config",
            "mCp/setting",
            "MODELCONTEXTPROTOCOL/data",
            "ModelContextProtocol/value",
            "modelContextProtocol/test",
            "api.mcp.io/setting",
            "com.modelcontextprotocol.test/value",
            "example.mcp/data",
            "subdomain.mcp.com/config",
            "app.modelcontextprotocol.dev/setting",
            "test.mcp/value",
            "service.modelcontextprotocol/data",
        ).forEach { key ->
            withClue(key) {
                shouldThrow<IllegalArgumentException> {
                    client.callTool("test-tool", emptyMap(), mapOf(key to "value"))
                }.message shouldContain "reserved"
            }
        }
    }

    @Test
    fun `should reject invalid meta key formats`() = runTest {
        val client = connectedClient()
        listOf("", "/invalid", "-invalid", ".invalid", "in valid", "api../test", "api./test").forEach { key ->
            withClue("'$key'") {
                shouldThrow<IllegalArgumentException> {
                    client.callTool("test-tool", emptyMap(), mapOf(key to "value"))
                }
            }
        }
    }
}
