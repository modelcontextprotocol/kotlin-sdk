package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import kotlin.test.Test

class ReadBufferTest {
    private val message: JSONRPCMessage = JSONRPCNotification(method = "foobar")
    private val line = serializeMessage(message)
    private val json = line.removeSuffix("\n")

    private fun ReadBuffer.append(text: String) = append(text.encodeToByteArray())

    @Test
    fun `should only yield a message after a newline`() {
        val readBuffer = ReadBuffer()

        readBuffer.append(json)
        readBuffer.readMessage().shouldBeNull()
        readBuffer.append("\r")
        readBuffer.readMessage().shouldBeNull()

        readBuffer.append("\n")
        readBuffer.readMessage() shouldBe message
        readBuffer.readMessage().shouldBeNull()
    }

    @Test
    fun `should yield a short message that follows a longer one split across chunks`() {
        val readBuffer = ReadBuffer()
        val longMessage: JSONRPCMessage = JSONRPCNotification(method = "x".repeat(line.length * 2))

        readBuffer.append(serializeMessage(longMessage).removeSuffix("\n"))
        readBuffer.readMessage().shouldBeNull()
        // The rest of the long frame and a whole frame shorter than what was already searched.
        readBuffer.append("\n$line")

        readBuffer.readMessage() shouldBe longMessage
        readBuffer.readMessage() shouldBe message
        readBuffer.readMessage().shouldBeNull()
    }

    @Test
    fun `should skip empty blank and malformed lines and return the next message`() {
        val readBuffer = ReadBuffer()

        readBuffer.append("\n \nnot json\n {ah=oh\n$line")

        readBuffer.readMessage() shouldBe message
        readBuffer.readMessage().shouldBeNull()
    }

    @Test
    fun `should recover a message preceded by non-JSON text on the same line`() {
        val readBuffer = ReadBuffer()

        readBuffer.append("garbage$line")

        readBuffer.readMessage() shouldBe message
    }

    @Test
    fun `should discard buffered partial data on clear`() {
        val readBuffer = ReadBuffer()
        // starts with '{', so if it survived clear() the next line could not be recovered
        readBuffer.append("""{"jsonrpc":""")

        readBuffer.clear()
        readBuffer.append(line)

        readBuffer.readMessage() shouldBe message
    }

    @Test
    fun `should yield a message shorter than the searched partial data discarded by clear`() {
        val readBuffer = ReadBuffer()
        readBuffer.append("a".repeat(line.length * 2))
        readBuffer.readMessage().shouldBeNull()

        readBuffer.clear()
        readBuffer.append(line)

        readBuffer.readMessage() shouldBe message
    }

    @Test
    fun `should fail when an unframed blob exceeds the cap`() {
        val readBuffer = ReadBuffer(maxFrameSize = 64)
        // No newline ever arrives: the memory-exhaustion vector.
        readBuffer.append("a".repeat(100))

        shouldThrow<TooLongFrameException> { readBuffer.readMessage() }.message shouldContain "maximum size"
    }

    @Test
    fun `should yield the next message after rejecting an unframed blob`() {
        val readBuffer = ReadBuffer(maxFrameSize = 64)
        readBuffer.append("a".repeat(100))
        shouldThrow<TooLongFrameException> { readBuffer.readMessage() }

        readBuffer.append(line)

        readBuffer.readMessage() shouldBe message
    }

    @Test
    fun `should accept a line of exactly maxFrameSize bytes and reject a longer one`() {
        val frameSize = json.encodeToByteArray().size

        val atCap = ReadBuffer(maxFrameSize = frameSize).apply { append(line) }
        atCap.readMessage() shouldBe message

        val belowCap = ReadBuffer(maxFrameSize = frameSize - 1).apply { append(line) }
        shouldThrow<TooLongFrameException> { belowCap.readMessage() }
    }

    @Test
    fun `should not enforce a cap when maxFrameSize is non-positive`() {
        val readBuffer = ReadBuffer(maxFrameSize = 0)
        // Well beyond any small cap and still no newline — must not throw when disabled.
        readBuffer.append("a".repeat(8192))

        readBuffer.readMessage().shouldBeNull()
    }
}
