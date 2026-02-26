package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.invoke
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

/**
 * Tests for ClientCapabilities DSL builder.
 *
 * Verifies that capabilities can be constructed via DSL within InitializeRequest,
 * covering minimal (empty), full (all fields), and variant patterns.
 */
@OptIn(ExperimentalMcpApi::class)
class CapabilitiesDslTest {
    @Test
    fun `capabilities should build minimal empty capabilities`() {
        val request = InitializeRequest {
            protocolVersion = "2024-11-05"
            capabilities { }
            info("Test", "1.0")
        }

        request.params.capabilities.shouldNotBeNull {
            sampling.shouldBeNull()
            roots.shouldBeNull()
            elicitation.shouldBeNull()
            experimental.shouldBeNull()
        }
    }

    @Test
    fun `capabilities should build full with all fields and nested properties`() {
        val request = InitializeRequest {
            protocolVersion = "2024-11-05"
            capabilities {
                sampling {
                    put("temperature", 0.7)
                    put("maxTokens", 1000)
                    put("topP", 0.95)
                }
                roots(listChanged = true)
                elicitation {
                    put("mode", "interactive")
                    put("timeout", 30)
                    put("retries", 3)
                }
                experimental {
                    put("customFeature", true)
                    put("version", "1.0")
                    put("beta", false)
                    put("maxConcurrency", 10)
                }
            }
            info("Test", "1.0")
        }

        request.params.capabilities.shouldNotBeNull {
            sampling shouldNotBeNull {
                get("temperature")?.jsonPrimitive?.double shouldBe 0.7
                get("maxTokens")?.jsonPrimitive?.content shouldBe "1000"
                get("topP")?.jsonPrimitive?.double shouldBe 0.95
            }
            roots shouldNotBeNull {
                listChanged shouldBe true
            }
            elicitation shouldNotBeNull {
                get("mode")?.jsonPrimitive?.content shouldBe "interactive"
                get("timeout")?.jsonPrimitive?.content shouldBe "30"
                get("retries")?.jsonPrimitive?.content shouldBe "3"
            }
            experimental shouldNotBeNull {
                get("customFeature")?.jsonPrimitive?.content shouldBe "true"
                get("version")?.jsonPrimitive?.content shouldBe "1.0"
                get("beta")?.jsonPrimitive?.content shouldBe "false"
                get("maxConcurrency")?.jsonPrimitive?.content shouldBe "10"
            }
        }
    }

    @Test
    fun `capabilities should support roots variants`() {
        // Test listChanged = true
        val requestTrue = InitializeRequest {
            protocolVersion = "2024-11-05"
            capabilities { roots(listChanged = true) }
            info("Test", "1.0")
        }
        requestTrue.params.capabilities.roots?.listChanged shouldBe true

        // Test listChanged = false
        val requestFalse = InitializeRequest {
            protocolVersion = "2024-11-05"
            capabilities { roots(listChanged = false) }
            info("Test", "1.0")
        }
        requestFalse.params.capabilities.roots?.listChanged shouldBe false

        // Test listChanged = null (not provided)
        val requestNull = InitializeRequest {
            protocolVersion = "2024-11-05"
            capabilities { roots() }
            info("Test", "1.0")
        }
        requestNull.params.capabilities.roots?.listChanged.shouldBeNull()
    }

    @Test
    fun `capabilities should overwrite when same field set multiple times`() {
        val request = InitializeRequest {
            protocolVersion = "2024-11-05"
            capabilities {
                sampling { put("temperature", 0.5) }
                sampling { put("temperature", 0.9) } // Should overwrite
                experimental { put("feature", "v1") }
                experimental { put("feature", "v2") } // Should overwrite
            }
            info("Test", "1.0")
        }

        request.params.capabilities.shouldNotBeNull {
            sampling shouldNotBeNull {
                get("temperature")?.jsonPrimitive?.double shouldBe 0.9
            }
            experimental shouldNotBeNull {
                get("feature")?.jsonPrimitive?.content shouldBe "v2"
            }
        }
    }
}
