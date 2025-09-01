package io.modelcontextprotocol.kotlin.sdk.integration.utils

import io.modelcontextprotocol.kotlin.sdk.CallToolResultBase
import io.modelcontextprotocol.kotlin.sdk.PromptMessageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.kotest.assertions.json.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

object TestUtils {
    fun <T> runTest(block: suspend () -> T): T = runBlocking {
        withContext(Dispatchers.IO) {
            block()
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

    // Use Kotest JSON assertions to compare whole JSON structures.
    fun assertJsonEquals(expectedJson: String, actual: JsonElement, message: String = "") {
        val prefix = if (message.isNotEmpty()) "$message\n" else ""
        (actual.toString()).shouldEqualJson(prefix + expectedJson)
    }

    fun assertJsonEquals(expected: JsonElement, actual: JsonElement) {
        (actual.toString()).shouldEqualJson(expected.toString())
    }

    fun assertIsJsonArray(actual: JsonElement) {
        actual.toString().shouldBeJsonArray()
    }
}
