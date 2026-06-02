package io.modelcontextprotocol.kotlin.sdk.shared

import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReadBufferTest {
    private val testMessage: JSONRPCMessage = JSONRPCNotification(method = "foobar")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `should have no messages after initialization`() {
        val readBuffer = ReadBuffer()
        assertNull(readBuffer.readMessage())
    }

    @Test
    fun `should only yield a message after a newline`() {
        val readBuffer = ReadBuffer()

        // Append message without a newline
        val messageBytes = json.encodeToString(testMessage).encodeToByteArray()
        readBuffer.append(messageBytes)
        assertNull(readBuffer.readMessage())
        readBuffer.append("\r".encodeToByteArray())
        assertNull(readBuffer.readMessage())

        // Append a newline and verify message is now available
        readBuffer.append("\n".encodeToByteArray())
        assertEquals(testMessage, readBuffer.readMessage())
        assertNull(readBuffer.readMessage())
    }

    @Test
    fun `skip empty line`() {
        val readBuffer = ReadBuffer()
        readBuffer.append("\n".toByteArray())
        assertNull(readBuffer.readMessage())
    }

    @Test
    fun `skip blank line`() {
        val readBuffer = ReadBuffer()
        readBuffer.append(" \n".toByteArray())
        assertNull(readBuffer.readMessage())
    }

    @Test
    fun `skip invalid json line`() {
        val readBuffer = ReadBuffer()
        readBuffer.append(" {ah=oh\n".toByteArray())
        assertNull(readBuffer.readMessage())
    }

    @Test
    fun `should be reusable after clearing`() {
        val readBuffer = ReadBuffer()

        readBuffer.append("foobar".toByteArray(Charsets.UTF_8))
        readBuffer.clear()
        assertNull(readBuffer.readMessage())

        val messageJson = serializeMessage(testMessage)
        readBuffer.append(messageJson.toByteArray(Charsets.UTF_8))
        readBuffer.append("\n".toByteArray(Charsets.UTF_8))
        val message = readBuffer.readMessage()
        assertEquals(testMessage, message)
    }

    @Test
    fun `should fail when an unframed blob exceeds the cap`() {
        val readBuffer = ReadBuffer(maxFrameSize = 64)
        // No newline ever arrives: the memory-exhaustion vector.
        readBuffer.append(ByteArray(100) { 'a'.code.toByte() })
        val ex = assertFailsWith<TooLongFrameException> { readBuffer.readMessage() }
        assertContains(ex.message.orEmpty(), "maximum size")
    }

    @Test
    fun `should fail when a completed line exceeds the cap`() {
        val readBuffer = ReadBuffer(maxFrameSize = 64)
        readBuffer.append(ByteArray(100) { 'a'.code.toByte() } + '\n'.code.toByte())
        assertFailsWith<TooLongFrameException> { readBuffer.readMessage() }
    }

    @Test
    fun `should not enforce a cap when maxFrameSize is non-positive`() {
        val readBuffer = ReadBuffer(maxFrameSize = 0)
        // Well beyond any small cap and still no newline — must not throw when disabled.
        readBuffer.append(ByteArray(8192) { 'a'.code.toByte() })
        assertNull(readBuffer.readMessage())
    }

    @Test
    fun `should parse a message that fits under the cap`() {
        val readBuffer = ReadBuffer(maxFrameSize = 1024)
        readBuffer.append(serializeMessage(testMessage).encodeToByteArray())
        assertEquals(testMessage, readBuffer.readMessage())
    }
}
