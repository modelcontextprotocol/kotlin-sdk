package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.security

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.KotlinTestBase
import io.modelcontextprotocol.kotlin.sdk.integration.utils.AuthorizationRules
import io.modelcontextprotocol.kotlin.sdk.integration.utils.MockAuthorizationWrapper
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class AbstractResourceSecurityIntegrationTest : KotlinTestBase() {

    private val publicResourceUri = "test://public-resource.txt"
    private val secretResourceUri = "test://secret-resource.txt"
    private val restrictedResourceUri = "test://restricted-resource.txt"

    override fun configureServerCapabilities(): ServerCapabilities = ServerCapabilities(
        resources = ServerCapabilities.Resources(
            subscribe = true,
            listChanged = true,
        ),
    )

    override fun configureServer() {
        configureServerWithAuthorization(
            allowedResources = setOf(publicResourceUri, restrictedResourceUri),
        )
    }

    protected fun configureServerWithAuthorization(
        allowedResources: Set<String>? = null,
        deniedResources: Set<String>? = null,
    ) {
        val authWrapper = MockAuthorizationWrapper(
            AuthorizationRules(
                allowedResources = allowedResources,
                deniedResources = deniedResources,
            ),
        )

        server.addResource(
            uri = publicResourceUri,
            name = "Public Resource",
            description = "A public resource that authorized users can access",
            mimeType = "text/plain",
        ) { request ->
            if (!authWrapper.isAllowed("resources", "read", mapOf("uri" to publicResourceUri))) {
                throw authWrapper.createDeniedError("Access denied to resource: $publicResourceUri")
            }

            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = "Public resource content",
                        uri = request.params.uri,
                        mimeType = "text/plain",
                    ),
                ),
            )
        }

        server.addResource(
            uri = secretResourceUri,
            name = "Secret Resource",
            description = "A secret resource that requires special permissions",
            mimeType = "text/plain",
        ) { request ->
            if (!authWrapper.isAllowed("resources", "read", mapOf("uri" to secretResourceUri))) {
                throw authWrapper.createDeniedError("Access denied to resource: $secretResourceUri")
            }

            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = "Secret resource content",
                        uri = request.params.uri,
                        mimeType = "text/plain",
                    ),
                ),
            )
        }

        server.addResource(
            uri = restrictedResourceUri,
            name = "Restricted Resource",
            description = "A restricted resource",
            mimeType = "text/plain",
        ) { request ->
            if (!authWrapper.isAllowed("resources", "read", mapOf("uri" to restrictedResourceUri))) {
                throw authWrapper.createDeniedError("Access denied to resource: $restrictedResourceUri")
            }

            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = "Restricted resource content",
                        uri = request.params.uri,
                        mimeType = "text/plain",
                    ),
                ),
            )
        }
    }

    @Test
    fun testListResourcesAllowed() = runBlocking {
        val result = client.listResources()

        assertNotNull(result, "List resources result should not be null")
        assertTrue(result.resources.isNotEmpty(), "Resources list should not be empty")

        val publicResource = result.resources.find { it.uri == publicResourceUri }
        assertNotNull(publicResource, "Public resource should be in the list")
        assertEquals("Public Resource", publicResource.name)
    }

    @Test
    fun testListResourcesDenied() {
        runBlocking {
            val result = client.listResources()
            assertNotNull(result, "List should still work in default configuration")
        }
    }

    @Test
    fun testReadResourceAllowed() = runBlocking {
        val result = client.readResource(
            ReadResourceRequest(
                ReadResourceRequestParams(
                    uri = publicResourceUri,
                ),
            ),
        )

        assertNotNull(result, "Read resource result should not be null")
        assertTrue(result.contents.isNotEmpty(), "Contents should not be empty")

        val content = result.contents.first() as TextResourceContents
        assertEquals("Public resource content", content.text)
        assertEquals("text/plain", content.mimeType)
    }

    @Test
    fun testReadResourceDenied() {
        val exception = assertThrows<McpException> {
            runBlocking {
                client.readResource(
                    ReadResourceRequest(
                        ReadResourceRequestParams(
                            uri = secretResourceUri,
                        ),
                    ),
                )
            }
        }

        withClue("Exception message should mention access denied") {
            exception.message?.lowercase()?.contains("access denied") shouldBe true
        }
    }

    @Test
    fun testReadResourcePartialAccess(): Unit = runBlocking {
        val publicResult = client.readResource(
            ReadResourceRequest(
                ReadResourceRequestParams(
                    uri = publicResourceUri,
                ),
            ),
        )
        assertNotNull(publicResult, "Public resource should be accessible")

        val exception = assertThrows<McpException> {
            runBlocking {
                client.readResource(
                    ReadResourceRequest(
                        ReadResourceRequestParams(
                            uri = secretResourceUri,
                        ),
                    ),
                )
            }
        }
        withClue("Should be denied access to secret resource") {
            exception.message?.lowercase()?.contains("access denied") shouldBe true
        }

        val restrictedResult = client.readResource(
            ReadResourceRequest(
                ReadResourceRequestParams(
                    uri = restrictedResourceUri,
                ),
            ),
        )
        assertNotNull(restrictedResult, "Restricted resource should be accessible with proper permissions")
    }

    @Test
    fun testUnauthorizedAfterInitialization(): Unit = runBlocking {
        val exception = assertThrows<McpException> {
            runBlocking {
                client.readResource(
                    ReadResourceRequest(
                        ReadResourceRequestParams(
                            uri = secretResourceUri,
                        ),
                    ),
                )
            }
        }

        withClue("Unauthorized operations should fail after successful initialization") {
            exception.message?.lowercase()?.contains("access denied") shouldBe true
        }
    }
}
