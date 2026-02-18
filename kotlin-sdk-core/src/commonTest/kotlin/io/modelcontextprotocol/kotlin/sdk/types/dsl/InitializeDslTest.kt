package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class InitializeDslTest {
    @Test
    fun `buildInitializeRequest should create request with all fields`() {
        val request = InitializeRequest {
            protocolVersion = "2024-11-05"
            capabilities {
                sampling {
                    put("maxTokens", 100)
                }
                roots(listChanged = true)
                elicitation {
                    put("mode", "interactive")
                }
                experimental {
                    put("custom", true)
                }
            }
            info(
                name = "TestClient",
                version = "1.0.0",
                title = "Test Client",
                websiteUrl = "https://example.com",
            )
        }

        request.params.protocolVersion shouldBe "2024-11-05"
        request.params.capabilities.shouldNotBeNull {
            sampling?.get("maxTokens")?.jsonPrimitive?.int shouldBe 100
            roots?.listChanged shouldBe true
            elicitation?.get("mode")?.jsonPrimitive?.content shouldBe "interactive"
            experimental?.get("custom")?.jsonPrimitive?.content shouldBe "true"
        }
        request.params.clientInfo.shouldNotBeNull {
            name shouldBe "TestClient"
            version shouldBe "1.0.0"
            title shouldBe "Test Client"
            websiteUrl shouldBe "https://example.com"
        }
    }

    @Test
    fun `buildInitializeRequest should support direct capabilities and info`() {
        val capabilities = ClientCapabilities(roots = ClientCapabilities.Roots(listChanged = false))
        val info = Implementation(name = "Direct", version = "0.1")

        val request = InitializeRequest {
            protocolVersion = "1.0"
            capabilities(capabilities)
            info(info)
        }

        request.params.capabilities shouldBe capabilities
        request.params.clientInfo shouldBe info
    }

    @Test
    fun `capabilities DSL should support direct JsonObject values`() {
        val samplingObj = buildJsonObject { put("key", "value") }
        val elicitationObj = buildJsonObject { put("key", "value") }
        val experimentalObj = buildJsonObject { put("key", "value") }

        val request = InitializeRequest {
            protocolVersion = "1.0"
            capabilities {
                sampling(samplingObj)
                elicitation(elicitationObj)
                experimental(experimentalObj)
            }
            info("Test", "1.0")
        }

        request.params.capabilities.shouldNotBeNull {
            sampling shouldBe samplingObj
            elicitation shouldBe elicitationObj
            experimental shouldBe experimentalObj
        }
    }

    @Test
    fun `ClientCapabilitiesBuilder roots should support default arguments`() {
        val request = InitializeRequest {
            protocolVersion = "1.0"
            capabilities {
                roots()
            }
            info("Test", "1.0")
        }
        request.params.capabilities.roots.shouldNotBeNull {
            listChanged shouldBe null
        }
    }

    @Test
    fun `buildInitializeRequest should throw if protocolVersion is missing`() {
        shouldThrow<IllegalArgumentException> {
            InitializeRequest {
                capabilities { }
                info("Test", "1.0")
            }
        }
    }

    @Test
    fun `buildInitializeRequest should throw if capabilities are missing`() {
        shouldThrow<IllegalArgumentException> {
            InitializeRequest {
                protocolVersion = "1.0"
                info("Test", "1.0")
            }
        }
    }

    @Test
    fun `buildInitializeRequest should throw if info is missing`() {
        shouldThrow<IllegalArgumentException> {
            InitializeRequest {
                protocolVersion = "1.0"
                capabilities { }
            }
        }
    }
}
