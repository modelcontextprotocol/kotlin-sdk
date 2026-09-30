package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.buildInitializeRequest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class InitializeDslTest {
    @Test
    fun `buildInitializeRequest should create request with all fields`() {
        val request = buildInitializeRequest {
            protocolVersion = "2024-11-05"
            capabilities {
                sampling(ClientCapabilities.Sampling(tools = EmptyJsonObject))
                roots(listChanged = true)
                elicitation(ClientCapabilities.Elicitation(form = EmptyJsonObject))
                experimental {
                    put("custom", true)
                }
                extensions(
                    mapOf(
                        "io.modelcontextprotocol/ui" to EmptyJsonObject,
                    ),
                )
            }
            info(
                name = "TestClient",
                version = "1.0.0",
                title = "Test Client",
                websiteUrl = "https://example.com",
            )
        }

        request.params shouldBe InitializeRequestParams(
            protocolVersion = "2024-11-05",
            capabilities = ClientCapabilities(
                sampling = ClientCapabilities.Sampling(tools = EmptyJsonObject),
                roots = ClientCapabilities.Roots(listChanged = true),
                elicitation = ClientCapabilities.Elicitation(form = EmptyJsonObject),
                experimental = buildJsonObject { put("custom", true) },
                extensions = mapOf("io.modelcontextprotocol/ui" to EmptyJsonObject),
            ),
            clientInfo = Implementation(
                name = "TestClient",
                version = "1.0.0",
                title = "Test Client",
                websiteUrl = "https://example.com",
            ),
        )
    }

    @Test
    fun `buildInitializeRequest should support direct capabilities and info`() {
        val capabilities = ClientCapabilities(roots = ClientCapabilities.Roots(listChanged = false))
        val info = Implementation(name = "Direct", version = "0.1")

        val request = buildInitializeRequest {
            protocolVersion = "1.0"
            capabilities(capabilities)
            info(info)
        }

        request.params.capabilities shouldBe capabilities
        request.params.clientInfo shouldBe info
    }

    @Test
    fun `buildInitializeRequest should throw if protocolVersion is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildInitializeRequest {
                capabilities { }
                info("Test", "1.0")
            }
        }
    }

    @Test
    fun `buildInitializeRequest should throw if capabilities are missing`() {
        shouldThrow<IllegalArgumentException> {
            buildInitializeRequest {
                protocolVersion = "1.0"
                info("Test", "1.0")
            }
        }
    }

    @Test
    fun `buildInitializeRequest should throw if info is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildInitializeRequest {
                protocolVersion = "1.0"
                capabilities { }
            }
        }
    }
}
