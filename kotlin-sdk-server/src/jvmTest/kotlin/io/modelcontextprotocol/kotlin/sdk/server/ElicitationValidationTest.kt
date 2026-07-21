package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.types.BooleanSchema
import io.modelcontextprotocol.kotlin.sdk.types.DoubleSchema
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.EnumOption
import io.modelcontextprotocol.kotlin.sdk.types.IntegerSchema
import io.modelcontextprotocol.kotlin.sdk.types.LegacyTitledEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.PrimitiveSchemaDefinition
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.StringSchema
import io.modelcontextprotocol.kotlin.sdk.types.StringSchemaFormat
import io.modelcontextprotocol.kotlin.sdk.types.TitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.TitledSingleSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledSingleSelectEnumSchema
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for [validateElicitationContent]: pure-function checks of accepted form-mode
 * content against the requested schema.
 *
 * End-to-end tests for the `createElicitation` round trip live in the `integration-test`
 * module under `io.modelcontextprotocol.kotlin.sdk.client`.
 */
class ElicitationValidationTest {

    private fun schema(vararg properties: Pair<String, PrimitiveSchemaDefinition>, required: List<String>? = null) =
        ElicitRequestParams.RequestedSchema(properties = mapOf(*properties), required = required)

    private fun assertRejected(
        schema: ElicitRequestParams.RequestedSchema,
        content: JsonObject,
        expectedError: String,
    ) {
        val exception = assertFailsWith<McpException> { validateElicitationContent(schema, content) }
        assertEquals(RPCError.ErrorCode.INVALID_PARAMS, exception.code)
        val message = exception.message.orEmpty()
        assertTrue(
            message.startsWith("Elicitation response content does not match requested schema: "),
            "Unexpected message prefix: $message",
        )
        assertTrue(expectedError in message, "Expected '$expectedError' in: $message")
    }

    @Test
    fun `validate content matching schema passes`() {
        assertDoesNotThrow {
            validateElicitationContent(
                schema(
                    "name" to StringSchema(minLength = 1, maxLength = 10),
                    "age" to IntegerSchema(minimum = 0, maximum = 120),
                    "score" to DoubleSchema(minimum = 0.0, maximum = 1.0),
                    "subscribed" to BooleanSchema(),
                    "color" to UntitledSingleSelectEnumSchema(enumValues = listOf("red", "green")),
                    "size" to TitledSingleSelectEnumSchema(
                        oneOf = listOf(EnumOption("s", "Small"), EnumOption("l", "Large")),
                    ),
                    "tags" to UntitledMultiSelectEnumSchema(
                        items = UntitledMultiSelectEnumSchema.Items(enumValues = listOf("a", "b", "c")),
                        minItems = 1,
                        maxItems = 2,
                    ),
                    required = listOf("name", "age"),
                ),
                buildJsonObject {
                    put("name", "octocat")
                    put("age", 42)
                    put("score", 0.5)
                    put("subscribed", true)
                    put("color", "red")
                    put("size", "l")
                    putJsonArray("tags") { add("a") }
                },
            )
        }
    }

    @Test
    fun `validate missing required property fails`() {
        assertRejected(
            schema("name" to StringSchema(), required = listOf("name")),
            JsonObject(emptyMap()),
            "must have required property 'name'",
        )
    }

    @Test
    fun `validate empty content with no required properties passes`() {
        assertDoesNotThrow {
            validateElicitationContent(schema("name" to StringSchema()), JsonObject(emptyMap()))
        }
    }

    @Test
    fun `validate wrong type for string property fails`() {
        assertRejected(
            schema("name" to StringSchema()),
            buildJsonObject { put("name", 42) },
            "'name' must be string",
        )
    }

    @Test
    fun `validate string length constraints fail`() {
        assertRejected(
            schema("name" to StringSchema(minLength = 3)),
            buildJsonObject { put("name", "ab") },
            "'name' must NOT have fewer than 3 characters",
        )
        assertRejected(
            schema("name" to StringSchema(maxLength = 3)),
            buildJsonObject { put("name", "abcd") },
            "'name' must NOT have more than 3 characters",
        )
    }

    @Test
    fun `validate json string is not accepted for integer boolean or number`() {
        // JsonPrimitive("42") is the JSON string "42", not the number 42; the validator must not
        // parse string content as a number.
        assertRejected(
            schema("age" to IntegerSchema()),
            buildJsonObject { put("age", "42") },
            "'age' must be integer",
        )
        assertRejected(
            schema("score" to DoubleSchema()),
            buildJsonObject { put("score", "1.5") },
            "'score' must be number",
        )
        assertRejected(
            schema("subscribed" to BooleanSchema()),
            buildJsonObject { put("subscribed", "true") },
            "'subscribed' must be boolean",
        )
    }

    @Test
    fun `validate integer outside minimum and maximum fails`() {
        assertRejected(
            schema("age" to IntegerSchema(minimum = 0)),
            buildJsonObject { put("age", -1) },
            "'age' must be >= 0",
        )
        assertRejected(
            schema("age" to IntegerSchema(maximum = 120)),
            buildJsonObject { put("age", 200) },
            "'age' must be <= 120",
        )
    }

    @Test
    fun `validate non-integral value for integer property fails`() {
        assertRejected(
            schema("age" to IntegerSchema()),
            buildJsonObject { put("age", 3.5) },
            "'age' must be integer",
        )
    }

    @Test
    fun `validate integral number forms are accepted for integer property`() {
        // JSON Schema's "integer" matches numbers with a zero fractional part.
        assertDoesNotThrow {
            validateElicitationContent(
                schema("age" to IntegerSchema(minimum = 0, maximum = 200)),
                buildJsonObject { put("age", 5.0) },
            )
        }
    }

    @Test
    fun `validate exponent form is accepted for integer property`() {
        val content = McpJson.parseToJsonElement("""{"age": 1e2}""") as JsonObject
        assertDoesNotThrow {
            validateElicitationContent(
                schema("age" to IntegerSchema(minimum = 0, maximum = 200)),
                content,
            )
        }
    }

    @Test
    fun `validate number outside range fails`() {
        assertRejected(
            schema("score" to DoubleSchema(maximum = 1.0)),
            buildJsonObject { put("score", 1.5) },
            "'score' must be <= 1.0",
        )
    }

    @Test
    fun `validate integral value is accepted for number property`() {
        assertDoesNotThrow {
            validateElicitationContent(
                schema("score" to DoubleSchema(minimum = 0.0)),
                buildJsonObject { put("score", 5) },
            )
        }
    }

    @Test
    fun `validate string length counts unicode code points`() {
        // JSON Schema string length counts RFC 8259 characters: one emoji is one character
        // even when it is two UTF-16 units.
        assertDoesNotThrow {
            validateElicitationContent(
                schema("emoji" to StringSchema(maxLength = 1)),
                buildJsonObject { put("emoji", "😀") },
            )
        }
        assertRejected(
            schema("emoji" to StringSchema(minLength = 2)),
            buildJsonObject { put("emoji", "😀") },
            "'emoji' must NOT have fewer than 2 characters",
        )
    }

    @Test
    fun `validate string format is treated as an annotation`() {
        assertDoesNotThrow {
            validateElicitationContent(
                schema("email" to StringSchema(format = StringSchemaFormat.Email)),
                buildJsonObject { put("email", "not-an-email") },
            )
        }
    }

    @Test
    fun `validate negative zero satisfies an inclusive zero bound`() {
        // IEEE 754 comparison: -0.0 == 0.0, so an inclusive minimum of 0.0 admits -0.0.
        assertDoesNotThrow {
            validateElicitationContent(
                schema("score" to DoubleSchema(minimum = 0.0)),
                buildJsonObject { put("score", -0.0) },
            )
        }
    }

    @Test
    fun `validate enum value not in allowed set fails`() {
        assertRejected(
            schema("color" to UntitledSingleSelectEnumSchema(enumValues = listOf("red", "green"))),
            buildJsonObject { put("color", "blue") },
            "'color' must be equal to one of the allowed values",
        )
        assertRejected(
            schema("size" to TitledSingleSelectEnumSchema(oneOf = listOf(EnumOption("s", "Small")))),
            buildJsonObject { put("size", "xl") },
            "'size' must be equal to one of the allowed values",
        )
        assertRejected(
            schema("legacy" to LegacyTitledEnumSchema(enumValues = listOf("a", "b"))),
            buildJsonObject { put("legacy", "c") },
            "'legacy' must be equal to one of the allowed values",
        )
    }

    @Test
    fun `validate multi-select constraints fail`() {
        val tags = UntitledMultiSelectEnumSchema(
            items = UntitledMultiSelectEnumSchema.Items(enumValues = listOf("a", "b", "c")),
            minItems = 1,
            maxItems = 2,
        )
        assertRejected(
            schema("tags" to tags),
            buildJsonObject { put("tags", "a") },
            "'tags' must be array",
        )
        assertRejected(
            schema("tags" to tags),
            buildJsonObject {
                putJsonArray("tags") {
                    add("a")
                    add(42)
                }
            },
            "'tags' items must be string",
        )
        assertRejected(
            schema("tags" to tags),
            buildJsonObject {
                putJsonArray("tags") {
                    add("a")
                    add("z")
                }
            },
            "'tags' items must be equal to one of the allowed values",
        )
        assertRejected(
            schema("tags" to tags),
            buildJsonObject { putJsonArray("tags") {} },
            "'tags' must NOT have fewer than 1 items",
        )
        assertRejected(
            schema("tags" to tags),
            buildJsonObject {
                putJsonArray("tags") {
                    add("a")
                    add("b")
                    add("c")
                }
            },
            "'tags' must NOT have more than 2 items",
        )
    }

    @Test
    fun `validate titled multi-select membership uses option consts`() {
        val sizes = TitledMultiSelectEnumSchema(
            items = TitledMultiSelectEnumSchema.Items(
                anyOf = listOf(EnumOption("s", "Small"), EnumOption("l", "Large")),
            ),
        )
        assertDoesNotThrow {
            validateElicitationContent(
                schema("sizes" to sizes),
                buildJsonObject { putJsonArray("sizes") { add("s") } },
            )
        }
        assertRejected(
            schema("sizes" to sizes),
            buildJsonObject { putJsonArray("sizes") { add("Small") } },
            "'sizes' items must be equal to one of the allowed values",
        )
    }

    @Test
    fun `validate json null is rejected with the type error for each schema kind`() {
        // JsonNull is a JsonPrimitive whose content is "null"; it must fail the type check, not
        // accidentally parse as a value.
        assertRejected(
            schema("name" to StringSchema()),
            buildJsonObject { put("name", JsonNull) },
            "'name' must be string",
        )
        assertRejected(
            schema("age" to IntegerSchema()),
            buildJsonObject { put("age", JsonNull) },
            "'age' must be integer",
        )
        assertRejected(
            schema("subscribed" to BooleanSchema()),
            buildJsonObject { put("subscribed", JsonNull) },
            "'subscribed' must be boolean",
        )
    }

    @Test
    fun `validate non-primitive value is rejected for scalar schemas`() {
        assertRejected(
            schema("name" to StringSchema()),
            buildJsonObject { putJsonArray("name") { add("x") } },
            "'name' must be string",
        )
        assertRejected(
            schema("age" to IntegerSchema()),
            buildJsonObject { putJsonObject("age") {} },
            "'age' must be integer",
        )
    }

    @Test
    fun `validate number below minimum fails`() {
        assertRejected(
            schema("score" to DoubleSchema(minimum = 0.0)),
            buildJsonObject { put("score", -0.5) },
            "'score' must be >= 0.0",
        )
    }

    @Test
    fun `validate non-finite value for number property fails`() {
        // McpJson is lenient, so an unquoted NaN literal can arrive off the wire; it decodes to
        // the same primitive as Double.NaN here.
        assertRejected(
            schema("score" to DoubleSchema(minimum = 0.0, maximum = 1.0)),
            buildJsonObject { put("score", Double.NaN) },
            "'score' must be number",
        )
    }

    @Test
    fun `validate required property not declared in properties is still required`() {
        // JSON Schema's `required` is independent of `properties`.
        assertRejected(
            schema("name" to StringSchema(), required = listOf("name", "consent")),
            buildJsonObject { put("name", "octocat") },
            "must have required property 'consent'",
        )
    }

    @Test
    fun `validate properties not declared in schema pass`() {
        // JSON Schema default: without an additionalProperties restriction, undeclared keys are permitted.
        assertDoesNotThrow {
            validateElicitationContent(
                schema("name" to StringSchema(), required = listOf("name")),
                buildJsonObject {
                    put("name", "octocat")
                    put("undeclared", 42)
                },
            )
        }
    }

    @Test
    fun `validate reports every violation in one error`() {
        val exception = assertFailsWith<McpException> {
            validateElicitationContent(
                schema(
                    "name" to StringSchema(),
                    "age" to IntegerSchema(),
                    required = listOf("name"),
                ),
                buildJsonObject { put("age", "old") },
            )
        }
        val message = exception.message.orEmpty()
        assertTrue("must have required property 'name'" in message, message)
        assertTrue("'age' must be integer" in message, message)
    }
}
