package io.modelcontextprotocol.kotlin.sdk

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallToolResultUtilsTest {

    @Test
    fun testOkWithOnlyText() {
        val content = "TextMessage"
        val result = CallToolResult.Companion.ok(content)

        assertEquals(1, result.content.size)
        assertEquals(content, (result.content[0] as TextContent).text)
        assertFalse(result.isError == true)
        assertEquals(EmptyJsonObject, result._meta)
    }

    @Test
    fun testOkWithMeta() {
        val content = "TextMessageWithMeta"
        val meta = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive(42))
        }
        val result = CallToolResult.Companion.ok(content, meta)

        assertEquals(1, result.content.size)
        assertEquals(content, (result.content[0] as TextContent).text)
        assertFalse(result.isError == true)
        assertEquals(meta, result._meta)
    }

    @Test
    fun testErrorWithOnlyText() {
        val content = "ErrorMessage"
        val result = CallToolResult.Companion.error(content)

        assertEquals(1, result.content.size)
        assertEquals(content, (result.content[0] as TextContent).text)
        assertTrue(result.isError == true)
        assertEquals(EmptyJsonObject, result._meta)
    }

    @Test
    fun testErrorWithMeta() {
        val content = "ErrorMessageWithMeta"
        val meta = buildJsonObject {
            put("errorCode", JsonPrimitive(404))
            put("errorDetail", JsonPrimitive("资源未找到"))
        }
        val result = CallToolResult.Companion.error(content, meta)

        assertEquals(1, result.content.size)
        assertEquals(content, (result.content[0] as TextContent).text)
        assertTrue(result.isError == true)
        assertEquals(meta, result._meta)
    }
}

