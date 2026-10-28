package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

class ServerResourceTemplateTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(
        resources = ServerCapabilities.Resources(listChanged = null, subscribe = null),
    )

    @Test
    fun `readResource should match URI against template and invoke handler`() = runTest {
        server.addResourceTemplate("test://items/{itemId}", "Item", mimeType = "text/plain") { request, variables ->
            val itemId = variables["itemId"] ?: "unknown"
            ReadResourceResult(
                listOf(TextResourceContents(text = "item=$itemId", uri = request.uri, mimeType = "text/plain")),
            )
        }

        val result = client.readResource(ReadResourceRequest(ReadResourceRequestParams("test://items/42")))

        result.contents shouldBe
            listOf(TextResourceContents(uri = "test://items/42", mimeType = "text/plain", text = "item=42"))
    }

    @Test
    fun `readResource should extract multiple URI template variables`() = runTest {
        val capturedVars = CompletableDeferred<Map<String, String>>()
        server.addResourceTemplate(
            uriTemplate = "test://users/{userId}/posts/{postId}",
            name = "User Post",
            mimeType = "text/plain",
        ) { _, variables ->
            capturedVars.complete(variables)
            ReadResourceResult(listOf(TextResourceContents("ok", "test://users/alice/posts/99")))
        }

        client.readResource(ReadResourceRequest(ReadResourceRequestParams("test://users/alice/posts/99")))

        val vars = capturedVars.await()
        vars shouldContainKey "userId"
        vars shouldContainKey "postId"
        vars["userId"] shouldBe "alice"
        vars["postId"] shouldBe "99"
    }

    @Test
    fun `readResource should prefer exact resource match over template`() = runTest {
        var exactHandlerCalled = false
        server.addResource("test://items/special", "Special Item", "An exact resource") {
            exactHandlerCalled = true
            ReadResourceResult(listOf(TextResourceContents("exact", "test://items/special")))
        }
        server.addResourceTemplate("test://items/{itemId}", "Item Template") { _, _ ->
            ReadResourceResult(listOf(TextResourceContents("template", "test://items/special")))
        }

        val result = client.readResource(ReadResourceRequest(ReadResourceRequestParams("test://items/special")))

        exactHandlerCalled shouldBe true
        (result.contents[0] as TextResourceContents).text shouldBe "exact"
    }

    @Test
    fun `readResource should select most specific template when multiple match`() = runTest {
        // "test://users/profile" has more literal chars than "test://users/{id}" — should win
        server.addResourceTemplate("test://users/{id}", "Generic User") { _, variables ->
            ReadResourceResult(listOf(TextResourceContents("generic:${variables["id"]}", "test://users/profile")))
        }
        server.addResourceTemplate("test://users/profile", "Profile") { _, _ ->
            ReadResourceResult(listOf(TextResourceContents("profile-page", "test://users/profile")))
        }

        val result = client.readResource(ReadResourceRequest(ReadResourceRequestParams("test://users/profile")))

        (result.contents[0] as TextResourceContents).text shouldBe "profile-page"
    }

    @Test
    fun `readResource should return RESOURCE_NOT_FOUND error when no template matches`() = runTest {
        server.addResourceTemplate("test://items/{itemId}", "Item") { _, _ -> ReadResourceResult(emptyList()) }

        val exception = shouldThrow<McpException> {
            client.readResource(ReadResourceRequest(ReadResourceRequestParams("test://users/42")))
        }

        exception.code shouldBe RPCError.ErrorCode.RESOURCE_NOT_FOUND
        exception.message shouldBe "Resource not found"
        exception.data shouldBe buildJsonObject { put("uri", "test://users/42") }
    }

    @Test
    fun `resourceTemplates property should reflect registered templates`() {
        server.addResourceTemplate(ResourceTemplate("test://a/{x}", "A")) { _, _ ->
            ReadResourceResult(emptyList())
        }
        server.addResourceTemplate(ResourceTemplate("test://b/{y}", "B")) { _, _ ->
            ReadResourceResult(emptyList())
        }

        val templates = server.resourceTemplates

        templates shouldBe listOf(
            ResourceTemplate("test://a/{x}", "A"),
            ResourceTemplate("test://b/{y}", "B"),
        )
    }

    @Test
    fun `removeResourceTemplate should remove a registered template`() {
        server.addResourceTemplate("test://items/{id}", "Item") { _, _ ->
            ReadResourceResult(emptyList())
        }

        val removed = server.removeResourceTemplate("test://items/{id}")

        removed shouldBe true
        server.resourceTemplates.size shouldBe 0
    }

    @Test
    fun `listResourceTemplates should include description from template`() = runTest {
        server.addResourceTemplate(
            uriTemplate = "test://data/{id}",
            name = "Data",
            description = "Parameterized data resource",
            mimeType = "application/json",
        ) { _, _ ->
            ReadResourceResult(emptyList())
        }

        val result = client.listResourceTemplates(ListResourceTemplatesRequest())

        result.resourceTemplates shouldBe listOf(
            ResourceTemplate(
                name = "Data",
                uriTemplate = "test://data/{id}",
                description = "Parameterized data resource",
                mimeType = "application/json",
            ),
        )
    }
}
