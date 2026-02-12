package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.schema.json.FunctionCallingSchema
import kotlinx.schema.json.JsonSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Converts a JSON Schema [JsonObject] into a [ToolSchema] representation.
 *
 * Extracts `properties` and `required` fields from the JSON Schema object. The JSON object
 * should conform to JSON Schema Draft 2020-12 specification for object types.
 *
 * Sample:
 * ```kotlin
 * val jsonSchema = buildJsonObject {
 *     put("type", "object")
 *     putJsonObject("properties") {
 *         putJsonObject("name") {
 *             put("type", "string")
 *         }
 *     }
 *     putJsonArray("required") { add("name") }
 * }
 * val toolSchema = jsonSchema.asToolSchema()
 * ```
 *
 * @return A [ToolSchema] instance with extracted schema metadata.
 * @throws IllegalArgumentException if the schema type is specified but is not "object". Missing type field is accepted.
 */
public fun JsonObject.asToolSchema(): ToolSchema {
    // Validate a schema type if present
    val schemaType = this["type"]?.jsonPrimitive?.content
    require(schemaType == null || schemaType == "object") {
        "Only object schemas are supported for ToolSchema conversion, got: $schemaType"
    }

    return ToolSchema(
        properties = this["properties"]?.jsonObject,
        required = this["required"]?.jsonArray?.map { it.jsonPrimitive.content },
    )
}

/**
 * Converts a [JsonSchema] from kotlinx-schema into a [ToolSchema] representation.
 *
 * This is useful when generating schemas from Kotlin data classes using kotlinx-schema's
 * schema generator. The schema is first serialized to JSON and then converted to [ToolSchema].
 *
 * @return A [ToolSchema] instance with the schema's properties and required fields.
 * @throws IllegalArgumentException if the schema type is not "object".
 * @sample
 * ```kotlin
 * import kotlinx.schema.generator.core.SchemaGeneratorService
 * import kotlinx.schema.Description
 *
 * @Serializable
 * data class SearchParams(
 *     @property:Description("Search query")
 *     val query: String,
 *     val limit: Int = 10
 * )
 *
 * val generator = SchemaGeneratorService.getGenerator(KClass::class, JsonSchema::class)
 * val schema = generator.generateSchema(SearchParams::class)
 * val toolSchema = schema.asToolSchema()
 * ```
 */
public fun JsonSchema.asToolSchema(): ToolSchema = McpJson.encodeToJsonElement(this)
    .jsonObject.asToolSchema()

/**
 * Converts a [FunctionCallingSchema]'s parameters into a [ToolSchema] representation.
 *
 * Extracts and converts the `parameters` field from the function calling schema,
 * which is typically used in OpenAI-compatible function calling APIs.
 *
 * @return A [ToolSchema] object containing the function's parameter schema.
 * @throws IllegalArgumentException if the parameters schema type is not "object".
 * @sample
 * ```kotlin
 * val functionSchema = FunctionCallingSchema(
 *     name = "search",
 *     description = "Search the web",
 *     parameters = ObjectPropertyDefinition(
 *         properties = mapOf(
 *             "query" to StringPropertyDefinition(description = "Search query")
 *         ),
 *         required = listOf("query")
 *     )
 * )
 * val toolSchema = functionSchema.asToolSchema()
 * ```
 */
public fun FunctionCallingSchema.asToolSchema(): ToolSchema =
    McpJson.encodeToJsonElement(this.parameters).jsonObject.asToolSchema()
