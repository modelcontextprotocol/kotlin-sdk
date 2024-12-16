package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.ReadBuffer
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.serializeMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.BufferedInputStream
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * A server transport that communicates with a client via standard I/O.
 *
 * Reads from System.in and writes to System.out.
 */
public class StdioServerTransport(
    private val inputStream: BufferedInputStream = BufferedInputStream(System.`in`),
    outputStream: PrintStream = System.out
) : Transport {
    private val logger = KotlinLogging.logger {}
    override var onClose: (() -> Unit)? = null
    override var onError: ((Throwable) -> Unit)? = null
    override var onMessage: (suspend (JSONRPCMessage) -> Unit)? = null

    private val readBuffer = ReadBuffer()
    private var initialized = AtomicBoolean(false)
    private var readingJob: Job? = null

    private val coroutineContext: CoroutineContext = Dispatchers.IO + SupervisorJob()
    private val scope = CoroutineScope(coroutineContext)
    private val readChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val outputWriter = outputStream.bufferedWriter()

    override suspend fun start() {
        if (!initialized.compareAndSet(false, true)) {
            error("StdioServerTransport already started!")
        }

        // Launch a coroutine to read from stdin
        readingJob = scope.launch {
            val buf = ByteArray(8192)
            try {
                while (isActive) {
                    val bytesRead = inputStream.read(buf)
                    if (bytesRead == -1) {
                        // EOF reached
                        break
                    }
                    if (bytesRead > 0) {
                        val chunk = buf.copyOf(bytesRead)
                        readChannel.send(chunk)
                    }
                }
            } catch (e: Throwable) {
                logger.error(e) { "Error reading from stdin" }
                onError?.invoke(e)
            } finally {
                // Reached EOF or error, close connection
                close()
            }
        }

        // Launch a coroutine to process messages from readChannel
        scope.launch {
            try {
                for (chunk in readChannel) {
                    readBuffer.append(chunk)
                    processReadBuffer()
                }
            } catch (e: Throwable) {
                onError?.invoke(e)
            }
        }
    }

    private suspend fun processReadBuffer() {
        while (true) {
            val message = try {
                readBuffer.readMessage()
            } catch (e: Throwable) {
                onError?.invoke(e)
                null
            }

            if (message == null) break
            // Async invocation broke delivery order
            try {
                onMessage?.invoke(message)
            } catch (e: Throwable) {
                onError?.invoke(e)
            }
        }
    }

    override suspend fun close() {
        if (!initialized.compareAndSet(true, false)) return

        // Cancel reading job and close channel
        readingJob?.cancel() // ToDO("was cancel and join")
        readChannel.close()
        readBuffer.clear()

        onClose?.invoke()
    }

    override suspend fun send(message: JSONRPCMessage) {
        val json = serializeMessage(message)
        synchronized(outputWriter) {
            // You may need to add Content-Length headers before the message if using the LSP framing protocol
            outputWriter.write(json)
            outputWriter.flush()
        }
    }
}
