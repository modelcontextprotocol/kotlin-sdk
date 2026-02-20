package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
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
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

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

        assertSoftly(result) {
            isError shouldBe true
            (content.single() as TextContent).text shouldBe "Tool nonexistent not found"
        }
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
        server.addTool("greet", "Greeting tool") { request ->
            val name = request.params.arguments?.get("name")?.jsonPrimitive?.content ?: "stranger"
            CallToolResult(listOf(TextContent("Hello, $name!")))
        }

        val result = client.callTool(
            CallToolRequest(
                CallToolRequestParams(
                    name = "greet",
                    arguments = JsonObject(emptyMap()),
                ),
            ),
        )

        (result.content.single() as TextContent).text shouldBe "Hello, stranger!"
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
    fun `getPrompt should use default when arguments are absent`() = runTest {
        server.addPrompt("templated", "Templated prompt") { request ->
            val topic = request.params.arguments?.get("topic") ?: "unknown"
            GetPromptResult(
                messages = listOf(
                    PromptMessage(role = Role.User, content = TextContent("Tell me about $topic")),
                ),
            )
        }

        val result = client.getPrompt(GetPromptRequest(GetPromptRequestParams("templated")))

        (result.messages.single().content as TextContent).text shouldBe "Tell me about unknown"
    }

    @Test
    fun `getPrompt should throw when prompt not found`() = runTest {
        assertFailsWith<McpException> {
            client.getPrompt(GetPromptRequest(GetPromptRequestParams("nonexistent")))
        }
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
        assertFailsWith<McpException> {
            client.readResource(ReadResourceRequest(ReadResourceRequestParams("test://nonexistent")))
        }
    }
}
