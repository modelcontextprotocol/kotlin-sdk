package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.types.BooleanSchema
import io.modelcontextprotocol.kotlin.sdk.types.DoubleSchema
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.IntegerSchema
import io.modelcontextprotocol.kotlin.sdk.types.LegacyTitledEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PrimitiveSchemaDefinition
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.StringSchema
import io.modelcontextprotocol.kotlin.sdk.types.TitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.TitledSingleSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledSingleSelectEnumSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.floor

/**
 * Validates accepted form-mode elicitation [content] against the supported constraints in
 * [requestedSchema]: types, `required`, numeric bounds, string lengths, enum membership, and item
 * counts. Undeclared properties are permitted.
 * [StringSchema.format] values are treated as annotations and are not asserted.
 *
 * @throws McpException with [RPCError.ErrorCode.INVALID_PARAMS] listing the detected violations
 * (at most one per property).
 */
internal fun validateElicitationContent(requestedSchema: ElicitRequestParams.RequestedSchema, content: JsonObject) {
    val errors = buildList {
        for (name in requestedSchema.required.orEmpty()) {
            if (name !in content) add("must have required property '$name'")
        }
        for ((name, schema) in requestedSchema.properties) {
            val value = content[name] ?: continue
            checkProperty(name, schema, value)?.let(::add)
        }
    }
    if (errors.isNotEmpty()) {
        throw McpException(
            code = RPCError.ErrorCode.INVALID_PARAMS,
            message = "Elicitation response content does not match requested schema: ${errors.joinToString(", ")}",
        )
    }
}

private fun checkProperty(name: String, schema: PrimitiveSchemaDefinition, value: JsonElement): String? =
    when (schema) {
        is StringSchema -> {
            val string = stringValue(value) ?: return "'$name' must be string"
            val minLength = schema.minLength
            val maxLength = schema.maxLength
            val length = codePointCount(string)
            when {
                minLength != null && length < minLength ->
                    "'$name' must NOT have fewer than $minLength characters"

                maxLength != null && length > maxLength ->
                    "'$name' must NOT have more than $maxLength characters"

                else -> null
            }
        }

        is IntegerSchema -> {
            val integer = integerValue(value) ?: return "'$name' must be integer"
            checkRange(name, integer, schema.minimum?.toLong(), schema.maximum?.toLong())
        }

        is DoubleSchema -> {
            val number = doubleValue(value) ?: return "'$name' must be number"
            checkRange(name, number, schema.minimum, schema.maximum)
        }

        is BooleanSchema -> if (booleanValue(value) == null) "'$name' must be boolean" else null

        is UntitledSingleSelectEnumSchema -> checkEnum(name, value, schema.enumValues)

        is TitledSingleSelectEnumSchema -> checkEnum(name, value, schema.oneOf.map { it.const })

        is LegacyTitledEnumSchema -> checkEnum(name, value, schema.enumValues)

        is UntitledMultiSelectEnumSchema ->
            checkMultiSelect(name, value, schema.items.enumValues, schema.minItems, schema.maxItems)

        is TitledMultiSelectEnumSchema ->
            checkMultiSelect(name, value, schema.items.anyOf.map { it.const }, schema.minItems, schema.maxItems)
    }

// Keep Double statically typed so comparisons use IEEE 754 semantics; generic Comparable ordering
// treats -0.0 as less than 0.0.
private fun checkRange(
    name: String,
    value: Long,
    minimum: Long?,
    maximum: Long?,
): String? = when {
    minimum != null && value < minimum -> "'$name' must be >= $minimum"
    maximum != null && value > maximum -> "'$name' must be <= $maximum"
    else -> null
}

private fun checkRange(
    name: String,
    value: Double,
    minimum: Double?,
    maximum: Double?,
): String? = when {
    minimum != null && value < minimum -> "'$name' must be >= $minimum"
    maximum != null && value > maximum -> "'$name' must be <= $maximum"
    else -> null
}

private fun checkEnum(name: String, value: JsonElement, allowed: List<String>): String? {
    val string = stringValue(value) ?: return "'$name' must be string"
    return if (string in allowed) null else "'$name' must be equal to one of the allowed values"
}

private fun checkMultiSelect(
    name: String,
    value: JsonElement,
    allowed: List<String>,
    minItems: Int?,
    maxItems: Int?,
): String? {
    val array = value as? JsonArray ?: return "'$name' must be array"
    if (minItems != null && array.size < minItems) return "'$name' must NOT have fewer than $minItems items"
    if (maxItems != null && array.size > maxItems) return "'$name' must NOT have more than $maxItems items"
    for (element in array) {
        val item = stringValue(element) ?: return "'$name' items must be string"
        if (item !in allowed) return "'$name' items must be equal to one of the allowed values"
    }
    return null
}

// JSON distinguishes the string "42" from the number 42; kotlinx's JsonPrimitive accessors parse
// string content too, so every helper checks isString to preserve that distinction.

private fun stringValue(value: JsonElement): String? = (value as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun integerValue(value: JsonElement): Long? {
    val primitive = (value as? JsonPrimitive)?.takeIf { !it.isString } ?: return null
    primitive.longOrNull?.let { return it }
    // JSON Schema's "integer" also matches numbers with a zero fractional part (e.g. 5.0, 1e2).
    // Values that do not parse as Long are evaluated using their finite Double representation.
    val number = primitive.doubleOrNull ?: return null
    return if (number.isFinite() && number == floor(number)) number.toLong() else null
}

private fun doubleValue(value: JsonElement): Double? =
    (value as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

private fun booleanValue(value: JsonElement): Boolean? =
    (value as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

// JSON Schema string lengths count Unicode code points (RFC 8259 characters), not UTF-16 units,
// so a surrogate pair is one character.
private fun codePointCount(string: String): Int {
    var count = 0
    var index = 0
    while (index < string.length) {
        if (string[index].isHighSurrogate() && index + 1 < string.length && string[index + 1].isLowSurrogate()) {
            index++
        }
        index++
        count++
    }
    return count
}
