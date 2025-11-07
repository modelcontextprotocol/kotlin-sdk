package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

public val EmptyJsonObject: JsonObject = JsonObject(emptyMap())

@OptIn(ExperimentalSerializationApi::class)
public val McpJson: Json by lazy {
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
        explicitNulls = false
    }
}

public fun Map<String, Any?>.toJson(): Map<String, JsonElement> = this.mapValues { (_, value) ->
    runCatching { convertToJsonElement(value) }
        .getOrElse { JsonPrimitive(value.toString()) }
}

@OptIn(ExperimentalUnsignedTypes::class, ExperimentalSerializationApi::class)
private fun convertToJsonElement(value: Any?): JsonElement = when (value) {
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
        JsonPrimitive(value.toString())
    }
}
