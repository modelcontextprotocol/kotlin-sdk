package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalMcpApi::class)
class ResultTypeTest {

    @Test
    fun `an empty result should stay an empty object`() {
        // Deployed peers validate empty results strictly and reject unknown keys, so this is the one
        // result that must be able to say nothing at all.
        McpJson.encodeToString(EmptyResult()) shouldEqualJson "{}"
        assertNull(EmptyResult().resultType)
    }

    @Test
    fun `an empty result should be able to declare its type explicitly`() {
        McpJson.encodeToString(EmptyResult(resultType = COMPLETE_RESULT_TYPE)) shouldEqualJson
            """{"resultType": "complete"}"""
    }

    @Test
    fun `every other result should declare itself complete by default`() {
        val results: List<RequestResult> = listOf(
            CallToolResult(content = emptyList()),
            ListToolsResult(tools = emptyList()),
            GetPromptResult(messages = emptyList()),
            ListPromptsResult(prompts = emptyList()),
            ReadResourceResult(contents = emptyList()),
            ListResourcesResult(resources = emptyList()),
            ListResourceTemplatesResult(resourceTemplates = emptyList()),
            CompleteResult(completion = CompleteResult.Completion(values = emptyList())),
            ListRootsResult(roots = emptyList()),
            ElicitResult(action = ElicitResult.Action.Decline),
            ListTasksResult(tasks = emptyList()),
            DiscoverResult(supportedVersions = MODERN_PROTOCOL_VERSIONS, capabilities = ServerCapabilities()),
            InitializeResult(
                capabilities = ServerCapabilities(),
                serverInfo = Implementation(name = "s", version = "1"),
            ),
        )

        results.forEach { result ->
            result.resultTypeOrNull shouldBe COMPLETE_RESULT_TYPE
        }
    }

    @Test
    fun `a result that omits its type should read as complete`() {
        // How every peer that predates the field reads, so absence can never mean anything else.
        val decoded = McpJson.decodeFromString<ServerResult>("""{"tools": []}""")

        assertIs<ListToolsResult>(decoded)
        decoded.resultType shouldBe COMPLETE_RESULT_TYPE
    }

    @Test
    fun `an empty result should be recognized whether or not it declares its type`() {
        listOf("{}", """{"resultType": "complete"}""", """{"_meta": {"a": 1}}""").forEach { wire ->
            assertIs<EmptyResult>(McpJson.decodeFromString<RequestResult>(wire))
        }
    }

    @Test
    fun `a result with a payload should never be mistaken for an empty one`() {
        val decoded = McpJson.decodeFromString<RequestResult>("""{"resultType": "complete", "roots": []}""")

        assertIs<ListRootsResult>(decoded)
    }

    @Test
    fun `an unrecognized result type should be refused only where it can occur`() {
        val result = CallToolResult(content = emptyList(), resultType = "input_required")

        // A lifecycle that predates the field cannot carry an unknown value, so nothing to refuse.
        checkInboundResult(ProtocolEra.Legacy, result)

        val failure = assertFailsWith<McpException> { checkInboundResult(ProtocolEra.Modern, result) }
        failure.code shouldBe RPCError.ErrorCode.INVALID_PARAMS
        failure.message.orEmpty() shouldContain "input_required"
    }

    @Test
    fun `a recognized or absent result type should pass in either lifecycle`() {
        ProtocolEra.entries.forEach { era ->
            checkInboundResult(era, CallToolResult(content = emptyList()))
            checkInboundResult(era, EmptyResult())
        }
    }

    @Test
    fun `an opaque payload should report whatever type it carries`() {
        val complete = GetTaskPayloadResult(McpJson.decodeFromString<JsonObject>("""{"resultType": "complete"}"""))
        val silent = GetTaskPayloadResult(McpJson.decodeFromString<JsonObject>("""{"content": []}"""))
        val illTyped = GetTaskPayloadResult(McpJson.decodeFromString<JsonObject>("""{"resultType": 7}"""))

        complete.resultTypeOrNull shouldBe COMPLETE_RESULT_TYPE
        assertNull(silent.resultTypeOrNull)
        assertNull(illTyped.resultTypeOrNull)
    }
}
