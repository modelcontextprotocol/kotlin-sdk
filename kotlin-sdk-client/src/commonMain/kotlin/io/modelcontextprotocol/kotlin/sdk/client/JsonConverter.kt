package io.modelcontextprotocol.kotlin.sdk.client

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private val logger = KotlinLogging.logger {}

/**
 * Utility object for converting Kotlin values to JSON elements.
 */
internal object JsonConverter {
    /**
     * Converts a map of values to a map of JSON elements.
     *
     * @param map The map to convert.
     * @return A map where each value has been converted to a JsonElement.
     */
    fun convertToJsonMap(map: Map<String, Any?>): Map<String, JsonElement> = map.mapValues { (key, value) ->
        try {
            convertToJsonElement(value)
        } catch (e: Exception) {
            logger.warn { "Failed to convert value for key '$key': ${e.message}. Using string representation." }
            JsonPrimitive(value.toString())
        }
    }

    /**
     * Converts a Kotlin value to a JSON element.
     *
     * Supports primitive types, collections, arrays, and nested structures.
     *
     * @param value The value to convert.
     * @return The corresponding JsonElement.
     */
    @OptIn(ExperimentalUnsignedTypes::class, ExperimentalSerializationApi::class)
    fun convertToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull

        is JsonElement -> value

        is String -> JsonPrimitive(value)

        is Number -> JsonPrimitive(value)

        is Boolean -> JsonPrimitive(value)

        is Char -> JsonPrimitive(value.toString())

        is Enum<*> -> JsonPrimitive(value.name)

        is Map<*, *> -> buildJsonObject { value.forEach { (k, v) -> put(k.toString(), convertToJsonElement(v)) } }

        is Collection<*> -> buildJsonArray { value.forEach { add(convertToJsonElement(it)) } }

        is Array<*> -> buildJsonArray { value.forEach { add(convertToJsonElement(it)) } }

        // Primitive arrays
        is IntArray -> buildJsonArray { value.forEach { add(it) } }

        is LongArray -> buildJsonArray { value.forEach { add(it) } }

        is FloatArray -> buildJsonArray { value.forEach { add(it) } }

        is DoubleArray -> buildJsonArray { value.forEach { add(it) } }

        is BooleanArray -> buildJsonArray { value.forEach { add(it) } }

        is ShortArray -> buildJsonArray { value.forEach { add(it) } }

        is ByteArray -> buildJsonArray { value.forEach { add(it) } }

        is CharArray -> buildJsonArray { value.forEach { add(it.toString()) } }

        // Unsigned arrays
        is UIntArray -> buildJsonArray { value.forEach { add(JsonPrimitive(it)) } }

        is ULongArray -> buildJsonArray { value.forEach { add(JsonPrimitive(it)) } }

        is UShortArray -> buildJsonArray { value.forEach { add(JsonPrimitive(it)) } }

        is UByteArray -> buildJsonArray { value.forEach { add(JsonPrimitive(it)) } }

        else -> {
            logger.debug { "Converting unknown type ${value::class} to string: $value" }
            JsonPrimitive(value.toString())
        }
    }
}

