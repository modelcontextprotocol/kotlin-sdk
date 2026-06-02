package io.modelcontextprotocol.kotlin.sdk.shared

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.indexOf
import kotlinx.io.readString

/**
 * Buffers a continuous stdio stream into discrete JSON-RPC messages.
 *
 * A single newline-terminated frame may not exceed an internal size cap (16 MiB): a peer that streams
 * bytes without ever sending a newline triggers a [TooLongFrameException] from [readMessage] instead
 * of growing the buffer without bound. A non-positive cap disables the check.
 */
public class ReadBuffer internal constructor(private val maxFrameSize: Int) {

    public constructor() : this(DEFAULT_MAX_FRAME_SIZE)

    internal companion object {
        internal const val DEFAULT_MAX_FRAME_SIZE: Int = 16 * 1024 * 1024
    }

    private val logger = KotlinLogging.logger { }

    private val buffer: Buffer = Buffer()

    /** Appends raw bytes to the internal buffer for subsequent message parsing. */
    public fun append(chunk: ByteArray) {
        buffer.write(chunk)
    }

    /**
     * Reads and deserializes a JSON-RPC message from the input buffer.
     *
     * The method attempts to read lines from the buffer until a valid `JSONRPCMessage` is successfully
     * deserialized. Blank lines are ignored, and if a deserialization error occurs, the method attempts
     * to recover and process the message.
     *
     * Recovery involves attempting to parse from the first detected JSON object in the line when an
     * error is encountered during deserialization.
     *
     * @return A deserialized `JSONRPCMessage` if successfully processed; otherwise, `null` if the
     *         input buffer is exhausted or no valid message is found.
     */
    public fun readMessage(): JSONRPCMessage? {
        while (true) {
            val line = readNextLine() ?: return null
            if (line.isBlank()) continue

            val message = try {
                deserializeMessage(line)
            } catch (e: Exception) {
                logger.error(e) { "Failed to deserialize message from line: $line\nAttempting to recover..." }
                tryRecover(line)
            }
            if (message != null) {
                return message
            }
        }
    }

    private fun readNextLine(): String? {
        val lfIndex = if (buffer.exhausted()) -1L else buffer.indexOf('\n'.code.toByte())
        if (lfIndex == -1L) {
            failIfFrameTooLong(buffer.size)
            return null
        }
        failIfFrameTooLong(lfIndex)

        return if (lfIndex == 0L) {
            buffer.skip(1)
            ""
        } else {
            var skipBytes = 1
            var messageLength = lfIndex
            if (buffer[lfIndex - 1] == '\r'.code.toByte()) {
                messageLength -= 1
                skipBytes += 1
            }
            val string = buffer.readString(messageLength)
            buffer.skip(skipBytes.toLong())
            string
        }
    }

    private fun failIfFrameTooLong(frameSize: Long) {
        if (maxFrameSize in 1..<frameSize) {
            buffer.clear()
            throw TooLongFrameException(frameSize, maxFrameSize)
        }
    }

    private fun tryRecover(line: String): JSONRPCMessage? {
        // if there is a non-JSON object prefix, try to parse from the first '{' onward.
        val braceIndex = line.indexOf('{')
        if (braceIndex == -1) return null

        val trimmed = line.substring(braceIndex)

        return try {
            deserializeMessage(trimmed)
        } catch (ignored: Exception) {
            logger.error(ignored) { "Deserialization failed for line: $line\nSkipping..." }
            null
        }
    }

    /** Clears all buffered data, discarding any partially received messages. */
    public fun clear() {
        buffer.clear()
    }
}

/**
 * Thrown by [ReadBuffer.readMessage] when a single newline-terminated frame exceeds the maximum size
 * before its terminator arrives — i.e. a peer is streaming data without ever framing it. Transports
 * treat it as fatal and close the connection.
 *
 * @property frameSize number of unframed bytes observed when the limit was tripped
 * @property maxFrameSize the maximum frame size that was exceeded
 */
public class TooLongFrameException(public val frameSize: Long, public val maxFrameSize: Int) :
    IOException(
        "JSON-RPC frame exceeded the maximum size of $maxFrameSize bytes " +
            "before a newline terminator (observed $frameSize bytes).",
    )

internal fun deserializeMessage(line: String): JSONRPCMessage = McpJson.decodeFromString<JSONRPCMessage>(line)

/** Serializes a [JSONRPCMessage] to its JSON string representation with a trailing newline. */
public fun serializeMessage(message: JSONRPCMessage): String = McpJson.encodeToString(message) + "\n"
