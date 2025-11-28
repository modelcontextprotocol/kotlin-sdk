package io.modelcontextprotocol.kotlin.sdk.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.internal.IODispatcher
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.ReadBuffer
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.shared.serializeMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.jvm.JvmOverloads

/**
 * A transport implementation for JSON-RPC communication that leverages standard input and output streams.
 *
 * This class reads from an input stream to process incoming JSON-RPC messages and writes JSON-RPC messages
 * to an output stream.
 *
 * Uses structured concurrency principles:
 * - Parent job controls all child coroutines
 * - Proper cancellation propagation
 * - Resource cleanup guaranteed via structured concurrency
 *
 * @param input The input stream where messages are received.
 * @param output The output stream where messages are sent.
 * @param error Optional error stream for stderr processing.
 * @param processStdError Callback for stderr lines. Returns true for fatal errors.
 */
@OptIn(ExperimentalAtomicApi::class)
public class StdioClientTransport @JvmOverloads public constructor(
    private val input: Source,
    private val output: Sink,
    private val error: Source? = null,
    private val processStdError: (String) -> Boolean = { true },
) : AbstractTransport() {
    private val logger = KotlinLogging.logger {}

    // Structured concurrency: single parent job manages all I/O operations
    private val parentJob: CompletableJob = SupervisorJob()
    private val scope = CoroutineScope(IODispatcher + parentJob)

    // State management through job lifecycle, not atomic flags
    private val initialized: AtomicBoolean = AtomicBoolean(false)
    private val sendChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun start() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
            error("StdioClientTransport already started!")
        }

        logger.debug { "Starting StdioClientTransport..." }

        // Launch all I/O operations in the scope - structured concurrency ensures cleanup
        scope.launch(CoroutineName("StdioClientTransport.IO#${hashCode()}")) {
            try {
                val outputStream = output.buffered()
                val errorStream = error?.buffered()

                // Use supervisorScope so individual stream failures don't cancel siblings
                supervisorScope {
                    // Launch stdin reader
                    val stdinJob = launch(CoroutineName("stdin-reader")) {
                        readStream(input, ::processReadBuffer)
                    }

                    // Launch stderr reader if present
                    val stderrJob = errorStream?.let {
                        launch(CoroutineName("stderr-reader")) {
                            readStream(it, ::processStderrBuffer)
                        }
                    }

                    // Launch writer
                    val writerJob = launch(CoroutineName("stdout-writer")) {
                        writeMessages(outputStream)
                    }

                    // Wait for both stdin and stderr to complete (reach EOF or get cancelled)
                    // When a process exits, both streams will be closed by the OS
                    logger.debug { "Waiting for stdin to complete..." }
                    stdinJob.join()
                    logger.debug { "stdin completed, waiting for stderr..." }
                    stderrJob?.join()
                    logger.debug { "stderr completed, cancelling writer..." }

                    // Cancel writer (it may be blocked waiting for channel messages)
                    writerJob.cancelAndJoin()
                    logger.debug { "writer cancelled, supervisorScope complete" }
                }
            } catch (e: CancellationException) {
                logger.debug { "Transport cancelled: ${e.message}" }
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Transport error" }
                _onError.invoke(e)
            } finally {
                // Cleanup: close all streams and notify
                runCatching { input.close() }
                runCatching { output.close() }
                runCatching { error?.close() }
                runCatching { sendChannel.close() }
                _onClose.invoke()
            }
        }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        if (!initialized.load()) {
            error("Transport not started")
        }

        sendChannel.send(message)
    }

    override suspend fun close() {
        if (!initialized.compareAndSet(expectedValue = true, newValue = false)) {
            error("Transport is already closed")
        }

        logger.debug { "Closing StdioClientTransport..." }

        // Cancel scope - structured concurrency handles cleanup via finally blocks
        parentJob.cancelAndJoin()
    }

    /**
     * Reads from a source stream and processes each chunk through the provided block.
     * Cancellation-aware and properly propagates CancellationException.
     */
    private suspend fun CoroutineScope.readStream(source: Source, block: suspend (ReadBuffer) -> Unit) {
        logger.debug { "Stream reader started" }

        source.use {
            val readBuffer = ReadBuffer()
            while (this.isActive) {
                val buffer = Buffer()
                val bytesRead = it.readAtMostTo(buffer, 8192)

                if (bytesRead == -1L) {
                    logger.debug { "EOF reached" }
                    break
                }

                if (bytesRead > 0L) {
                    readBuffer.append(buffer.readByteArray())
                    block(readBuffer)
                }
            }
        }
    }

    /**
     * Processes JSON-RPC messages from the read buffer.
     * Each message is delivered to the onMessage callback.
     */
    private suspend fun processReadBuffer(buffer: ReadBuffer) {
        while (true) {
            val msg = buffer.readMessage() ?: break

            @Suppress("TooGenericExceptionCaught")
            try {
                _onMessage.invoke(msg)
            } catch (e: Throwable) {
                _onError.invoke(e)
                logger.error(e) { "Error processing message" }
            }
        }
    }

    /**
     * Processes stderr lines from the read buffer.
     * If processStdError returns true (fatal), cancels the scope.
     */
    private suspend fun processStderrBuffer(buffer: ReadBuffer) {
        val errorLine = buffer.readLine()
        buffer.clear()

        if (errorLine != null) {
            val isFatal = processStdError(errorLine)

            if (isFatal) {
                logger.error { "Fatal stderr error: $errorLine" }

                val exception = McpException(
                    RPCError.ErrorCode.CONNECTION_CLOSED,
                    "Fatal error in stderr: $errorLine",
                )

                // Notify error handler
                _onError.invoke(exception)

                // Close streams to trigger EOF - this will cause natural shutdown
                // The stdin reader will complete, then we'll shut down gracefully
                runCatching { input.close() }
                runCatching { output.close() }

                // Exit the stderr reader loop
                return
            } else {
                logger.warn { "Non-fatal stderr warning: $errorLine" }
            }
        }
    }

    /**
     * Writes JSON-RPC messages from the send channel to the output stream.
     * Runs until the channel is closed or coroutine is cancelled.
     */
    private suspend fun writeMessages(outputStream: Sink) {
        logger.debug { "Writer started" }

        try {
            for (message in sendChannel) {
                if (!currentCoroutineContext().isActive) break

                val json = serializeMessage(message)
                outputStream.writeString(json)
                outputStream.flush()
            }
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                _onError.invoke(e)
                logger.error(e) { "Error writing to output stream" }
            }
            throw e
        }

        logger.debug { "Writer finished" }
    }
}
