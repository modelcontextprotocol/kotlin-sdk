package io.modelcontextprotocol.kotlin.sdk.shared

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import kotlinx.io.Buffer
import kotlinx.io.indexOf
import kotlinx.io.readString

/**
 * Buffers a continuous stdio stream into discrete JSON-RPC messages.
 */
public class ReadBuffer {

    private val logger = KotlinLogging.logger { }

    private val buffer: Buffer = Buffer()

    public fun append(chunk: ByteArray) {
        buffer.write(chunk)
    }

    public fun readMessage(): JSONRPCMessage? {
        if (buffer.exhausted()) return null
        var lfIndex = buffer.indexOf('\n'.code.toByte())
        val line = when (lfIndex) {
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

    public fun clear() {
        buffer.clear()
    }
}

internal fun deserializeMessage(line: String): JSONRPCMessage = McpJson.decodeFromString<JSONRPCMessage>(line)

public fun serializeMessage(message: JSONRPCMessage): String = McpJson.encodeToString(message) + "\n"
