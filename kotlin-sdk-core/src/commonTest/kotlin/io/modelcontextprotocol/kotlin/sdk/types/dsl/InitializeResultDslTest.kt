package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.buildInitializeResult
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

/**
 * Tests for InitializeResult DSL builder.
 *
 * Verifies InitializeResult can be constructed via DSL,
 * covering minimal (required only), full (all fields), and edge cases.
 */
@OptIn(ExperimentalMcpApi::class)
class InitializeResultDslTest {

    @Test
    fun `InitializeResult should build minimal with default protocol version`() {
        val result = buildInitializeResult {
            capabilities(ServerCapabilities())
            info("MyServer", "1.0.0")
        }

        result.protocolVersion shouldBe LATEST_PROTOCOL_VERSION
        result.capabilities shouldNotBeNull {}
        result.serverInfo shouldNotBeNull {
            name shouldBe "MyServer"
            version shouldBe "1.0.0"
        }
        result.instructions.shouldBeNull()
        result.meta.shouldBeNull()
    }

    @Test
    fun `InitializeResult should build full with all fields`() {
        val result = buildInitializeResult {
            protocolVersion = "2024-11-05"

            capabilities(
                ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                    resources = ServerCapabilities.Resources(listChanged = true, subscribe = true),
                    prompts = ServerCapabilities.Prompts(listChanged = true),
                    logging = EmptyJsonObject,
                    completions = EmptyJsonObject,
                ),
            )

            info(
                name = "AdvancedServer",
                version = "2.0.0",
                title = "Advanced MCP Server",
                websiteUrl = "https://example.com",
            )

            instructions = "Use this server for advanced operations. Available tools include..."

            meta {
                put("serverStartTime", 1707317000000L)
                put("region", "us-west-1")
                put("environment", "production")
            }
        }

        result.protocolVersion shouldBe "2024-11-05"

        result.capabilities shouldNotBeNull {
            tools shouldNotBeNull {
                listChanged shouldBe true
            }
            resources shouldNotBeNull {
                listChanged shouldBe true
                subscribe shouldBe true
            }
            prompts shouldNotBeNull {
                listChanged shouldBe true
            }
            logging shouldNotBeNull {}
            completions shouldNotBeNull {}
        }

        result.serverInfo shouldNotBeNull {
            name shouldBe "AdvancedServer"
            version shouldBe "2.0.0"
            title shouldBe "Advanced MCP Server"
            websiteUrl shouldBe "https://example.com"
        }

        result.instructions shouldBe "Use this server for advanced operations. Available tools include..."

        result.meta shouldNotBeNull {
            get("region")?.jsonPrimitive?.content shouldBe "us-west-1"
            get("environment")?.jsonPrimitive?.content shouldBe "production"
        }
    }

    @Test
    fun `InitializeResult should support partial capabilities`() {
        val result = buildInitializeResult {
            capabilities(
                ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                    // Other capabilities null
                ),
            )
            info("SimpleServer", "1.0")
        }

        result.capabilities shouldNotBeNull {
            tools shouldNotBeNull {}
            resources.shouldBeNull()
            prompts.shouldBeNull()
            logging.shouldBeNull()
            completions.shouldBeNull()
        }
    }

    @Test
    fun `InitializeResult should support capabilities with false flags`() {
        val result = buildInitializeResult {
            capabilities(
                ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                    resources = ServerCapabilities.Resources(listChanged = false, subscribe = false),
                ),
            )
            info("StaticServer", "1.0")
        }

        result.capabilities shouldNotBeNull {
            tools shouldNotBeNull {
                listChanged shouldBe false
            }
            resources shouldNotBeNull {
                listChanged shouldBe false
                subscribe shouldBe false
            }
        }
    }

    @Test
    fun `InitializeResult should throw if capabilities missing`() {
        shouldThrow<IllegalArgumentException> {
            buildInitializeResult {
                info("Server", "1.0")
            }
        }
    }

    @Test
    fun `InitializeResult should throw if info missing`() {
        shouldThrow<IllegalArgumentException> {
            buildInitializeResult {
                capabilities(ServerCapabilities())
            }
        }
    }

    @Test
    fun `InitializeResult should support long instructions`() {
        val longInstructions = "This is a very long instruction text. ".repeat(100)

        val result = buildInitializeResult {
            capabilities(ServerCapabilities())
            info("Server", "1.0")
            instructions = longInstructions
        }

        result.instructions shouldBe longInstructions
    }

    @Test
    fun `InitializeResult should support custom protocol versions`() {
        val result = buildInitializeResult {
            protocolVersion = "2025-01-01"
            capabilities(ServerCapabilities())
            info("FutureServer", "3.0")
        }

        result.protocolVersion shouldBe "2025-01-01"
    }

    @Test
    fun `InitializeResult should support unicode in instructions`() {
        val result = buildInitializeResult {
            capabilities(ServerCapabilities())
            info("Server", "1.0")
            instructions = "サーバーの使い方: 🚀 Start here! Ça va?"
        }

        result.instructions shouldBe "サーバーの使い方: 🚀 Start here! Ça va?"
    }
}
