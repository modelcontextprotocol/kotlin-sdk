package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.security

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.KotlinTestBase
import io.modelcontextprotocol.kotlin.sdk.integration.utils.AuthorizationRules
import io.modelcontextprotocol.kotlin.sdk.integration.utils.MockAuthorizationWrapper
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class AbstractPromptSecurityIntegrationTest : KotlinTestBase() {

    private val publicPromptName = "public-prompt"
    private val secretPromptName = "secret-prompt"
    private val restrictedPromptName = "restricted-prompt"

    override fun configureServerCapabilities(): ServerCapabilities = ServerCapabilities(
        prompts = ServerCapabilities.Prompts(
            listChanged = true,
        ),
    )

    override fun configureServer() {
        configureServerWithAuthorization(
            allowedPrompts = setOf(publicPromptName, restrictedPromptName),
        )
    }

    protected fun configureServerWithAuthorization(
        allowedPrompts: Set<String>? = null,
        deniedPrompts: Set<String>? = null,
    ) {
        val authWrapper = MockAuthorizationWrapper(
            AuthorizationRules(
                allowedPrompts = allowedPrompts,
                deniedPrompts = deniedPrompts,
            ),
        )

        server.addPrompt(
            name = publicPromptName,
            description = "A public prompt that authorized users can access",
            arguments = listOf(
                PromptArgument(
                    name = "query",
                    description = "The query to process",
                    required = false,
                ),
            ),
        ) { request ->
            if (!authWrapper.isAllowed("prompts", "get", mapOf("name" to publicPromptName))) {
                throw authWrapper.createDeniedError("Access denied to prompt: $publicPromptName")
            }

            val query = request.params.arguments?.get("query") ?: "default query"

            GetPromptResult(
                description = "Public prompt response",
                messages = listOf(
                    PromptMessage(
                        role = Role.User,
                        content = TextContent(text = "Query: $query"),
                    ),
                ),
            )
        }

        server.addPrompt(
            name = secretPromptName,
            description = "A secret prompt that requires special permissions",
            arguments = listOf(),
        ) { request ->
            if (!authWrapper.isAllowed("prompts", "get", mapOf("name" to secretPromptName))) {
                throw authWrapper.createDeniedError("Access denied to prompt: $secretPromptName")
            }

            GetPromptResult(
                description = "Secret prompt response",
                messages = listOf(
                    PromptMessage(
                        role = Role.User,
                        content = TextContent(text = "This is secret information"),
                    ),
                ),
            )
        }

        server.addPrompt(
            name = restrictedPromptName,
            description = "A restricted prompt that some users can access",
            arguments = listOf(),
        ) { request ->
            if (!authWrapper.isAllowed("prompts", "get", mapOf("name" to restrictedPromptName))) {
                throw authWrapper.createDeniedError("Access denied to prompt: $restrictedPromptName")
            }

            GetPromptResult(
                description = "Restricted prompt response",
                messages = listOf(
                    PromptMessage(
                        role = Role.User,
                        content = TextContent(text = "This is restricted information"),
                    ),
                ),
            )
        }
    }

    @Test
    fun testListPromptsAllowed() = runBlocking {
        val result = client.listPrompts()

        assertNotNull(result, "List prompts result should not be null")
        assertTrue(result.prompts.isNotEmpty(), "Prompts list should not be empty")

        val promptNames = result.prompts.map { it.name }
        assertTrue(promptNames.contains(publicPromptName), "Public prompt should be listed")
        assertTrue(promptNames.contains(secretPromptName), "Secret prompt should be listed")
        assertTrue(promptNames.contains(restrictedPromptName), "Restricted prompt should be listed")
    }

    @Test
    fun testListPromptsDenied() {
        runBlocking {
            val result = client.listPrompts()
            assertNotNull(result, "List prompts should succeed")
        }
    }

    @Test
    fun testGetPromptAllowed() = runBlocking {
        val result = client.getPrompt(
            GetPromptRequest(
                GetPromptRequestParams(
                    name = publicPromptName,
                    arguments = mapOf("query" to "test query"),
                ),
            ),
        )

        assertNotNull(result, "Get prompt result should not be null")
        assertEquals("Public prompt response", result.description)
        assertTrue(result.messages.isNotEmpty(), "Messages should not be empty")

        val userMessage = result.messages.first()
        assertEquals(Role.User, userMessage.role)
        val content = userMessage.content as TextContent
        assertTrue(content.text.contains("test query"), "Response should contain the query")
    }

    @Test
    fun testGetPromptDenied() {
        val exception = assertThrows<McpException> {
            runBlocking {
                client.getPrompt(
                    GetPromptRequest(
                        GetPromptRequestParams(
                            name = secretPromptName,
                            arguments = emptyMap(),
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
    fun testGetPromptPartialAccess(): Unit = runBlocking {
        val publicResult = client.getPrompt(
            GetPromptRequest(
                GetPromptRequestParams(
                    name = publicPromptName,
                    arguments = emptyMap(),
                ),
            ),
        )
        assertNotNull(publicResult, "Public prompt should be accessible")

        val restrictedResult = client.getPrompt(
            GetPromptRequest(
                GetPromptRequestParams(
                    name = restrictedPromptName,
                    arguments = emptyMap(),
                ),
            ),
        )
        assertNotNull(restrictedResult, "Restricted prompt should be accessible")

        val exception = assertThrows<McpException> {
            runBlocking {
                client.getPrompt(
                    GetPromptRequest(
                        GetPromptRequestParams(
                            name = secretPromptName,
                            arguments = emptyMap(),
                        ),
                    ),
                )
            }
        }

        withClue("Secret prompt should be denied") {
            exception.message?.lowercase()?.contains("access denied") shouldBe true
        }
    }

    @Test
    fun testUnauthorizedAfterInitialization(): Unit = runBlocking {
        assertNotNull(client, "Client should be initialized")

        val listResult = client.listPrompts()
        assertNotNull(listResult, "List prompts should succeed")

        val exception = assertThrows<McpException> {
            runBlocking {
                client.getPrompt(
                    GetPromptRequest(
                        GetPromptRequestParams(
                            name = secretPromptName,
                            arguments = emptyMap(),
                        ),
                    ),
                )
            }
        }

        withClue("Authorization should be checked on prompt access, not initialization") {
            exception.message?.lowercase()?.contains("access denied") shouldBe true
        }
    }
}
