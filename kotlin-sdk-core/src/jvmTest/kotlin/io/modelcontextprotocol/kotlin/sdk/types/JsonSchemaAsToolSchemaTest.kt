package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.schema.Description
import kotlinx.schema.generator.core.SchemaGeneratorService
import kotlinx.schema.json.JsonSchema
import kotlin.reflect.KClass
import kotlin.test.Test

class JsonSchemaAsToolSchemaTest {

    private val schemaGenerator = requireNotNull(
        SchemaGeneratorService.getGenerator(KClass::class, JsonSchema::class),
    )

    @Test
    fun `should convert data class with annotations`() {
        data class TestParams(
            @property:Description("Test parameter")
            val value: String,
        )

        val jsonSchema = schemaGenerator.generateSchema(TestParams::class)
        val toolSchema = jsonSchema.asToolSchema()

        toolSchema.type shouldBe "object"
        toolSchema.properties.shouldNotBeNull()
        toolSchema.required shouldContainExactly listOf("value")

        val tool = Tool(
            name = "test",
            inputSchema = toolSchema,
        )
        val json = McpJson.encodeToString(tool)

        json shouldEqualJson """
            {
              "name": "test",
              "inputSchema": {
                "type": "object",
                "properties": {
                  "value": {
                    "type": "string",
                    "description": "Test parameter"
                  }
                },
                "required": ["value"]
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should handle complex types and optional fields`() {
        data class ComplexParams(
            val stringField: String,
            val intField: Int,
            val boolField: Boolean,
            val optionalField: String? = null,
            val listField: List<String>,
        )

        val jsonSchema = schemaGenerator.generateSchema(ComplexParams::class)
        val toolSchema = jsonSchema.asToolSchema()

        toolSchema.type shouldBe "object"
        toolSchema.properties.shouldNotBeNull {
            keys shouldContainExactly setOf(
                "stringField",
                "intField",
                "boolField",
                "optionalField",
                "listField",
            )
        }

        toolSchema.required.shouldNotBeNull {
            this shouldContainExactly listOf(
                "stringField",
                "intField",
                "boolField",
                "listField",
            )
        }
    }
}
