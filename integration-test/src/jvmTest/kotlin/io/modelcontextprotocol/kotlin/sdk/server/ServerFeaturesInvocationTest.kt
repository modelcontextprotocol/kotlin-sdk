package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

class ServerFeaturesInvocationTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(listChanged = null),
        prompts = ServerCapabilities.Prompts(listChanged = null),
        resources = ServerCapabilities.Resources(listChanged = null, subscribe = null),
    )

    // ── Tool invocation ────────────────────────────────────────────────────────

    @Test
    fun `callTool should return tool result content`() = runTest {
        server.addTool("echo", "Echo tool") {
            CallToolResult(listOf(TextContent("echo response")))
        }

        val result = client.callTool(CallToolRequest(CallToolRequestParams("echo")))

        assertSoftly(result) {
            isError shouldNotBe true
            (content.single() as TextContent).text shouldBe "echo response"
        }
    }

    @Test
    fun `callTool should pass arguments to handler`() = runTest {
        server.addTool("greet", "Greeting tool") { request ->
            val name = request.params.arguments?.get("name")?.jsonPrimitive?.content ?: "stranger"
            CallToolResult(listOf(TextContent("Hello, $name!")))
        }

        val result = client.callTool(
            CallToolRequest(
                CallToolRequestParams(
                    name = "greet",
                    arguments = JsonObject(mapOf("name" to JsonPrimitive("World"))),
                ),
            ),
        )

        (result.content.single() as TextContent).text shouldBe "Hello, World!"
    }

    @Test
    fun `callTool should return error result when tool not found`() = runTest {
        val result = client.callTool(CallToolRequest(CallToolRequestParams("nonexistent")))

        result shouldBe CallToolResult(content = listOf(TextContent("Tool nonexistent not found")), isError = true)
    }

    @Test
    fun `callTool should return error result when handler throws`() = runTest {
        server.addTool("failing", "Failing tool") {
            throw IllegalStateException("handler failure")
        }

        val result = client.callTool(CallToolRequest(CallToolRequestParams("failing")))

        assertSoftly(result) {
            isError shouldBe true
            (content.single() as TextContent).text shouldBe "Error executing tool failing: handler failure"
        }
    }

    @Test
    fun `callTool should pass empty arguments map to handler`() = runTest {
        val received = CompletableDeferred<JsonObject?>()
        server.addTool("greet", "Greeting tool") { request ->
            received.complete(request.params.arguments)
            CallToolResult(emptyList())
        }

        client.callTool(CallToolRequest(CallToolRequestParams(name = "greet", arguments = JsonObject(emptyMap()))))

        received.await() shouldBe JsonObject(emptyMap())
    }

    // ── Prompt invocation ──────────────────────────────────────────────────────

    @Test
    fun `getPrompt should return prompt description and messages`() = runTest {
        server.addPrompt("my-prompt", "My prompt") {
            GetPromptResult(
                description = "Prompt result description",
                messages = listOf(
                    PromptMessage(role = Role.User, content = TextContent("User message")),
                ),
            )
        }

        val result = client.getPrompt(GetPromptRequest(GetPromptRequestParams("my-prompt")))

        assertSoftly(result) {
            description shouldBe "Prompt result description"
            messages shouldHaveSize 1
            (messages.single().content as TextContent).text shouldBe "User message"
        }
    }

    @Test
    fun `getPrompt should pass arguments to handler`() = runTest {
        server.addPrompt("templated", "Templated prompt") { request ->
            val topic = request.params.arguments?.get("topic") ?: "unknown"
            GetPromptResult(
                messages = listOf(
                    PromptMessage(role = Role.User, content = TextContent("Tell me about $topic")),
                ),
            )
        }

        val result = client.getPrompt(
            GetPromptRequest(
                GetPromptRequestParams(
                    name = "templated",
                    arguments = mapOf("topic" to "Kotlin coroutines"),
                ),
            ),
        )

        (result.messages.single().content as TextContent).text shouldBe "Tell me about Kotlin coroutines"
    }

    @Test
    fun `getPrompt should pass absent arguments as null`() = runTest {
        val received = CompletableDeferred<Map<String, String>?>()
        server.addPrompt("templated", "Templated prompt") { request ->
            received.complete(request.params.arguments)
            GetPromptResult(messages = emptyList())
        }

        client.getPrompt(GetPromptRequest(GetPromptRequestParams("templated")))

        received.await().shouldBeNull()
    }

    @Test
    fun `getPrompt should throw when prompt not found`() = runTest {
        val exception = shouldThrow<McpException> {
            client.getPrompt(GetPromptRequest(GetPromptRequestParams("nonexistent")))
        }

        exception.code shouldBe RPCError.ErrorCode.INVALID_PARAMS
        exception.message shouldBe "Prompt not found: nonexistent"
    }

    // ── Resource invocation ────────────────────────────────────────────────────

    @Test
    fun `readResource should return resource content`() = runTest {
        val uri = "test://my-resource"
        server.addResource(uri, "My Resource", "Test resource") {
            ReadResourceResult(
                contents = listOf(TextResourceContents(text = "resource content", uri = uri)),
            )
        }

        val result = client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri)))

        (result.contents.single() as TextResourceContents).text shouldBe "resource content"
    }

    @Test
    fun `readResource should pass request URI to handler`() = runTest {
        val uri = "test://uri-check"
        val receivedUri = CompletableDeferred<String>()
        server.addResource(uri, "URI Check", "Test") { request ->
            receivedUri.complete(request.params.uri)
            ReadResourceResult(
                contents = listOf(TextResourceContents(text = "ok", uri = uri)),
            )
        }

        client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri)))

        receivedUri.await() shouldBe uri
    }

    @Test
    fun `readResource should throw when resource not found`() = runTest {
        val exception = shouldThrow<McpException> {
            client.readResource(ReadResourceRequest(ReadResourceRequestParams("test://nonexistent")))
        }

        exception.code shouldBe RPCError.ErrorCode.RESOURCE_NOT_FOUND
        exception.message shouldBe "Resource not found"
        exception.data shouldBe buildJsonObject { put("uri", "test://nonexistent") }
    }
}
