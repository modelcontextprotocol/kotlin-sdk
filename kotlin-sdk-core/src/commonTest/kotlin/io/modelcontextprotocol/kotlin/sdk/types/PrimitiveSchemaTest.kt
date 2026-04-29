package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.SerializationException
import kotlin.test.Test

class PrimitiveSchemaTest {

    // ── StringSchema ────────────────────────────────────────────────────

    @Test
    fun `should serialize StringSchema with minimal fields`() {
        verifySerialization(
            StringSchema() as PrimitiveSchemaDefinition,
            McpJson,
            """{"type": "string"}""",
        )
    }

    @Test
    fun `should serialize StringSchema with all fields`() {
        val schema = StringSchema(
            title = "Email address",
            description = "User email",
            minLength = 5,
            maxLength = 100,
            format = StringSchemaFormat.Email,
            default = "user@example.com",
        )

        verifySerialization(
            schema as PrimitiveSchemaDefinition,
            McpJson,
            """
            {
              "title": "Email address",
              "description": "User email",
              "minLength": 5,
              "maxLength": 100,
              "format": "email",
              "default": "user@example.com",
              "type": "string"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize StringSchema from JSON`() {
        val json = """
            {
              "type": "string",
              "title": "Name",
              "format": "uri"
            }
        """.trimIndent()

        val schema = verifyDeserialization<PrimitiveSchemaDefinition>(McpJson, json)
        schema.shouldBeInstanceOf<StringSchema>()
        schema.title shouldBe "Name"
        schema.format shouldBe StringSchemaFormat.Uri
    }

    // ── StringSchemaFormat ──────────────────────────────────────────────

    @Test
    fun `should serialize all StringSchemaFormat values`() {
        val cases = mapOf(
            StringSchemaFormat.Email to "email",
            StringSchemaFormat.Uri to "uri",
            StringSchemaFormat.Date to "date",
            StringSchemaFormat.DateTime to "date-time",
        )
        for ((format, expectedValue) in cases) {
            val schema = StringSchema(format = format)
            val json = McpJson.encodeToString(schema)
            json shouldContain "\"format\":\"$expectedValue\""
        }
    }

    // ── IntegerSchema ───────────────────────────────────────────────────

    @Test
    fun `should serialize IntegerSchema with minimal fields`() {
        verifySerialization(
            IntegerSchema() as PrimitiveSchemaDefinition,
            McpJson,
            """{"type": "integer"}""",
        )
    }

    @Test
    fun `should serialize IntegerSchema with all fields`() {
        verifySerialization(
            IntegerSchema(
                title = "Age",
                description = "User age",
                minimum = 0,
                maximum = 150,
                default = 25,
            ) as PrimitiveSchemaDefinition,
            McpJson,
            """
            {
              "title": "Age",
              "description": "User age",
              "minimum": 0,
              "maximum": 150,
              "default": 25,
              "type": "integer"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize IntegerSchema from JSON`() {
        val json = """{"type": "integer", "minimum": 1, "maximum": 10}"""
        val schema = verifyDeserialization<PrimitiveSchemaDefinition>(McpJson, json)
        schema.shouldBeInstanceOf<IntegerSchema>()
        schema.minimum shouldBe 1
        schema.maximum shouldBe 10
    }

    // ── DoubleSchema ────────────────────────────────────────────────────

    @Test
    fun `should serialize DoubleSchema with minimal fields`() {
        verifySerialization(
            DoubleSchema() as PrimitiveSchemaDefinition,
            McpJson,
            """{"type": "number"}""",
        )
    }

    @Test
    fun `should serialize DoubleSchema with all fields`() {
        verifySerialization(
            DoubleSchema(
                title = "Score",
                description = "Test score",
                minimum = 0.0,
                maximum = 100.0,
                default = 50.0,
            ) as PrimitiveSchemaDefinition,
            McpJson,
            """
            {
              "title": "Score",
              "description": "Test score",
              "minimum": 0.0,
              "maximum": 100.0,
              "default": 50.0,
              "type": "number"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize DoubleSchema from JSON`() {
        val json = """{"type": "number", "minimum": 0.5}"""
        val schema = verifyDeserialization<PrimitiveSchemaDefinition>(McpJson, json)
        schema.shouldBeInstanceOf<DoubleSchema>()
        schema.minimum shouldBe 0.5
    }

    // ── BooleanSchema ───────────────────────────────────────────────────

    @Test
    fun `should serialize BooleanSchema with minimal fields`() {
        verifySerialization(
            BooleanSchema() as PrimitiveSchemaDefinition,
            McpJson,
            """{"type": "boolean"}""",
        )
    }

    @Test
    fun `should serialize BooleanSchema with all fields`() {
        verifySerialization(
            BooleanSchema(
                title = "Agree",
                description = "Terms acceptance",
                default = false,
            ) as PrimitiveSchemaDefinition,
            McpJson,
            """
            {
              "title": "Agree",
              "description": "Terms acceptance",
              "default": false,
              "type": "boolean"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize BooleanSchema from JSON`() {
        val json = """{"type": "boolean", "default": true}"""
        val schema = verifyDeserialization<PrimitiveSchemaDefinition>(McpJson, json)
        schema.shouldBeInstanceOf<BooleanSchema>()
        schema.default shouldBe true
    }

    // ── UntitledSingleSelectEnumSchema ──────────────────────────────────

    @Test
    fun `should serialize UntitledSingleSelectEnumSchema`() {
        verifySerialization(
            UntitledSingleSelectEnumSchema(
                title = "Color",
                enumValues = listOf("Red", "Green", "Blue"),
                default = "Red",
            ) as PrimitiveSchemaDefinition,
            McpJson,
            """
            {
              "title": "Color",
              "enum": ["Red", "Green", "Blue"],
              "default": "Red",
              "type": "string"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize UntitledSingleSelectEnumSchema from JSON`() {
        val json = """
            {
              "type": "string",
              "enum": ["A", "B", "C"]
            }
        """.trimIndent()

        val schema = verifyDeserialization<PrimitiveSchemaDefinition>(McpJson, json)
        schema.shouldBeInstanceOf<UntitledSingleSelectEnumSchema>()
        schema.enumValues shouldBe listOf("A", "B", "C")
    }

    // ── TitledSingleSelectEnumSchema ────────────────────────────────────

    @Test
    fun `should serialize TitledSingleSelectEnumSchema`() {
        verifySerialization(
            TitledSingleSelectEnumSchema(
                title = "Color",
                oneOf = listOf(
                    EnumOption(const = "#FF0000", title = "Red"),
                    EnumOption(const = "#00FF00", title = "Green"),
                ),
                default = "#FF0000",
            ) as PrimitiveSchemaDefinition,
            McpJson,
            """
            {
              "title": "Color",
              "oneOf": [
                {"const": "#FF0000", "title": "Red"},
                {"const": "#00FF00", "title": "Green"}
              ],
              "default": "#FF0000",
              "type": "string"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize TitledSingleSelectEnumSchema from JSON`() {
        val json = """
            {
              "type": "string",
              "oneOf": [
                {"const": "a", "title": "Alpha"},
                {"const": "b", "title": "Beta"}
              ]
            }
        """.trimIndent()

        val schema = verifyDeserialization<PrimitiveSchemaDefinition>(McpJson, json)
        schema.shouldBeInstanceOf<TitledSingleSelectEnumSchema>()
        schema.oneOf shouldHaveSize 2
        schema.oneOf[0].const shouldBe "a"
        schema.oneOf[0].title shouldBe "Alpha"
    }

    // ── UntitledMultiSelectEnumSchema ────────────────────────────────────

    @Test
    fun `should serialize UntitledMultiSelectEnumSchema`() {
        verifySerialization(
            UntitledMultiSelectEnumSchema(
                title = "Colors",
                minItems = 1,
                maxItems = 3,
                items = UntitledMultiSelectEnumSchema.Items(enumValues = listOf("Red", "Green", "Blue")),
                default = listOf("Red"),
            ) as PrimitiveSchemaDefinition,
            McpJson,
            """
            {
              "title": "Colors",
              "minItems": 1,
              "maxItems": 3,
              "items": {
                "enum": ["Red", "Green", "Blue"],
                "type": "string"
              },
              "default": ["Red"],
              "type": "array"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize UntitledMultiSelectEnumSchema from JSON`() {
        val json = """
            {
              "type": "array",
              "items": {
                "type": "string",
                "enum": ["X", "Y"]
              }
            }
        """.trimIndent()

        val schema = verifyDeserialization<PrimitiveSchemaDefinition>(McpJson, json)
        schema.shouldBeInstanceOf<UntitledMultiSelectEnumSchema>()
        schema.items.enumValues shouldBe listOf("X", "Y")
    }

    // ── TitledMultiSelectEnumSchema ──────────────────────────────────────

    @Test
    fun `should serialize TitledMultiSelectEnumSchema`() {
        verifySerialization(
            TitledMultiSelectEnumSchema(
                title = "Colors",
                minItems = 1,
                maxItems = 2,
                items = TitledMultiSelectEnumSchema.Items(
                    anyOf = listOf(
                        EnumOption(const = "#FF0000", title = "Red"),
                        EnumOption(const = "#00FF00", title = "Green"),
                    ),
                ),
                default = listOf("#FF0000"),
            ) as PrimitiveSchemaDefinition,
            McpJson,
            """
            {
              "title": "Colors",
              "minItems": 1,
              "maxItems": 2,
              "items": {
                "anyOf": [
                  {"const": "#FF0000", "title": "Red"},
                  {"const": "#00FF00", "title": "Green"}
                ]
              },
              "default": ["#FF0000"],
              "type": "array"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize TitledMultiSelectEnumSchema from JSON`() {
        val json = """
            {
              "type": "array",
              "items": {
                "anyOf": [
                  {"const": "x", "title": "X"},
                  {"const": "y", "title": "Y"}
                ]
              }
            }
        """.trimIndent()

        val schema = verifyDeserialization<PrimitiveSchemaDefinition>(McpJson, json)
        schema.shouldBeInstanceOf<TitledMultiSelectEnumSchema>()
        schema.items.anyOf shouldHaveSize 2
        schema.items.anyOf[0].const shouldBe "x"
    }

    // ── LegacyTitledEnumSchema ────────────────────────────────────────────

    @Test
    fun `should serialize LegacyTitledEnumSchema`() {
        verifySerialization(
            LegacyTitledEnumSchema(
                title = "Status",
                enumValues = listOf("opt1", "opt2", "opt3"),
                enumNames = listOf("Option One", "Option Two", "Option Three"),
                default = "opt1",
            ) as PrimitiveSchemaDefinition,
            McpJson,
            """
            {
              "title": "Status",
              "enum": ["opt1", "opt2", "opt3"],
              "enumNames": ["Option One", "Option Two", "Option Three"],
              "default": "opt1",
              "type": "string"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize LegacyTitledEnumSchema from JSON`() {
        val json = """
            {
              "type": "string",
              "enum": ["a", "b"],
              "enumNames": ["Alpha", "Beta"]
            }
        """.trimIndent()

        val schema = verifyDeserialization<PrimitiveSchemaDefinition>(McpJson, json)
        schema.shouldBeInstanceOf<LegacyTitledEnumSchema>()
        schema.enumValues shouldBe listOf("a", "b")
        schema.enumNames shouldBe listOf("Alpha", "Beta")
    }

    // ── Error cases ─────────────────────────────────────────────────────

    @Test
    fun `should throw on unknown type`() {
        val json = """{"type": "unknown"}"""
        shouldThrow<SerializationException> {
            McpJson.decodeFromString<PrimitiveSchemaDefinition>(json)
        }
    }

    // ── RequestedSchema with typed properties ───────────────────────────

    @Test
    fun `should round-trip RequestedSchema with mixed property types`() {
        val schema = ElicitRequestParams.RequestedSchema(
            properties = mapOf(
                "name" to StringSchema(title = "Name"),
                "age" to IntegerSchema(minimum = 0),
                "confirmed" to BooleanSchema(default = false),
                "color" to UntitledSingleSelectEnumSchema(enumValues = listOf("Red", "Blue")),
            ),
            required = listOf("name"),
        )

        verifySerialization(
            schema,
            McpJson,
            """
            {
              "properties": {
                "name": {"title": "Name", "type": "string"},
                "age": {"minimum": 0, "type": "integer"},
                "confirmed": {"default": false, "type": "boolean"},
                "color": {"enum": ["Red", "Blue"], "type": "string"}
              },
              "required": ["name"],
              "type": "object"
            }
            """.trimIndent(),
        )
    }
}
