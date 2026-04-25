package io.modelcontextprotocol.kotlin.sdk.client

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
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClientSamplingValidationTest {

    private val dummyTool = Tool(
        name = "t",
        inputSchema = ToolSchema(properties = buildJsonObject { }, required = emptyList()),
    )

    private val minimalMessages = listOf(SamplingMessage(Role.User, TextContent("hi")))

    private val noToolsCaps = ClientCapabilities(sampling = ClientCapabilities.sampling)
    private val withToolsCaps = ClientCapabilities(
        sampling = ClientCapabilities.Sampling(tools = EmptyJsonObject),
    )

    @Test
    fun `request without tools or toolChoice is always accepted`() {
        val request = CreateMessageRequest(
            CreateMessageRequestParams(maxTokens = 10, messages = minimalMessages),
        )
        assertDoesNotThrow { validateSamplingToolsCapability(request, noToolsCaps) }
    }

    @Test
    fun `tools without sampling tools capability throws InvalidParams`() {
        val request = CreateMessageRequest(
            CreateMessageRequestParams(
                maxTokens = 10,
                messages = minimalMessages,
                tools = listOf(dummyTool),
            ),
        )
        val exception = assertFailsWith<McpException> {
            validateSamplingToolsCapability(request, noToolsCaps)
        }
        assertEquals(RPCError.ErrorCode.INVALID_PARAMS, exception.code)
        check(exception.message?.contains("tools") == true)
    }

    @Test
    fun `toolChoice without sampling tools capability throws InvalidParams`() {
        val request = CreateMessageRequest(
            CreateMessageRequestParams(
                maxTokens = 10,
                messages = minimalMessages,
                toolChoice = ToolChoice(mode = ToolChoice.Mode.Required),
            ),
        )
        val exception = assertFailsWith<McpException> {
            validateSamplingToolsCapability(request, noToolsCaps)
        }
        assertEquals(RPCError.ErrorCode.INVALID_PARAMS, exception.code)
        check(exception.message?.contains("toolChoice") == true)
    }

    @Test
    fun `tools with sampling tools capability is accepted`() {
        val request = CreateMessageRequest(
            CreateMessageRequestParams(
                maxTokens = 10,
                messages = minimalMessages,
                tools = listOf(dummyTool),
            ),
        )
        assertDoesNotThrow { validateSamplingToolsCapability(request, withToolsCaps) }
    }

    @Test
    fun `toolChoice with sampling tools capability is accepted`() {
        val request = CreateMessageRequest(
            CreateMessageRequestParams(
                maxTokens = 10,
                messages = minimalMessages,
                toolChoice = ToolChoice(mode = ToolChoice.Mode.Auto),
            ),
        )
        assertDoesNotThrow { validateSamplingToolsCapability(request, withToolsCaps) }
    }
}
