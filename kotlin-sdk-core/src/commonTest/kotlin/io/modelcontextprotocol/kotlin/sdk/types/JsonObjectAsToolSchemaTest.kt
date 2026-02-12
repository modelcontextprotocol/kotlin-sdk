package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test

class JsonObjectAsToolSchemaTest {

    @Test
    fun `should convert valid object schema`() {
        val jsonSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "User name")
                }
                putJsonObject("age") {
                    put("type", "integer")
                }
            }
            putJsonArray("required") {
                add("name")
            }
        }

        val toolSchema = jsonSchema.asToolSchema()

        toolSchema.type shouldBe "object"
        toolSchema.properties.shouldNotBeNull {
            keys shouldContainExactly setOf("name", "age")
        }
        toolSchema.required shouldContainExactly listOf("name")
    }

    @Test
    fun `should handle missing required field`() {
        val jsonSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("optional") {
                    put("type", "string")
                }
            }
        }

        val toolSchema = jsonSchema.asToolSchema()

        toolSchema.type shouldBe "object"
        toolSchema.properties.shouldNotBeNull()
        toolSchema.required.shouldBeNull()
    }

    @Test
    fun `should handle empty required array`() {
        val jsonSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("field1") { put("type", "string") }
            }
            putJsonArray("required") { } // Empty array - all fields optional
        }

        val toolSchema = jsonSchema.asToolSchema()

        toolSchema.type shouldBe "object"
        toolSchema.required.shouldNotBeNull {
            shouldBeEmpty()
        }
    }

    @Test
    fun `should accept schema without type field`() {
        val jsonSchema = buildJsonObject {
            putJsonObject("properties") {
                putJsonObject("name") { put("type", "string") }
            }
            putJsonArray("required") { add("name") }
        }

        val toolSchema = jsonSchema.asToolSchema()

        // ToolSchema always has type="object" by default
        toolSchema.type shouldBe "object"
        toolSchema.properties.shouldNotBeNull()
        toolSchema.required shouldContainExactly listOf("name")
    }

    @Test
    fun `should reject non-object schema types`() {
        listOf("array", "string", "number", "boolean", "null").forEach { type ->
            val invalidSchema = buildJsonObject { put("type", type) }

            shouldThrow<IllegalArgumentException> {
                invalidSchema.asToolSchema()
            }.message shouldBe "Only object schemas are supported for ToolSchema conversion, got: $type"
        }
    }

    @Test
    fun `should preserve nested schema structures`() {
        val jsonSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("config") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("enabled") {
                            put("type", "boolean")
                        }
                        putJsonObject("timeout") {
                            put("type", "integer")
                        }
                    }
                    putJsonArray("required") {
                        add("enabled")
                    }
                }
            }
            putJsonArray("required") {
                add("config")
            }
        }

        val toolSchema = jsonSchema.asToolSchema()

        toolSchema.type shouldBe "object"
        toolSchema.properties.shouldNotBeNull()
        toolSchema.required shouldContainExactly listOf("config")

        val configProperty = toolSchema.properties["config"]?.jsonObject
        configProperty.shouldNotBeNull {
            get("type")?.jsonPrimitive?.content shouldBe "object"
            get("properties")?.jsonObject.shouldNotBeNull {
                keys shouldContainExactly setOf("enabled", "timeout")
            }
        }
    }

}
