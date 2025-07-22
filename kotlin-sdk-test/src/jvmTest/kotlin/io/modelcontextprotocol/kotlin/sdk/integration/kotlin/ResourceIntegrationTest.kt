package io.modelcontextprotocol.kotlin.sdk.integration.kotlin

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.integration.utils.TestUtils.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ResourceIntegrationTest : KotlinTestBase() {

    override val port = 3005
    private val testResourceUri = "test://example.txt"
    private val testResourceName = "Test Resource"
    private val testResourceDescription = "A test resource for integration testing"
    private val testResourceContent = "This is the content of the test resource."

    override fun configureServerCapabilities(): ServerCapabilities {
        return ServerCapabilities(
            resources = ServerCapabilities.Resources(
                subscribe = true,
                listChanged = true,
            )
        )
    }

    override fun configureServer() {
        server.addResource(
            uri = testResourceUri,
            name = testResourceName,
            description = testResourceDescription,
            mimeType = "text/plain",
        ) { request ->
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = testResourceContent,
                        uri = request.uri,
                        mimeType = "text/plain",
                    )
                )
            )
        }

        server.setRequestHandler<SubscribeRequest>(Method.Defined.ResourcesSubscribe) { _, _ ->
            EmptyRequestResult()
        }

        server.setRequestHandler<UnsubscribeRequest>(Method.Defined.ResourcesUnsubscribe) { _, _ ->
            EmptyRequestResult()
        }
    }

    @Test
    fun testListResources() = runTest {
        val result = client.listResources()

        assertNotNull(result, "List resources result should not be null")
        assertTrue(result.resources.isNotEmpty(), "Resources list should not be empty")

        val testResource = result.resources.find { it.uri == testResourceUri }
        assertNotNull(testResource, "Test resource should be in the list")
        assertEquals(testResourceName, testResource.name, "Resource name should match")
        assertEquals(testResourceDescription, testResource.description, "Resource description should match")
    }

    @Test
    fun testReadResource() = runTest {
        val result = client.readResource(ReadResourceRequest(uri = testResourceUri))

        assertNotNull(result, "Read resource result should not be null")
        assertTrue(result.contents.isNotEmpty(), "Resource contents should not be empty")

        val content = result.contents.firstOrNull() as? TextResourceContents
        assertNotNull(content, "Resource content should be TextResourceContents")
        assertEquals(testResourceContent, content.text, "Resource content should match")
    }

    @Test
    fun testSubscribeAndUnsubscribe() {
        runTest {
            val subscribeResult = client.subscribeResource(SubscribeRequest(uri = testResourceUri))
            assertNotNull(subscribeResult, "Subscribe result should not be null")

            val unsubscribeResult = client.unsubscribeResource(UnsubscribeRequest(uri = testResourceUri))
            assertNotNull(unsubscribeResult, "Unsubscribe result should not be null")
        }
    }
}