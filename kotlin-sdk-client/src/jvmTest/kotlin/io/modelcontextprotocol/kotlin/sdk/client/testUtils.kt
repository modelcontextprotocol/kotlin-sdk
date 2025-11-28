package io.modelcontextprotocol.kotlin.sdk.client

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * RawSource that reads from a byte array.
 *
 * Useful for simulating stdin/stderr streams with predefined content.
 * Returns EOF (-1) when all data has been read.
 */
class ByteArraySource(private val data: ByteArray = ByteArray(512)) : RawSource {
    private var position = 0
    private var closed = false

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (closed) return -1
        if (position >= data.size) return -1

        val toRead = minOf(byteCount.toInt(), data.size - position)
        sink.write(data, position, toRead)
        position += toRead
        return toRead.toLong()
    }

    override fun close() {
        closed = true
    }
}

/**
 * RawSource that blocks until explicitly unblocked.
 *
 * This is useful for simulating a process that's waiting for data (e.g., stdin from a server
 * that hasn't responded yet).
 *
 * IMPORTANT: Always call [unblock] in cleanup to prevent resource leaks.
 */
class ControllableBlockingSource : RawSource {
    private val latch = CountDownLatch(1)
    private var closed = false

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        // Block until unblocked or closed
        while (!closed && latch.count > 0) {
            latch.await(100, TimeUnit.MILLISECONDS)
        }
        return -1
    }

    override fun close() {
        closed = true
        latch.countDown()
    }

    /**
     * Unblocks the source, allowing readAtMostTo to return EOF.
     * Should be called in test cleanup.
     */
    fun unblock() {
        latch.countDown()
    }
}

/**
 * RawSink that discards all data written to it (like /dev/null).
 *
 * Useful for test scenarios where we don't care about output data
 * but need a valid sink for the transport.
 */
class NoOpSink : RawSink {
    private var closed = false

    override fun write(source: Buffer, byteCount: Long) {
        if (closed) error("Sink is closed")
        // Discard the data
        source.skip(byteCount)
    }

    override fun flush() {
        // No-op
    }

    override fun close() {
        closed = true
    }
}
