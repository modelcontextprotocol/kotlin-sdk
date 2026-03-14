package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.schema.json.ArrayPropertyDefinition
import kotlinx.schema.json.FunctionCallingSchema
import kotlinx.schema.json.ObjectPropertyDefinition
import kotlinx.schema.json.StringPropertyDefinition
import kotlin.test.Test

class FunctionCallingSchemaAsToolSchemaTest {

    @Test
    fun `should convert function parameters`() {
        val functionSchema = FunctionCallingSchema(
            name = "calculate",
            description = "Perform calculation",
            parameters = ObjectPropertyDefinition(
                properties = mapOf(
                    "operation" to StringPropertyDefinition(
                        description = "Math operation",
                    ),
                    "x" to StringPropertyDefinition(),
                    "y" to StringPropertyDefinition(),
                ),
                required = listOf("operation", "x", "y"),
            ),
        )

        val toolSchema = functionSchema.asToolSchema()

        toolSchema.type shouldBe "object"
        toolSchema.properties.shouldNotBeNull {
            keys shouldContainExactly setOf("operation", "x", "y")
        }
        toolSchema.required shouldContainExactly listOf("operation", "x", "y")
    }

    @Test
    fun `should handle required and optional parameters`() {
        // Test with some required, some optional
        val mixedSchema = FunctionCallingSchema(
            name = "search",
            parameters = ObjectPropertyDefinition(
                properties = mapOf(
                    "query" to StringPropertyDefinition(description = "Search query"),
                    "limit" to StringPropertyDefinition(description = "Result limit"),
                ),
                required = listOf("query"),
            ),
        )

        val mixedToolSchema = mixedSchema.asToolSchema()
        mixedToolSchema.type shouldBe "object"
        mixedToolSchema.properties.shouldNotBeNull {
            keys shouldContainExactly setOf("query", "limit")
        }
        mixedToolSchema.required shouldContainExactly listOf("query")

        // Test with all optional (empty required list)
        val allOptionalSchema = FunctionCallingSchema(
            name = "list",
            parameters = ObjectPropertyDefinition(
                properties = mapOf("filter" to StringPropertyDefinition()),
                required = emptyList(),
            ),
        )

        val allOptionalToolSchema = allOptionalSchema.asToolSchema()
        allOptionalToolSchema.required.shouldNotBeNull {
            this shouldContainExactly emptyList()
        }
    }

    @Test
    fun `should handle complex nested parameter types`() {
        val functionSchema = FunctionCallingSchema(
            name = "process",
            parameters = ObjectPropertyDefinition(
                properties = mapOf(
                    "items" to ArrayPropertyDefinition(
                        items = StringPropertyDefinition(),
                        description = "List of items to process",
                    ),
                    "config" to ObjectPropertyDefinition(
                        properties = mapOf(
                            "timeout" to StringPropertyDefinition(description = "Timeout in seconds"),
                            "retries" to StringPropertyDefinition(description = "Number of retries"),
                        ),
                        required = listOf("timeout"),
                    ),
                ),
                required = listOf("items", "config"),
            ),
        )

        val toolSchema = functionSchema.asToolSchema()

        toolSchema.type shouldBe "object"
        toolSchema.properties.shouldNotBeNull {
            keys shouldContainExactly setOf("items", "config")
        }
        toolSchema.required shouldContainExactly listOf("items", "config")
    }

    @Test
    fun `should handle parameters count variations`() {
        // Empty parameters
        val emptySchema = FunctionCallingSchema(
            name = "ping",
            parameters = ObjectPropertyDefinition(
                properties = emptyMap(),
                required = emptyList(),
            ),
        )

        val emptyToolSchema = emptySchema.asToolSchema()
        emptyToolSchema.type shouldBe "object"
        emptyToolSchema.properties.shouldNotBeNull {
            keys shouldContainExactly emptySet()
        }

        // Single parameter
        val singleSchema = FunctionCallingSchema(
            name = "greet",
            parameters = ObjectPropertyDefinition(
                properties = mapOf(
                    "name" to StringPropertyDefinition(description = "Name to greet"),
                ),
                required = listOf("name"),
            ),
        )

        val singleToolSchema = singleSchema.asToolSchema()
        singleToolSchema.properties.shouldNotBeNull {
            keys shouldContainExactly setOf("name")
        }
        singleToolSchema.required shouldContainExactly listOf("name")
    }
}
