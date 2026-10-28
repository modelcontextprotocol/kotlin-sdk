package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlinx.serialization.SerializationException
import kotlin.test.Test

class PrimitiveSchemaTest {

    @Suppress("DEPRECATION")
    @Test
    fun `should round-trip every schema type through PrimitiveSchemaDefinition`() {
        val cases = listOf<Pair<PrimitiveSchemaDefinition, String>>(
            StringSchema(
                title = "Email address",
                description = "User email",
                minLength = 5,
                maxLength = 100,
                format = StringSchemaFormat.Email,
                default = "user@example.com",
            ) to """
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
            IntegerSchema(title = "Age", description = "User age", minimum = 0, maximum = 150, default = 25) to """
                {
                  "title": "Age",
                  "description": "User age",
                  "minimum": 0,
                  "maximum": 150,
                  "default": 25,
                  "type": "integer"
                }
            """.trimIndent(),
            DoubleSchema(
                title = "Score",
                description = "Test score",
                minimum = 0.0,
                maximum = 100.0,
                default = 50.0,
            ) to """
                {
                  "title": "Score",
                  "description": "Test score",
                  "minimum": 0.0,
                  "maximum": 100.0,
                  "default": 50.0,
                  "type": "number"
                }
            """.trimIndent(),
            BooleanSchema(title = "Agree", description = "Terms acceptance", default = false) to """
                {"title": "Agree", "description": "Terms acceptance", "default": false, "type": "boolean"}
            """.trimIndent(),
            UntitledSingleSelectEnumSchema(title = "Color", enumValues = listOf("Red", "Green"), default = "Red") to """
                {"title": "Color", "enum": ["Red", "Green"], "default": "Red", "type": "string"}
            """.trimIndent(),
            TitledSingleSelectEnumSchema(
                title = "Color",
                oneOf = listOf(
                    EnumOption(const = "#FF0000", title = "Red"),
                    EnumOption(const = "#00FF00", title = "Green"),
                ),
                default = "#FF0000",
            ) to """
                {
                  "title": "Color",
                  "oneOf": [{"const": "#FF0000", "title": "Red"}, {"const": "#00FF00", "title": "Green"}],
                  "default": "#FF0000",
                  "type": "string"
                }
            """.trimIndent(),
            UntitledMultiSelectEnumSchema(
                title = "Colors",
                minItems = 1,
                maxItems = 3,
                items = UntitledMultiSelectEnumSchema.Items(enumValues = listOf("Red", "Green", "Blue")),
                default = listOf("Red"),
            ) to """
                {
                  "title": "Colors",
                  "minItems": 1,
                  "maxItems": 3,
                  "items": {"enum": ["Red", "Green", "Blue"], "type": "string"},
                  "default": ["Red"],
                  "type": "array"
                }
            """.trimIndent(),
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
            ) to """
                {
                  "title": "Colors",
                  "minItems": 1,
                  "maxItems": 2,
                  "items": {"anyOf": [{"const": "#FF0000", "title": "Red"}, {"const": "#00FF00", "title": "Green"}]},
                  "default": ["#FF0000"],
                  "type": "array"
                }
            """.trimIndent(),
            LegacyTitledEnumSchema(
                title = "Status",
                enumValues = listOf("opt1", "opt2"),
                enumNames = listOf("Option One", "Option Two"),
                default = "opt1",
            ) to """
                {
                  "title": "Status",
                  "enum": ["opt1", "opt2"],
                  "enumNames": ["Option One", "Option Two"],
                  "default": "opt1",
                  "type": "string"
                }
            """.trimIndent(),
        )

        cases.forEach { (schema, json) ->
            withClue(schema::class.simpleName) {
                verifySerialization(schema, McpJson, json)
            }
        }
    }

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

    @Test
    fun `should reject schema with missing non-string or unknown type`() {
        listOf("""{"title": "x"}""", """{"type": 1}""", """{"type": "unknown"}""").forEach { json ->
            withClue(json) {
                shouldThrow<SerializationException> {
                    McpJson.decodeFromString<PrimitiveSchemaDefinition>(json)
                }
            }
        }
    }

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
