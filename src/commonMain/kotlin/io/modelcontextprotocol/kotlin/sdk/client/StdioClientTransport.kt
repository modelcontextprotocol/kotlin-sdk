package io.modelcontextprotocol.kotlin.sdk.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.ReadBuffer
import io.modelcontextprotocol.kotlin.sdk.shared.serializeMessage
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlin.coroutines.CoroutineContext

/**
 * A transport implementation for JSON-RPC communication that leverages standard input and output streams.
 *
 * This class reads from an input stream to process incoming JSON-RPC messages and writes JSON-RPC messages
 * to an output stream.
 *
 * @param input The input stream where messages are received.
 * @param output The output stream where messages are sent.
 */
public class StdioClientTransport(
    private val input: Source,
    private val output: Sink
) : AbstractTransport() {
    private val logger = KotlinLogging.logger {}
    private val ioCoroutineContext: CoroutineContext = Dispatchers.IO
    private val scope by lazy {
        CoroutineScope(ioCoroutineContext + SupervisorJob())
    }
    private var job: Job? = null
    private val initialized: AtomicBoolean = atomic(false)
    private val sendChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
    private val readBuffer = ReadBuffer()

    override suspend fun start() {
        if (!initialized.compareAndSet(false, true)) {
            error("StdioClientTransport already started!")
        }

        logger.debug { "Starting StdioClientTransport..." }

        val outputStream = output.buffered()

        job = scope.launch(CoroutineName("StdioClientTransport.IO#${hashCode()}")) {
            val readJob = launch {
                logger.debug { "Read coroutine started." }
                try {
                    input.use {
                        while (isActive) {
                            val buffer = Buffer()
                            val bytesRead = input.readAtMostTo(buffer, 8192)
                            if (bytesRead == -1L) break
                            if (bytesRead > 0L) {
                                readBuffer.append(buffer.readByteArray())
                                processReadBuffer()
                            }
                        }
                    }
                } catch (e: Exception) {
                    _onError.invoke(e)
                    logger.error(e) { "Error reading from input stream" }
                }
            }

            val writeJob = launch {
                logger.debug { "Write coroutine started." }
                try {
                    sendChannel.consumeEach { message ->
                        val json = serializeMessage(message)
                        outputStream.writeString(json)
                        outputStream.flush()
                    }
                } catch (e: Throwable) {
                    if (isActive) {
                        _onError.invoke(e)
                        logger.error(e) { "Error writing to output stream" }
                    }
                } finally {
                    output.close()
                }
            }

            readJob.join()
            writeJob.cancelAndJoin()
            _onClose.invoke()
        }
    }

    override suspend fun send(message: JSONRPCMessage) {
        if (!initialized.value) {
            error("Transport not started")
        }

        sendChannel.send(message)
    }

    override suspend fun close() {
        if (!initialized.compareAndSet(true, false)) {
            error("Transport is already closed")
        }
        job?.cancelAndJoin()
        input.close()
        output.close()
        readBuffer.clear()
        sendChannel.close()
        _onClose.invoke()
    }

    private suspend fun processReadBuffer() {
        while (true) {
            val msg = readBuffer.readMessage() ?: break
            try {
                _onMessage.invoke(msg)
            } catch (e: Throwable) {
                _onError.invoke(e)
                logger.error(e) { "Error processing message." }
            }
        }
    }
}
