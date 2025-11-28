package io.modelcontextprotocol.kotlin.sdk.shared

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.io.Buffer
import kotlinx.io.indexOf
import kotlinx.io.readString

/**
 * Buffers a continuous stdio stream into discrete JSON-RPC messages.
 *
 * This class accumulates bytes from a stream and extracts complete lines,
 * parsing them as JSON-RPC messages. Handles line-buffering with proper
 * CR/LF handling for cross-platform compatibility.
 *
 * Thread-safety: This class is NOT thread-safe. It should be used from
 * a single coroutine or protected by external synchronization.
 */
public class ReadBuffer {

    private val logger = KotlinLogging.logger { }

    private val buffer: Buffer = Buffer()

    /**
     * Returns true if there's no pending data in the buffer.
     */
    public fun isEmpty(): Boolean = buffer.exhausted()

    /**
     * Appends a chunk of bytes to the buffer.
     * Call this when new data arrives from the stream.
     */
    public fun append(chunk: ByteArray) {
        buffer.write(chunk)
    }

    /**
     * Reads a complete line from the buffer if available.
     * Returns null if no complete line is present.
     *
     * Handles both CRLF and LF line endings.
     */
    public fun readLine(): String? {
        if (buffer.exhausted()) return null
        var lfIndex = buffer.indexOf('\n'.code.toByte())
        return when (lfIndex) {
            -1L -> return null

            0L -> {
                buffer.skip(1)
                return null
            }

            else -> {
                var skipBytes = 1
                if (buffer[lfIndex - 1] == '\r'.code.toByte()) {
                    lfIndex -= 1
                    skipBytes += 1
                }
                val string = buffer.readString(lfIndex)
                buffer.skip(skipBytes.toLong())
                string
            }
        }
    }

    /**
     * Reads and parses the next JSON-RPC message from the buffer.
     * Returns null if no complete message is available.
     *
     * Attempts recovery if the line has a non-JSON prefix by looking for the first '{'.
     * If deserialization fails completely, logs the error and returns null.
     */
    public fun readMessage(): JSONRPCMessage? {
        val line = readLine() ?: return null
        try {
            return deserializeMessage(line)
        } catch (e: Exception) {
            logger.error(e) { "Failed to deserialize message from line: $line\nAttempting to recover..." }
            // if there is a non-JSON object prefix, try to parse from the first '{' onward.
            val braceIndex = line.indexOf('{')
            if (braceIndex != -1) {
                val trimmed = line.substring(braceIndex)
                try {
                    return deserializeMessage(trimmed)
                } catch (ignored: Exception) {
                    logger.error(ignored) { "Deserialization failed for line: $line\nSkipping..." }
                }
            }
        }

        return null
    }

    /**
     * Clears all pending data from the buffer.
     * Useful for discarding incomplete messages after errors.
     */
    public fun clear() {
        buffer.clear()
    }
}

internal fun deserializeMessage(line: String): JSONRPCMessage = McpJson.decodeFromString<JSONRPCMessage>(line)

public fun serializeMessage(message: JSONRPCMessage): String = McpJson.encodeToString(message) + "\n"
