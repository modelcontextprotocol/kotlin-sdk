package io.modelcontextprotocol.kotlin.sdk.server

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.readString

/** Default maximum size (in bytes) for an incoming HTTP request body: 4 MiB. */
internal const val DEFAULT_MAX_REQUEST_BODY_SIZE: Long = 4L * 1024 * 1024

/** Signals that an incoming request body exceeded the maximum size a transport is willing to read. */
internal class RequestBodyTooLargeException(val maxBodySize: Long) :
    IOException("Request body exceeds the maximum of $maxBodySize bytes.")

/**
 * Reads the request body as a UTF-8 string, rejecting bodies larger than [maxBytes].
 *
 * At most `maxBytes + 1` bytes are ever pulled from the request channel, so an oversized or unframed
 * body cannot exhaust memory regardless of the (untrusted) `Content-Length` header.
 *
 * @throws RequestBodyTooLargeException if the body exceeds [maxBytes]
 */
internal suspend fun ApplicationCall.receiveTextWithLimit(maxBytes: Long): String {
    val buffer = Buffer()
    val readLimit = if (maxBytes == Long.MAX_VALUE) maxBytes else maxBytes + 1
    val read = receiveChannel().readRemaining(readLimit).transferTo(buffer)
    if (read > maxBytes) {
        buffer.clear()
        throw RequestBodyTooLargeException(maxBytes)
    }
    return buffer.readString()
}
