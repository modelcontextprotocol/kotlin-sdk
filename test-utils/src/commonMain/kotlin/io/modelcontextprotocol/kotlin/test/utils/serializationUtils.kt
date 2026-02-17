package io.modelcontextprotocol.kotlin.test.utils

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import kotlin.test.fail

/**
 * Verifies the serialization and deserialization process for a given object, ensuring:
 * - The object can be serialized to JSON.
 * - The serialized JSON matches the expected JSON.
 * - The serialized JSON can be deserialized back into the original object.
 *
 *  This is especially useful for round-trip serialization tests.
 *
 * @param T The type of the object to be verified.
 * @param value The object to be serialized and deserialized.
 * @param json The JSON serializer/deserializer used for the serialization process.
 * @param expectedJson A lambda returning the expected JSON string for comparison purposes.
 */
public inline fun <reified T> verifySerialization(value: T, json: Json, expectedJson: () -> String) {
    verifySerialization(value, json, expectedJson.invoke())
}

/**
 * Verifies the serialization and deserialization process for a given object, ensuring:
 * - The object can be serialized to JSON.
 * - The serialized JSON matches the expected JSON.
 * - The serialized JSON can be deserialized back into the original object.
 *
 * This is especially useful for round-trip serialization tests.
 *
 * @param T The type of the object being tested for serialization and deserialization.
 * @param value The object to be serialized and deserialized.
 * @param json The JSON serialization/deserialization processor.
 * @param expectedJson The expected JSON string to compare against the serialized output.
 */
public inline fun <reified T> verifySerialization(value: T, json: Json, @Language("json") expectedJson: String) {
    val jsonString = json.encodeToString(value)

    jsonString shouldEqualJson expectedJson

    val parsedObject: T = try {
        json.decodeFromString(jsonString)
    } catch (e: SerializationException) {
        fail("Failed to parse generated json string: ${e.message}:\n$value", e)
    }
    withClue("Parsed object is not equal to original value") {
        parsedObject shouldBe value
    }
}

/**
 * Verifies the deserialization of a JSON payload into an object of the specified type,
 * and ensures that the reverse serialization process returns the same JSON payload.
 *
 * This is especially useful for round-trip serialization tests.
 *
 * @param T The type of the object being deserialized from the JSON payload.
 * @param json The JSON processor used for deserialization and serialization.
 * @param payload The JSON string to deserialize into an object and verify against.
 * @return The deserialized object of type T.
 */
public inline fun <reified T> verifyDeserialization(json: Json, @Language("json") payload: String): T {
    val value: T = json.decodeFromString<T>(payload)
    verifySerialization(value = value, json = json, expectedJson = payload)
    return value
}

/**
 * Verifies the round-trip serialization and deserialization process for a given object.
 * This method ensures that:
 * - The object can be serialized into a JSON string.
 * - The serialized JSON string can be deserialized back into an equivalent object.
 *
 * This is particularly useful for testing the integrity of serialization logic.
 *
 * @param T The type of the object being tested for serialization and deserialization.
 * @param value The object to be serialized and deserialized.
 * @param json The JSON processor used for the serialization and deserialization process.
 */
public inline fun <reified T> verifySerializationRoundTrip(value: T, json: Json) {
    val jsonString: String = json.encodeToString(value)
    verifySerialization(value = value, json = json, expectedJson = jsonString)
}
