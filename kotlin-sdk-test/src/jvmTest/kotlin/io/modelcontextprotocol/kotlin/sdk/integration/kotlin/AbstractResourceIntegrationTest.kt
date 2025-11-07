package io.modelcontextprotocol.kotlin.sdk.integration.kotlin

import io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.UnsubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.UnsubscribeRequestParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class AbstractResourceIntegrationTest : KotlinTestBase() {

    private val testResourceUri = "test://example.txt"
    private val testResourceName = "Test Resource"
    private val testResourceDescription = "A test resource for integration testing"
    private val testResourceContent = "This is the content of the test resource."

    private val binaryResourceUri = "test://image.png"
    private val binaryResourceName = "Binary Resource"
    private val binaryResourceDescription = "A binary resource for testing"
    private val binaryResourceContent =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

    private val largeResourceUri = "test://large.txt"
    private val largeResourceName = "Large Resource"
    private val largeResourceDescription = "A large text resource for testing"
    private val largeResourceContent = "X".repeat(100_000) // 100KB of data

    private val dynamicResourceUri = "test://dynamic.txt"
    private val dynamicResourceName = "Dynamic Resource"
    private val dynamicResourceContent = AtomicBoolean(false)

    override fun configureServerCapabilities(): ServerCapabilities = ServerCapabilities(
        resources = ServerCapabilities.Resources(
            subscribe = true,
            listChanged = true,
        ),
    )

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
                        uri = request.params.uri,
                        mimeType = "text/plain",
                    ),
                ),
            )
        }

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
                        uri = request.params.uri,
                        mimeType = "text/plain",
                    ),
                ),
            )
        }

        server.addResource(
            uri = binaryResourceUri,
            name = binaryResourceName,
            description = binaryResourceDescription,
            mimeType = "image/png",
        ) { request ->
            ReadResourceResult(
                contents = listOf(
                    BlobResourceContents(
                        blob = binaryResourceContent,
                        uri = request.params.uri,
                        mimeType = "image/png",
                    ),
                ),
            )
        }

        server.addResource(
            uri = largeResourceUri,
            name = largeResourceName,
            description = largeResourceDescription,
            mimeType = "text/plain",
        ) { request ->
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = largeResourceContent,
                        uri = request.params.uri,
                        mimeType = "text/plain",
                    ),
                ),
            )
        }

        server.addResource(
            uri = dynamicResourceUri,
            name = dynamicResourceName,
            description = "A resource that can be updated",
            mimeType = "text/plain",
        ) { request ->
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = if (dynamicResourceContent.get()) "Updated content" else "Original content",
                        uri = request.params.uri,
                        mimeType = "text/plain",
                    ),
                ),
            )
        }
    }

    @Test
    fun testListResources() = runBlocking(Dispatchers.IO) {
        val result = client.listResources()

        assertNotNull(result, "List resources result should not be null")
        assertTrue(result.resources.isNotEmpty(), "Resources list should not be empty")

        val testResource = result.resources.find { it.uri == testResourceUri }
        assertNotNull(testResource, "Test resource should be in the list")
        assertEquals(testResourceName, testResource.name, "Resource name should match")
        assertEquals(testResourceDescription, testResource.description, "Resource description should match")
    }

    @Test
    fun testReadResource() = runBlocking(Dispatchers.IO) {
        val result = client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = testResourceUri)))

        assertNotNull(result, "Read resource result should not be null")
        assertTrue(result.contents.isNotEmpty(), "Resource contents should not be empty")

        val content = result.contents.firstOrNull() as? TextResourceContents
        assertNotNull(content, "Resource content should be TextResourceContents")
        assertEquals(testResourceContent, content.text, "Resource content should match")
    }

    @Ignore("Blocked by https://github.com/modelcontextprotocol/kotlin-sdk/issues/249")
    @Test
    fun testSubscribeAndUnsubscribe() {
        runBlocking(Dispatchers.IO) {
            val subscribeResult =
                client.subscribeResource(SubscribeRequest(SubscribeRequestParams(uri = testResourceUri)))
            assertNotNull(subscribeResult, "Subscribe result should not be null")

            val unsubscribeResult =
                client.unsubscribeResource(UnsubscribeRequest(UnsubscribeRequestParams(uri = testResourceUri)))
            assertNotNull(unsubscribeResult, "Unsubscribe result should not be null")
        }
    }

    @Test
    fun testBinaryResource() = runBlocking(Dispatchers.IO) {
        val result = client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = binaryResourceUri)))

        assertNotNull(result, "Read resource result should not be null")
        assertTrue(result.contents.isNotEmpty(), "Resource contents should not be empty")

        val content = result.contents.firstOrNull() as? BlobResourceContents
        assertNotNull(content, "Resource content should be BlobResourceContents")
        assertEquals(binaryResourceContent, content.blob, "Binary resource content should match")
        assertEquals("image/png", content.mimeType, "MIME type should match")
    }

    @Test
    fun testLargeResource() = runBlocking(Dispatchers.IO) {
        val result = client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = largeResourceUri)))

        assertNotNull(result, "Read resource result should not be null")
        assertTrue(result.contents.isNotEmpty(), "Resource contents should not be empty")

        val content = result.contents.firstOrNull() as? TextResourceContents
        assertNotNull(content, "Resource content should be TextResourceContents")
        assertEquals(100_000, content.text.length, "Large resource content length should match")
        assertEquals("X".repeat(100_000), content.text, "Large resource content should match")
    }

    @Test
    fun testInvalidResourceUri() = runTest {
        val invalidUri = "test://nonexistent.txt"

        val exception = assertThrows<IllegalStateException> {
            runBlocking {
                client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = invalidUri)))
            }
        }

        val msg = exception.message ?: ""
        val expectedMessage =
            "JSONRPCError(code=InternalError, message=Resource not found: test://nonexistent.txt, data={})"

        assertEquals(expectedMessage, msg, "Unexpected error message for invalid resource URI")
    }

    @Test
    fun testDynamicResource() = runBlocking(Dispatchers.IO) {
        val initialResult =
            client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = dynamicResourceUri)))
        assertNotNull(initialResult, "Initial read result should not be null")
        val initialContent = (initialResult.contents.firstOrNull() as? TextResourceContents)?.text
        assertEquals("Original content", initialContent, "Initial content should match")

        // update resource
        dynamicResourceContent.set(true)

        val updatedResult =
            client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = dynamicResourceUri)))
        assertNotNull(updatedResult, "Updated read result should not be null")
        val updatedContent = (updatedResult.contents.firstOrNull() as? TextResourceContents)?.text
        assertEquals("Updated content", updatedContent, "Updated content should match")
    }

    @Test
    fun testResourceAddAndRemove() = runBlocking(Dispatchers.IO) {
        val initialList = client.listResources()
        assertNotNull(initialList, "Initial list result should not be null")
        val initialCount = initialList.resources.size

        val newResourceUri = "test://new-resource.txt"
        server.addResource(
            uri = newResourceUri,
            name = "New Resource",
            description = "A newly added resource",
            mimeType = "text/plain",
        ) { request ->
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = "New resource content",
                        uri = request.params.uri,
                        mimeType = "text/plain",
                    ),
                ),
            )
        }

        val updatedList = client.listResources()
        assertNotNull(updatedList, "Updated list result should not be null")
        val updatedCount = updatedList.resources.size

        assertEquals(initialCount + 1, updatedCount, "Resource count should increase by 1")
        val newResource = updatedList.resources.find { it.uri == newResourceUri }
        assertNotNull(newResource, "New resource should be in the list")

        server.removeResource(newResourceUri)

        val finalList = client.listResources()
        assertNotNull(finalList, "Final list result should not be null")
        val finalCount = finalList.resources.size

        assertEquals(initialCount, finalCount, "Resource count should return to initial value")
        val removedResource = finalList.resources.find { it.uri == newResourceUri }
        assertEquals(null, removedResource, "Resource should be removed from the list")
    }

    @Test
    fun testConcurrentResourceOperations() = runTest {
        val concurrentCount = 10
        val results = mutableListOf<ReadResourceResult?>()

        runBlocking {
            repeat(concurrentCount) { index ->
                launch {
                    val uri = when (index % 3) {
                        0 -> testResourceUri
                        1 -> binaryResourceUri
                        else -> largeResourceUri
                    }

                    val result = client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = uri)))
                    synchronized(results) {
                        results.add(result)
                    }
                }
            }
        }

        assertEquals(concurrentCount, results.size, "All concurrent operations should complete")
        results.forEach { result ->
            assertNotNull(result, "Result should not be null")
            assertTrue(result.contents.isNotEmpty(), "Result contents should not be empty")
        }
    }
}
