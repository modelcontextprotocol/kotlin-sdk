package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.SamplingMessage
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolChoice
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlin.test.Test

class ClientSamplingValidationTest {

    private val dummyTools = listOf(Tool(name = "t", inputSchema = ToolSchema()))
    private val noToolsCaps = ClientCapabilities(sampling = ClientCapabilities.sampling)
    private val withToolsCaps = ClientCapabilities(sampling = ClientCapabilities.Sampling(tools = EmptyJsonObject))

    private fun request(tools: List<Tool>? = null, toolChoice: ToolChoice? = null) = CreateMessageRequest(
        CreateMessageRequestParams(
            maxTokens = 10,
            messages = listOf(SamplingMessage(Role.User, TextContent("hi"))),
            tools = tools,
            toolChoice = toolChoice,
        ),
    )

    @Test
    fun `request without tools or toolChoice is always accepted`() {
        validateSamplingToolsCapability(request(), noToolsCaps)
    }

    @Test
    fun `tools without sampling tools capability throws InvalidParams`() {
        val exception = shouldThrow<McpException> {
            validateSamplingToolsCapability(request(tools = dummyTools), noToolsCaps)
        }

        exception.code shouldBe RPCError.ErrorCode.INVALID_PARAMS
        exception.message shouldContain "request contains tools parameter"
    }

    @Test
    fun `toolChoice without sampling tools capability throws InvalidParams`() {
        val exception = shouldThrow<McpException> {
            validateSamplingToolsCapability(request(toolChoice = ToolChoice(ToolChoice.Mode.Required)), noToolsCaps)
        }

        exception.code shouldBe RPCError.ErrorCode.INVALID_PARAMS
        exception.message shouldContain "request contains toolChoice parameter"
    }

    @Test
    fun `tools and toolChoice are accepted with sampling tools capability`() {
        validateSamplingToolsCapability(request(dummyTools, ToolChoice(ToolChoice.Mode.Auto)), withToolsCaps)
    }
}
