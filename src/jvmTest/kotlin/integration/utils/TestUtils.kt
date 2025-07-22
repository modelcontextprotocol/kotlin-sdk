package integration.utils

import io.modelcontextprotocol.kotlin.sdk.CallToolResultBase
import io.modelcontextprotocol.kotlin.sdk.PromptMessageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

object TestUtils {
    fun <T> runTest(block: suspend () -> T): T {
        return runBlocking {
            withContext(Dispatchers.IO) {
                block()
            }
        }
    }

    fun assertTextContent(content: PromptMessageContent?, expectedText: String) {
        assertNotNull(content, "Content should not be null")
        assertTrue(content is TextContent, "Content should be TextContent")
        assertNotNull(content.text, "Text content should not be null")
        assertEquals(expectedText, content.text, "Text content should match")
    }

    fun assertCallToolResult(result: Any?, message: String = ""): CallToolResultBase {
        assertNotNull(result, "${message}Call tool result should not be null")
        assertTrue(result is CallToolResultBase, "${message}Result should be CallToolResultBase")
        assertTrue(result.content.isNotEmpty(), "${message}Tool result content should not be empty")
        assertNotNull(result.structuredContent, "${message}Tool result structured content should not be null")

        return result
    }

    /**
     * Asserts that a JSON property has the expected string value.
     */
    fun assertJsonProperty(json: JsonObject, property: String, expectedValue: String, message: String = "") {
        assertEquals(expectedValue, json[property]?.toString()?.trim('"'), "${message}${property} should match")
    }

    /**
     * Asserts that a JSON property has the expected numeric value.
     */
    fun assertJsonProperty(json: JsonObject, property: String, expectedValue: Number, message: String = "") {
        when (expectedValue) {
            is Int -> assertEquals(
                expectedValue,
                (json[property] as? JsonPrimitive)?.content?.toIntOrNull(),
                "${message}${property} should match"
            )

            is Double -> assertEquals(
                expectedValue,
                (json[property] as? JsonPrimitive)?.content?.toDoubleOrNull(),
                "${message}${property} should match"
            )

            else -> assertEquals(
                expectedValue.toString(),
                json[property]?.toString()?.trim('"'),
                "${message}${property} should match"
            )
        }
    }

    /**
     * Asserts that a JSON property has the expected boolean value.
     */
    fun assertJsonProperty(json: JsonObject, property: String, expectedValue: Boolean, message: String = "") {
        assertEquals(expectedValue.toString(), json[property].toString(), "${message}${property} should match")
    }
}