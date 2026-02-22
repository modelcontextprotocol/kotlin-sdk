package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.buildGetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.buildListPromptsResult
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

/**
 * Tests for PromptsResult DSL builders.
 *
 * Verifies GetPromptResult and ListPromptsResult can be constructed via DSL,
 * covering minimal (required only), full (all fields), and edge cases.
 */
@OptIn(ExperimentalMcpApi::class)
class PromptsResultDslTest {

    // ========================================================================
    // GetPromptResult Tests
    // ========================================================================

    @Test
    fun `GetPromptResult should build minimal with single message`() {
        val result = buildGetPromptResult {
            message(Role.User, TextContent("Hello, how can I help you?"))
        }

        result.messages shouldHaveSize 1
        result.messages[0].role shouldBe Role.User
        (result.messages[0].content as TextContent).text shouldBe "Hello, how can I help you?"
        result.description.shouldBeNull()
        result.meta.shouldBeNull()
    }

    @Test
    fun `GetPromptResult should build full with multiple messages and description`() {
        val result = buildGetPromptResult {
            description = "A customer service greeting prompt with context"

            message(Role.User, TextContent("You are a helpful customer service assistant."))
            message(Role.User, TextContent("I need help with my order."))
            message(Role.Assistant, TextContent("I'd be happy to help! Could you provide your order number?"))

            meta {
                put("category", "customer-service")
                put("language", "en")
                put("version", 2)
            }
        }

        result.messages shouldHaveSize 3
        result.messages[0].role shouldBe Role.User
        result.messages[1].role shouldBe Role.User
        result.messages[2].role shouldBe Role.Assistant

        result.description shouldBe "A customer service greeting prompt with context"

        result.meta shouldNotBeNull {
            get("category")?.jsonPrimitive?.content shouldBe "customer-service"
            get("language")?.jsonPrimitive?.content shouldBe "en"
            get("version")?.jsonPrimitive?.content shouldBe "2"
        }
    }

    @Test
    fun `GetPromptResult should throw if no messages provided`() {
        shouldThrow<IllegalArgumentException> {
            buildGetPromptResult { }
        }
    }

    // ========================================================================
    // ListPromptsResult Tests
    // ========================================================================

    @Test
    fun `ListPromptsResult should build minimal with single prompt`() {
        val result = buildListPromptsResult {
            prompt {
                name = "greeting"
            }
        }

        result.prompts shouldHaveSize 1
        result.prompts[0].name shouldBe "greeting"
        result.nextCursor.shouldBeNull()
        result.meta.shouldBeNull()
    }

    @Test
    fun `ListPromptsResult should build full with multiple prompts and pagination`() {
        val result = buildListPromptsResult {
            prompt {
                name = "greeting"
                description = "A friendly greeting prompt"
                title = "Friendly Greeting"
                arguments = listOf(
                    PromptArgument(name = "userName", description = "The user's name", required = true),
                    PromptArgument(name = "language", description = "Preferred language", required = false),
                )
                meta {
                    put("category", "greetings")
                }
            }

            prompt {
                name = "farewell"
                description = "A polite farewell prompt"
                title = "Polite Farewell"
            }

            prompt {
                name = "product-recommendation"
                description = "Recommends products based on user preferences"
                arguments = listOf(
                    PromptArgument(name = "userId", required = true),
                    PromptArgument(name = "category", required = false),
                )
            }

            nextCursor = "eyJwYWdlIjogMn0="

            meta {
                put("serverVersion", "2.0")
                put("totalPrompts", 50)
            }
        }

        result.prompts shouldHaveSize 3

        result.prompts[0].let { prompt ->
            prompt.name shouldBe "greeting"
            prompt.description shouldBe "A friendly greeting prompt"
            prompt.title shouldBe "Friendly Greeting"
            prompt.arguments?.let { args ->
                args shouldHaveSize 2
                args[0].name shouldBe "userName"
                args[0].required shouldBe true
                args[1].name shouldBe "language"
                args[1].required shouldBe false
            }
        }

        result.prompts[1].name shouldBe "farewell"
        result.prompts[2].name shouldBe "product-recommendation"

        result.nextCursor shouldBe "eyJwYWdlIjogMn0="

        result.meta shouldNotBeNull {
            get("serverVersion")?.jsonPrimitive?.content shouldBe "2.0"
            get("totalPrompts")?.jsonPrimitive?.content shouldBe "50"
        }
    }

    @Test
    fun `ListPromptsResult should throw if no prompts provided`() {
        shouldThrow<IllegalArgumentException> {
            buildListPromptsResult { }
        }
    }

    @Test
    fun `ListPromptsResult should support prompt without arguments`() {
        val result = buildListPromptsResult {
            prompt {
                name = "simplePrompt"
                description = "A prompt with no arguments"
            }
        }

        result.prompts[0].arguments.shouldBeNull()
    }

    @Test
    fun `ListPromptsResult should support empty arguments list`() {
        val result = buildListPromptsResult {
            prompt {
                name = "emptyArgs"
                arguments = emptyList()
            }
        }

        result.prompts[0].arguments?.let { it shouldHaveSize 0 }
    }
}
